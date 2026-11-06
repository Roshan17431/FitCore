// Cookie transport is opt-in. Authentication handles are never visible to this module.
export const cookieMode = import.meta.env?.VITE_AUTH_TRANSPORT === 'cookie-spike';
const LOCK = 'fitcore:cookie-session';
const MARKER = 'fitcore:cookie-transition';
const REVISION = 'fitcore:cookie-revision';
const STORAGE_PROBE = 'fitcore:cookie-storage-probe';
const uncertainMessage = 'Cookie delivery is uncertain. Check sign-in status before continuing.';
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
      // Pre-lock checks are read-only: another tab may advance protocol state
      // between any two storage calls, even these synchronous calls.
      revision(); env.storage.getItem(MARKER);
    } catch { throw unsupported(); }
  };
  const writable = () => {
    capable();
    try {
      // Only the granted lock probes writes, using a separate non-secret key.
      env.storage.setItem(STORAGE_PROBE, '1');
      if (env.storage.getItem(STORAGE_PROBE) !== '1') throw unsupported();
      env.storage.removeItem(STORAGE_PROBE);
      if (env.storage.getItem(STORAGE_PROBE) !== null) throw unsupported();
    } catch { throw unsupported(); }
  };
  const blocked = () => { if (env.storage.getItem(MARKER)) throw uncertain(); };
  const canonicalRevision = value => {
    if (typeof value === 'number') {
      if (!Number.isSafeInteger(value) || value < 0) throw uncertain();
      value = String(value);
    }
    if (typeof value !== 'string' || !/^(0|[1-9][0-9]{0,18})$/.test(value) || BigInt(value) > 9223372036854775807n) throw uncertain();
    return value;
  };
  const validContext = data => {
    if (!data || typeof data.csrfToken !== 'string' || !data.csrfToken.length || data.csrfToken.length > 4096 ||
        typeof data.authenticated !== 'boolean' || typeof data.reauthenticationRequired !== 'boolean' ||
        typeof data.currentContext !== 'boolean' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.familyId || '') ||
        !['PREAUTH','AUTH'].includes(data.contextKind) || !['IDLE','PENDING'].includes(data.transitionState) ||
        (data.transitionState === 'IDLE' ? data.pendingKind !== null : !['LOGIN','LOGOUT'].includes(data.pendingKind)) ||
        (data.authenticated && (data.contextKind !== 'AUTH' || data.reauthenticationRequired || data.transitionState !== 'IDLE'))) throw uncertain();
    return {...data, contextRevision:canonicalRevision(data.contextRevision), familyRevision:canonicalRevision(data.familyRevision)};
  };
  const assertIdle = current => {
    if (current.transitionState !== 'IDLE' || !current.currentContext || current.contextRevision !== current.familyRevision) throw uncertain();
    return current;
  };
  const binding = current => JSON.stringify([current.familyId,current.contextRevision,current.familyRevision,current.contextKind,current.currentContext,current.transitionState,current.pendingKind,current.authenticated,current.reauthenticationRequired,current.csrfToken]);
  const mark = (kind, current) => {
    const local = String(BigInt(canonicalRevision(revision())) + 1n);
    canonicalRevision(local);
    env.storage.setItem(REVISION, local);
    const owned = JSON.stringify({kind,revision:local,...(current ? {preFamilyId:current.familyId,preServerRevision:current.contextRevision} : {})});
    env.storage.setItem(MARKER, owned);
    if (env.storage.getItem(MARKER) !== owned) throw unsupported();
    notify('TRANSITION');
    return owned;
  };
  const owns = owned => {
    if (env.storage.getItem(MARKER) !== owned || revision() !== JSON.parse(owned).revision) throw uncertain();
  };
  const clearOwned = owned => {
    owns(owned); env.storage.removeItem(MARKER);
    if (env.storage.getItem(MARKER)) throw uncertain();
    notify('IDLE');
  };
  const lock = async (action, recovery = false) => {
    capable();
    const controller = new AbortController();
    let timer, cancelled = false, acquired = false;
    try {
      return await Promise.race([
        env.locks.request(LOCK, {mode:'exclusive',signal:controller.signal}, async () => {
          acquired = true; clearTimeout(timer);
          if (cancelled) throw failure('COOKIE_LOCK_TIMEOUT','Sign-in status is busy. Please try again.');
          writable(); if (!recovery) blocked();
          return action();
        }),
        new Promise((_,reject) => { if (acquired) return; timer = setTimeout(() => {
          cancelled = true; controller.abort();
          reject(failure('COOKIE_LOCK_TIMEOUT','Sign-in status is busy. Please try again.'));
        },env.lockTimeoutMs ?? 20000); }),
      ]);
    } catch (error) {
      if (cancelled) throw failure('COOKIE_LOCK_TIMEOUT','Sign-in status is busy. Please try again.');
      throw error;
    } finally { clearTimeout(timer); }
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
          if (!r.ok) throw Object.assign(new Error(body?.message || `HTTP ${r.status}`), {response:{status:r.status,data:body,headers:r.headers}});
          return {data:body,status:r.status,headers:r.headers};
        })(),
        new Promise((_, reject) => { timer = setTimeout(() => { controller.abort(); reject(failure('ETIMEDOUT', 'Request timed out.')); }, env.timeoutMs ?? 20000); }),
      ]);
      return response;
    } finally { clearTimeout(timer); }
  };
  const readContext = async () => validContext((await send('/api/browser/csrf')).data);
  const writer = async (kind, current, action) => {
    const owned = mark(kind, current);
    try {
      const result = await action(owned);
      clearOwned(owned);
      return result;
    } catch (error) {
      if (error.cookieDefinitiveDenial) throw error;
      identity = undefined; notify('UNCERTAIN'); throw uncertain();
    }
  };
  const context = async (allowBootstrap = true) => {
    let current;
    try { current = await readContext(); }
    catch (error) {
      if (error.response?.status !== 401 || !allowBootstrap) throw error;
      current = await writer('BOOTSTRAP', null, async () => assertIdle(validContext((await send('/api/browser/bootstrap', {method:'POST',data:{},bootstrap:true})).data)));
    }
    if (current.transitionState !== 'IDLE') {
      mark('SERVER_PENDING'); notify('UNCERTAIN'); throw uncertain();
    }
    return assertIdle(current);
  };
  const freshMe = async (owned) => {
    const version = revision();
    if (owned) owns(owned);
    const result = await send('/api/auth/me');
    if (owned) owns(owned);
    else if (env.storage.getItem(MARKER)) throw failure('ERR_CANCELED', 'Session changed.');
    if (revision() !== version) throw failure('ERR_CANCELED', 'Session changed.');
    if (!result.data?.id || !result.data?.gymId || !result.data?.role) throw failure('COOKIE_IDENTITY_REQUIRED', 'Unable to confirm the current session.');
    return result.data;
  };
  // Metadata never grants authority: Cookie + CSRF + backend account validation do.
  const confirm = async (current, owned) => {
    assertIdle(current);
    if (current.reauthenticationRequired) {
      identity = undefined;
      throw failure('COOKIE_REAUTH_REQUIRED','Sign out the previous session before signing in again.');
    }
    if (owned) owns(owned);
    const user = current.authenticated ? await freshMe(owned) : null;
    const after = assertIdle(await readContext());
    if (binding(after) !== binding(current)) throw uncertain();
    if (owned) owns(owned); else blocked();
    identity = user ? identityKey(user) : undefined;
    return user;
  };
  const expectedNext = (current, pre, kind) => {
    const expectedKind = kind === 'LOGIN' ? 'AUTH' : 'PREAUTH';
    if (current.familyId !== pre.familyId || current.contextRevision !== String(BigInt(pre.contextRevision)+1n) ||
        current.familyRevision !== current.contextRevision || !current.currentContext || current.contextKind !== expectedKind ||
        current.reauthenticationRequired || current.authenticated !== (kind === 'LOGIN' && current.transitionState === 'IDLE') ||
        (current.transitionState === 'PENDING' && current.pendingKind !== kind)) throw uncertain();
    return current;
  };
  const settle = async (current, pre, kind, owned) => {
    expectedNext(current,pre,kind); owns(owned);
    if (current.transitionState === 'PENDING') {
      const ack = await send('/api/browser/ack',{method:'POST',data:{},csrf:current.csrfToken});
      owns(owned);
      if (ack.data?.acknowledged !== true) throw uncertain();
      const after = assertIdle(expectedNext(await readContext(),pre,kind));
      if (binding({...current,transitionState:'IDLE',pendingKind:null,authenticated:kind === 'LOGIN'}) !== binding(after)) throw uncertain();
      current = after;
    }
    return confirm(current,owned);
  };
  const definitiveDenial = error => !error.response?.headers?.get?.('set-cookie') && (
    (error.response?.status === 401 && error.response.data?.message === 'INVALID_CREDENTIALS') ||
    (error.response?.status === 403 && error.response.data?.message === 'GYM_UNAVAILABLE') ||
    (error.response?.status === 429 && error.response.data?.message === 'RATE_LIMITED'));
  const transition = (kind, data) => lock(async () => {
    const current = await context();
    if (kind === 'LOGIN' && current.reauthenticationRequired) {
      identity = undefined;
      throw failure('COOKIE_REAUTH_REQUIRED','Sign out the previous session before signing in again.');
    }
    return writer(kind, current, async owned => {
      let result;
      try { result = await send(`/api/browser/${kind.toLowerCase()}`,{method:'POST',data:data ?? {},csrf:current.csrfToken}); }
      catch (error) {
        if (kind !== 'LOGIN' || !definitiveDenial(error)) throw error;
        owns(owned);
        const after = assertIdle(await readContext());
        if (binding(after) !== binding(current)) throw uncertain();
        const user = await confirm(after,owned);
        clearOwned(owned);
        throw Object.assign(error,{cookieDefinitiveDenial:true,cookieSessionUser:user});
      }
      const pending = validContext(result.data);
      if (pending.transitionState !== 'PENDING') throw uncertain();
      return settle(pending,current,kind,owned);
    });
  });
  const browserRecovery = async () => {
    capable();
    const observed = env.storage.getItem(MARKER);
    return lock(async () => {
      try {
        if (env.storage.getItem(MARKER) !== observed) throw uncertain();
        if (!observed) return confirm(await context(false));
        let marker;
        try { marker = JSON.parse(observed); } catch { throw uncertain(); }
        if (!['LOGIN','LOGOUT'].includes(marker.kind) || !/^[0-9a-f-]{36}$/i.test(marker.preFamilyId || '')) throw uncertain();
        canonicalRevision(marker.revision); canonicalRevision(marker.preServerRevision); owns(observed);
        const pre = {familyId:marker.preFamilyId,contextRevision:marker.preServerRevision};
        const user = await settle(await readContext(),pre,marker.kind,observed);
        clearOwned(observed); return user;
      } catch (error) {
        identity = undefined;
        if (!observed && error.code === 'COOKIE_REAUTH_REQUIRED') throw error;
        notify('UNCERTAIN'); throw uncertain();
      }
    },true);
  };
  return {
    browserRecovery,
    browserLogin: (email,password) => transition('LOGIN',{email,password}),
    browserLogout: () => transition('LOGOUT'),
    browserMe: () => lock(async () => confirm(await context())),
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
export const browserRecovery = () => session().browserRecovery();
export const subscribeSessionChange = callback => session().subscribeSessionChange(callback);
