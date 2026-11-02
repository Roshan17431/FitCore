import { useCallback, useEffect, useRef, useState } from 'react';
import { captureSecurityToken } from '../utils/security-token';
import { AuthContext } from './auth-context';
import { authService, formatApiError } from '../services/api';
import { captureInvitation, readSessionToken, clearTrainingState, clearForeignTrainingState } from '../utils/tenant-session';

const storedToken = readSessionToken;
const clearSession = () => { for (const storage of [localStorage, sessionStorage]) { storage.removeItem('token'); storage.removeItem('user'); } clearTrainingState(); };
export const AuthProvider = ({ children, initialInvitation = null, initialSecurity = null }) => {
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
