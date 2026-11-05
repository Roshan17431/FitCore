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
const CookieAuthProvider = ({ children }) => {
  const [user, setUser] = useState(null), [loading, setLoading] = useState(true), [sessionError, setSessionError] = useState('');
  const [sessionRevision, setSessionRevision] = useState(0);
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
      setUser(current); setSessionError('');
    } catch (error) {
      if (version === generation.current) setSessionError(formatApiError(error, 'Unable to confirm this session.'));
    } finally { if (version === generation.current) setLoading(false); }
  }, [invalidate]);
  useEffect(() => {
    const unsubscribe = subscribeSessionChange(event => {
      // Local transitions belong to this provider's pending login/logout. External
      // changes invalidate that request's generation as well as all tenant caches.
      if (event.external || event.state === 'ENDED') generation.current++;
      if (event.state !== 'IDLE') {
        invalidate();
        if (event.external || event.state === 'ENDED') setLoading(false);
        if (event.state === 'UNCERTAIN') setSessionError('Cookie delivery is uncertain. This browser session is blocked; contact the local test operator.');
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
    try { await authService.logout(); }
    catch (error) { if (version === generation.current) setSessionError(formatApiError(error)); }
    finally { localWriters.current--; }
  }, [invalidate]);
  const login = async (email, password) => {
    localWriters.current++;
    const version = ++generation.current;
    invalidate(); setSessionError('');
    try {
      const current = await authService.login(email, password);
      if (version !== generation.current) return {success:false,error:'Session changed. Sign in again.'};
      clearForeignTrainingState(current); setUser(current); setLoading(false);
      return {success:true,user:current};
    } catch (error) {
      if (version === generation.current) { invalidate(); setLoading(false); setSessionError(formatApiError(error)); }
      return {success:false,error:formatApiError(error)};
    } finally { localWriters.current--; }
  };
  const unavailable = async () => ({success:false,error:'Registration, verification and recovery are unavailable in this cookie test environment.'});
  const value = {
    user, loading, sessionError, sessionRevision, cookieTransport:true,
    invitationToken:null, securityToken:null,
    clearInvitation:() => {}, captureIncomingInvitation:() => {}, clearSecurity:() => {}, captureIncomingSecurity:() => {},
    login, logout, refresh:restore, register:unavailable, registerOwner:unavailable, resetPassword:unavailable,
    // Receipts never authenticate or write browser cookies. These endpoints are
    // disabled in this spike; no token-bearing legacy path is reachable here.
    verifyEmail:async (email, token) => { try { await authService.verifyEmail({email,token}); return {success:true,verified:true}; } catch (error) { return {success:false,error:formatApiError(error)}; } },
    completeReset:input => authService.completeReset(input),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
export const AuthProvider = props => cookieMode ? <CookieAuthProvider {...props} /> : <BearerAuthProvider {...props} />;
