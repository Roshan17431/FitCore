import { useCallback, useEffect, useRef, useState } from 'react';
import { cookieMode, subscribeCookieLifecycle, subscribeSessionChange } from '../services/browser-session.js';
import { captureSecurityToken } from '../utils/security-token';
import { AuthContext } from './auth-context';
import { authService, formatApiError } from '../services/api';
import { captureInvitation, readSessionToken, clearTrainingState, clearForeignTrainingState } from '../utils/tenant-session';

const storedToken = readSessionToken;
const clearSession = () => { for (const storage of [localStorage, sessionStorage]) { storage.removeItem('token'); storage.removeItem('user'); } clearTrainingState(); };
const BearerAuthProvider = ({ children, initialInvitation = null, initialSecurity = null }) => {
  const [user, setUser] = useState(null), [loading, setLoading] = useState(true), [sessionError, setSessionError] = useState('');
  const [invitationToken, setInvitationToken] = useState(initialInvitation);
  const [securityToken, setSecurityToken] = useState(initialSecurity);
  const clearSecurity = useCallback(() => setSecurityToken(null), []);
  const captureIncomingSecurity = useCallback(() => { if (window.location.hash) { const incoming = captureSecurityToken(); if (incoming) setSecurityToken(incoming); } }, []);
  const generation = useRef(0);
  const logout = useCallback(() => { generation.current++; clearSession(); setUser(null); setInvitationToken(null); setSecurityToken(null); setLoading(false); }, []);
  const restore = useCallback(async () => {
    const version = ++generation.current, token = storedToken();
    setUser(null); setLoading(true);
    if (!token) { setLoading(false); return; }
    try {
      const current = await authService.me();
      if (version !== generation.current || storedToken() !== token) return;
      const storage = localStorage.getItem('token') ? localStorage : sessionStorage;
      clearForeignTrainingState(current); storage.setItem('user', JSON.stringify(current)); setUser(current); setSessionError('');
    } catch (error) {
      if (version === generation.current) { clearSession(); setUser(null); setSessionError(formatApiError(error, 'Unable to restore your session. Sign in again.')); }
    } finally { if (version === generation.current) setLoading(false); }
  }, []);
  const clearInvitation = useCallback(() => setInvitationToken(null), []);
  const captureIncomingInvitation = useCallback(() => {
    if (['/register', '/secure/register'].includes(window.location.pathname) && window.location.hash) setInvitationToken(captureInvitation());
  }, []);
  useEffect(() => {
    restore();
    const ended = event => { logout(); setSessionError(event.detail); };
    const changed = event => { if (event.key === 'token' || event.key === null) { if (!localStorage.getItem('token')) logout(); else { clearTrainingState(); restore(); } } };
    window.addEventListener('fitcore:session-ended', ended); window.addEventListener('storage', changed); window.addEventListener('hashchange', captureIncomingInvitation); window.addEventListener('hashchange', captureIncomingSecurity);
    const identityGeneration = generation;
    return () => { identityGeneration.current++; window.removeEventListener('fitcore:session-ended', ended); window.removeEventListener('storage', changed); window.removeEventListener('hashchange', captureIncomingInvitation); window.removeEventListener('hashchange', captureIncomingSecurity); };
  }, [restore, logout, captureIncomingInvitation, captureIncomingSecurity]);
  const authenticate = async (request, remember = true, allowVerifiedOnly = false) => {
    const version = ++generation.current;
    clearSession(); setUser(null); setSessionError('');
    try {
      const result = await request();
      if (version !== generation.current) return { success: false, error: 'Session changed. Please try again.' };
      if (allowVerifiedOnly && !result?.token) return { success: true, verified: true };
      const storage = remember ? localStorage : sessionStorage;
      storage.setItem('token', result.token);
      const current = await authService.me();
      if (version !== generation.current || storedToken() !== result.token) return { success: false, error: 'Session changed. Please try again.' };
      storage.setItem('user', JSON.stringify(current)); setUser(current); setInvitationToken(null);
      return { success: true, user: current };
    } catch (error) {
      if (version === generation.current) { clearSession(); setUser(null); }
      return { success: false, error: formatApiError(error, 'Unable to establish your session.') };
    }
  };
  const value = {
    user, loading, sessionError, invitationToken, securityToken, clearSecurity, captureIncomingSecurity,
    verifyEmail: (email, token) => authenticate(() => authService.verifyEmail({ email, token }), true, true),
    completeReset: async input => { const version = generation.current, token = storedToken(); await authService.completeReset(input); if (version === generation.current && storedToken() === token) logout(); },
    clearInvitation, captureIncomingInvitation,
    refresh: async () => { const current = await authService.me(); setUser(current); return current; },
    login: (email, password, remember = true) => authenticate(() => authService.login(email, password), remember),
    register: input => authenticate(() => authService.register(input)),
    registerOwner: input => authenticate(() => authService.registerOwner(input)),
    resetPassword: async email => { try { await authService.resetPassword(email); return { success: true }; } catch (error) { return { success: false, error: formatApiError(error) }; } },
    logout,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

// Cookie identity stays in React memory and always comes from a fresh authoritative me.
const CookieAuthProvider = ({ children, initialInvitation = null, initialSecurity = null }) => {
  const [user, setUser] = useState(null), [loading, setLoading] = useState(true), [sessionError, setSessionError] = useState('');
  const [sessionRevision, setSessionRevision] = useState(0);
  const [invitationToken, setInvitationToken] = useState(initialInvitation), [securityToken, setSecurityToken] = useState(initialSecurity);
  const clearInvitation = useCallback(() => setInvitationToken(null), []);
  const clearSecurity = useCallback(() => setSecurityToken(null), []);
  const captureIncomingInvitation = useCallback(() => {
    if (['/register', '/secure/register'].includes(window.location.pathname) && window.location.hash) setInvitationToken(captureInvitation());
  }, []);
  const captureIncomingSecurity = useCallback(() => {
    if (window.location.hash) { const incoming = captureSecurityToken(); if (incoming) setSecurityToken(incoming); }
  }, []);
  const [sessionFreshContextRequired, setSessionFreshContextRequired] = useState(false);
  useEffect(() => {
    window.addEventListener('hashchange', captureIncomingInvitation); window.addEventListener('hashchange', captureIncomingSecurity);
    return () => { window.removeEventListener('hashchange', captureIncomingInvitation); window.removeEventListener('hashchange', captureIncomingSecurity); };
  }, [captureIncomingInvitation, captureIncomingSecurity]);
  const [sessionRecoveryAvailable, setSessionRecoveryAvailable] = useState(false), [sessionSignOutRequired, setSessionSignOutRequired] = useState(false);
  const recordError = useCallback(error => {
    setSessionError(formatApiError(error, 'Unable to confirm this session.'));
    setSessionRecoveryAvailable(error.code === 'COOKIE_UNCERTAIN' || error.code === 'COOKIE_LOCK_TIMEOUT');
    setSessionSignOutRequired(error.code === 'COOKIE_REAUTH_REQUIRED');
    setSessionFreshContextRequired(error.code === 'COOKIE_FRESH_CONTEXT_REQUIRED');
  }, []);
  const generation = useRef(0), localWriters = useRef(0), displayedUser = useRef(null);
  displayedUser.current = user;
  const invalidate = useCallback(() => {
    setUser(null); try { clearSession(); } catch { /* The adapter reports unavailable persistent storage. */ } setSessionRevision(value => value + 1);
  }, []);
  const restore = useCallback(async () => {
    const version = ++generation.current;
    invalidate(); setLoading(true);
    try {
      const current = await authService.me();
      if (version !== generation.current) return;
      if (current) clearForeignTrainingState(current);
      setUser(current); setSessionError(''); setSessionRecoveryAvailable(false); setSessionSignOutRequired(false); setSessionFreshContextRequired(false);
    } catch (error) {
      if (version === generation.current) recordError(error);
    } finally { if (version === generation.current) setLoading(false); }
  }, [invalidate, recordError]);
  useEffect(() => {
    const unsubscribe = subscribeSessionChange(event => {
      // Local transitions belong to this provider's pending login/logout. External
      // changes invalidate that request's generation as well as all tenant caches.
      if (event.external || event.state === 'ENDED') generation.current++;
      if (event.state !== 'IDLE') {
        invalidate();
        if (event.external || event.state === 'ENDED') setLoading(false);
        if (event.state === 'UNCERTAIN') { setSessionRecoveryAvailable(true); setSessionSignOutRequired(false); setSessionError('Cookie delivery is uncertain. Check sign-in status before continuing.'); }
        if (event.state === 'ENDED') setSessionError('Your session changed or is unavailable. Sign in again.');
      } else if (event.external) { void restore(); }
    });
    const unsubscribeLifecycle = subscribeCookieLifecycle({
      windowTarget: window, documentTarget: document,
      // Only revalidate an accepted view. Login forms need no foreground bootstrap.
      canRestore: () => localWriters.current === 0 && displayedUser.current !== null,
      restore,
    });
    void restore();
    const currentGeneration = generation;
    return () => { currentGeneration.current++; unsubscribeLifecycle(); unsubscribe(); };
  }, [invalidate, restore]);
  const logout = useCallback(async () => {
    localWriters.current++;
    const version = ++generation.current;
    invalidate(); setLoading(false); setSessionError('');
    try {
      await authService.logout();
      if (version === generation.current) { setSessionRecoveryAvailable(false); setSessionSignOutRequired(false); setSessionFreshContextRequired(false); }
    }
    catch (error) { if (version === generation.current) recordError(error); }
    finally { localWriters.current--; }
  }, [invalidate, recordError]);
  const recoverSession = async () => {
    localWriters.current++;
    const version = ++generation.current;
    invalidate(); setLoading(true); setSessionError('');
    try {
      const current = await authService.browserRecovery();
      if (version !== generation.current) return {success:false,error:'Session changed. Check sign-in status again.'};
      if (current) clearForeignTrainingState(current);
      setUser(current); setSessionRecoveryAvailable(false); setSessionSignOutRequired(false); setSessionFreshContextRequired(false);
      return {success:true,user:current};
    } catch (error) {
      if (version === generation.current) recordError(error);
      return {success:false,error:formatApiError(error)};
    } finally { localWriters.current--; if (version === generation.current) setLoading(false); }
  };
  const login = async (email, password) => {
    localWriters.current++;
    const version = ++generation.current;
    invalidate(); setSessionError('');
    try {
      const current = await authService.login(email, password);
      if (version !== generation.current) return {success:false,error:'Session changed. Sign in again.'};
      clearForeignTrainingState(current); setUser(current); setLoading(false); setSessionRecoveryAvailable(false); setSessionSignOutRequired(false); setSessionFreshContextRequired(false);
      return {success:true,user:current};
    } catch (error) {
      if (version === generation.current) {
        invalidate(); setLoading(false); recordError(error);
        if (error.cookieDefinitiveDenial && error.cookieSessionUser) { clearForeignTrainingState(error.cookieSessionUser); setUser(error.cookieSessionUser); }
      }
      return {success:false,error:formatApiError(error)};
    } finally { localWriters.current--; }
  };
  const accountRequest = async request => {
    const version = generation.current;
    localWriters.current++;
    try {
      await request();
      if (version !== generation.current) throw Object.assign(new Error('Session changed. Please try again.'), {code:'ERR_CANCELED'});
      setSessionError(''); setSessionRecoveryAvailable(false); setSessionSignOutRequired(false); setSessionFreshContextRequired(false);
    } catch (error) { if (version === generation.current) recordError(error); throw error; }
    finally { localWriters.current--; }
  };
  const pendingOwner = input => accountRequest(() => authService.pendingOwner(input));
  const pendingInvite = input => accountRequest(() => authService.pendingInvite(input));
  const pendingResult = async request => {
    try { await request(); return {success:true,pending:true}; }
    catch (error) { return {success:false,error:formatApiError(error)}; }
  };
  const value = {
    user, loading, sessionError, sessionRevision, cookieTransport:true, sessionRecoveryAvailable, sessionSignOutRequired, sessionFreshContextRequired, recoverSession,
    invitationToken, securityToken, clearInvitation, clearSecurity, captureIncomingInvitation, captureIncomingSecurity,
    login, logout, refresh:restore, pendingOwner, pendingInvite,
    register:input => pendingResult(() => pendingInvite(input)),
    registerOwner:input => pendingResult(() => pendingOwner(input)),
    requestVerification:email => accountRequest(() => authService.requestVerification(email)),
    resetPassword:async email => {
      try { await accountRequest(() => authService.resetPassword(email)); return {success:true}; }
      catch (error) { return {success:false,error:formatApiError(error)}; }
    },
    // Empty receipts never install identity, call me, or log in implicitly.
    verifyEmail:async (email, token) => {
      try { await accountRequest(() => authService.verifyEmail({email,token})); return {success:true,verified:true}; }
      catch (error) { return {success:false,error:formatApiError(error)}; }
    },
    completeReset:input => accountRequest(() => authService.completeReset(input)),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
export const AuthProvider = props => cookieMode ? <CookieAuthProvider {...props} /> : <BearerAuthProvider {...props} />;
