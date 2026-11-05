// Cookie transport is opt-in. Authentication handles are never visible to this module.
export const cookieMode = import.meta.env?.VITE_AUTH_TRANSPORT === 'cookie-spike';
const LOCK = 'fitcore:cookie-session';
const MARKER = 'fitcore:cookie-transition';
const REVISION = 'fitcore:cookie-revision';
const uncertainMessage = 'Cookie delivery is uncertain. This browser session is blocked; contact the local test operator.';
const failure = (code, message) => Object.assign(new Error(message), { code });
const unsupported = () => failure('COOKIE_UNSUPPORTED', 'This browser needs Web Locks and persistent storage for cookie sessions.');
const uncertain = () => failure('COOKIE_UNCERTAIN', uncertainMessage);

export function createCookieSession(env) {
  const listeners = new Set();
  let channel, channelUnavailable = false, identity;
  const identityKey = user => JSON.stringify([user.id, user.gymId, user.role, user.authzVersion, user.enabled, user.emailVerified]);
  const revision = () => env.storage.getItem(REVISION) || '0';
  const notify = (state, external = false) => {
    if (state === 'ENDED') identity = undefined;
    const event = { state, revision: revision(), external };
    for (const callback of listeners) callback(event);
    if (!external) channel?.postMessage({ state, revision: event.revision });
  };
  const capable = () => {
    try {
      if (channelUnavailable || !env.locks?.request || !env.storage) throw unsupported();
      // Never probe by clearing the transition marker.
      const value = revision(); env.storage.setItem(REVISION, value);
      if (revision() !== value) throw unsupported();
    } catch { throw unsupported(); }
  };
  const blocked = () => { if (env.storage.getItem(MARKER)) throw uncertain(); };
  const mark = kind => {
    env.storage.setItem(REVISION, String(Number(revision()) + 1));
    env.storage.setItem(MARKER, JSON.stringify({ kind, revision: revision() }));
    if (!env.storage.getItem(MARKER)) throw unsupported();
    notify('TRANSITION');
  };
  const lock = async action => {
    capable();
    return env.locks.request(LOCK, { mode: 'exclusive' }, async () => { capable(); blocked(); return action(); });
  };
  const send = async (url, {method = 'GET', data, csrf, bootstrap = false, responseType} = {}) => {
    const controller = new AbortController();
    let timer;
    const headers = { 'Content-Type': 'application/json' };
    if (csrf) headers['X-CSRF-TOKEN'] = csrf;
    if (bootstrap) headers['X-FitCore-Bootstrap'] = '1';
    // Fetch produces Origin/Fetch Metadata; caller headers are deliberately not forwarded.
    try {
      const response = await Promise.race([
        (async () => {
          const r = await env.fetch(url, { method, headers, credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal: controller.signal, ...(data === undefined ? {} : {body:typeof data === 'string' ? data : JSON.stringify(data)}) });
          const text = await r.text();
          let body = text;
          if (text && responseType !== 'text') { try { body = JSON.parse(text); } catch { /* CSV/text responses stay text. */ } }
          if (!r.ok) throw Object.assign(new Error(body?.message || `HTTP ${r.status}`), {response:{status:r.status,data:body}});
          return {data:body,status:r.status,headers:r.headers};
        })(),
        new Promise((_, reject) => { timer = setTimeout(() => { controller.abort(); reject(failure('ETIMEDOUT', 'Request timed out.')); }, env.timeoutMs ?? 20000); }),
      ]);
      return response;
    } finally { clearTimeout(timer); }
  };
  const writer = async (kind, action) => {
    mark(kind);
    try {
      const result = await action();
      env.storage.removeItem(MARKER);
      notify('IDLE');
      return result;
    } catch {
      // No timeout or late response clears the marker. Only this successful exchange can.
      notify('UNCERTAIN'); throw uncertain();
    }
  };
  const context = async (allowBootstrap = true) => {
    let response;
    try { response = await send('/api/browser/csrf'); }
    catch (error) {
      if (error.response?.status !== 401 || !allowBootstrap) throw error;
      response = await writer('BOOTSTRAP', async () => {
        const result = await send('/api/browser/bootstrap', {method:'POST',data:{},bootstrap:true});
        if (result.data?.transitionState !== 'IDLE' || typeof result.data?.csrfToken !== 'string') throw uncertain();
        return result;
      });
    }
    if (response.data?.transitionState !== 'IDLE') {
      mark('SERVER_PENDING'); notify('UNCERTAIN'); throw uncertain();
    }
    if (typeof response.data.csrfToken !== 'string' || typeof response.data.authenticated !== 'boolean') throw uncertain();
    return response.data;
  };
  const freshMe = async () => {
    const version = revision();
    const result = await send('/api/auth/me');
    if (revision() !== version || env.storage.getItem(MARKER)) throw failure('ERR_CANCELED', 'Session changed.');
    if (!result.data?.id || !result.data?.gymId || !result.data?.role) throw failure('COOKIE_IDENTITY_REQUIRED', 'Unable to confirm the current session.');
    return result.data;
  };
  const transition = (kind, data) => lock(async () => {
    const current = await context();
    await writer(kind, async () => {
      const result = await send(`/api/browser/${kind.toLowerCase()}`, {method:'POST',data:data ?? {},csrf:current.csrfToken});
      if (result.data?.transitionState !== 'PENDING' || typeof result.data?.csrfToken !== 'string') throw uncertain();
      const ack = await send('/api/browser/ack', {method:'POST',data:{},csrf:result.data.csrfToken});
      if (ack.data?.acknowledged !== true) throw uncertain();
    });
    if (kind !== 'LOGIN') { identity = undefined; return; }
    const user = await freshMe(); identity = identityKey(user); return user;
  });
  return {
    browserLogin: (email,password) => transition('LOGIN',{email,password}),
    browserLogout: () => transition('LOGOUT'),
    browserMe: () => lock(async () => {
      const current = await context();
      if (!current.authenticated) { identity = undefined; return null; }
      const user = await freshMe(); identity = identityKey(user); return user;
    }),
    cookieApiRequest: async config => {
      const path = config.url;
      if (typeof path !== 'string' || !/^\/(?:[a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_-]+$/.test(path.split('?')[0]) || path.includes('\\') || /[\r\n#]/.test(path) || path.startsWith('/browser/') || (path.startsWith('/auth/') && path !== '/auth/me')) {
        throw failure('COOKIE_ROUTE_DISABLED', 'Registration, verification and recovery are unavailable in this cookie test environment.');
      }
      // Bind old-view work before joining the lock queue. A later login/restore
      // may complete while this call waits; it cannot adopt that new identity.
      capable(); blocked();
      const origin = { revision: revision(), identity };
      const assertOrigin = () => {
        blocked();
        if (origin.identity === undefined || origin.revision !== revision() || origin.identity !== identity) throw failure('ERR_CANCELED', 'Session changed.');
      };
      assertOrigin();
      return lock(async () => {
        const query = new URLSearchParams(config.params || {}).toString();
        const url = `/api${path}${query ? (path.includes('?') ? '&' : '?') + query : ''}`;
        try {
          assertOrigin();
          const current = await context(false);
          if (!current.authenticated) throw Object.assign(new Error('Sign in to continue.'),{response:{status:401,data:{message:'Unauthorized'}}});
          // Raw cookie changes and DB revocation need no BroadcastChannel event.
          // Confirm identity both before sending tenant work and before accepting it.
          const before = identityKey(await freshMe());
          assertOrigin();
          if (origin.identity !== before) { notify('ENDED'); throw failure('ERR_CANCELED', 'Session changed.'); }
          const result = await send(url, {method:(config.method || 'GET').toUpperCase(),data:config.data,csrf:current.csrfToken,responseType:config.responseType});
          assertOrigin();
          const after = identityKey(await freshMe());
          assertOrigin();
          if (after !== origin.identity) { notify('ENDED'); throw failure('ERR_CANCELED', 'Session changed.'); }
          return {...result, config};
        } catch (error) {
          if (error.response?.status === 423) { mark('SERVER_PENDING'); notify('UNCERTAIN'); throw uncertain(); }
          if (error.response?.status === 401 || (error.response?.status === 403 && error.response?.data?.message === 'GYM_UNAVAILABLE')) notify('ENDED');
          throw error;
        }
      });
    },
    subscribeSessionChange: callback => {
      if (!channel && env.channel) {
        try {
          channel = env.channel();
          channel.onmessage = event => { if (['TRANSITION','IDLE','UNCERTAIN','ENDED'].includes(event.data?.state)) notify(event.data.state, true); };
        } catch { channelUnavailable = true; }
      }
      listeners.add(callback);
      const unsubscribeStorage = env.onStorage?.(event => { if ([MARKER,REVISION].includes(event.key) || event.key === null) notify(env.storage.getItem(MARKER) ? 'TRANSITION' : 'IDLE',true); });
      return () => { listeners.delete(callback); unsubscribeStorage?.(); if (!listeners.size) { channel?.close(); channel = undefined; } };
    },
  };
}
// Foreground events can arrive together. One authoritative restore owns that burst;
// local writers already perform fresh identity/clearance and must keep their generation.
export function subscribeCookieLifecycle({windowTarget, documentTarget, canRestore, restore}) {
  let pending = false, stopped = false;
  const returned = () => {
    if (stopped || pending || !canRestore()) return;
    pending = true;
    const settled = () => { pending = false; };
    // The provider invalidates identity and protected caches synchronously before reading.
    Promise.resolve(restore()).then(settled, settled);
  };
  const visible = () => { if (documentTarget.visibilityState === 'visible') returned(); };
  const restored = event => { if (event.persisted) returned(); };
  windowTarget.addEventListener('focus', returned);
  documentTarget.addEventListener('visibilitychange', visible);
  windowTarget.addEventListener('pageshow', restored);
  return () => {
    stopped = true;
    windowTarget.removeEventListener('focus', returned);
    documentTarget.removeEventListener('visibilitychange', visible);
    windowTarget.removeEventListener('pageshow', restored);
  };
}
let singleton;
const session = () => {
  if (!singleton) {
    let storage; try { storage = globalThis.localStorage; } catch { /* Fails closed at first operation. */ }
    singleton = createCookieSession({storage,locks:globalThis.navigator?.locks,fetch:(...args)=>globalThis.fetch(...args),channel:()=>new BroadcastChannel(LOCK),onStorage:callback=>{window.addEventListener('storage',callback);return()=>window.removeEventListener('storage',callback);}});
  }
  return singleton;
};
export const cookieApiRequest = config => session().cookieApiRequest(config);
export const browserLogin = (email,password) => session().browserLogin(email,password);
export const browserLogout = () => session().browserLogout();
export const browserMe = () => session().browserMe();
export const subscribeSessionChange = callback => session().subscribeSessionChange(callback);
