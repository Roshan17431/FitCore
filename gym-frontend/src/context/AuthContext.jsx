import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthContext } from './auth-context';
import { authService, formatApiError } from '../services/api';

const TOKEN_KEY = 'token';
const USER_KEY = 'user';

const redactAuthPayload = (payload) => ({
  ...payload,
  password: payload?.password ? '[REDACTED]' : payload?.password,
  confirmPassword: payload?.confirmPassword ? '[REDACTED]' : payload?.confirmPassword,
});

const readStoredSession = () => {
  const token = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
  const savedUser = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);

  if (!token || !savedUser) return null;

  try {
    return {
      token,
      user: JSON.parse(savedUser),
    };
  } catch {
    return null;
  }
};

const persistSession = ({ token, user }, rememberMe = true) => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);

  const storage = rememberMe ? localStorage : sessionStorage;
  storage.setItem(TOKEN_KEY, token);
  storage.setItem(USER_KEY, JSON.stringify(user));
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const session = readStoredSession();
    if (session?.user) setUser(session.user);
    setLoading(false);
  }, []);

  const login = async (email, password, rememberMe = true) => {
    try {
      console.info('[FitCore Auth] login called', { email, rememberMe });
      const { token, user: userData } = await authService.login(email, password);
      persistSession({ token, user: userData }, rememberMe);
      setUser(userData);
      console.info('[FitCore Auth] login succeeded', { userId: userData?.id, email: userData?.email });
      return { success: true };
    } catch (error) {
      console.error('[FitCore Auth] login failed', error);
      return { 
        success: false, 
        error: formatApiError(error, 'Login failed. Please check your credentials.'),
      };
    }
  };

  const register = async (userData) => {
    try {
      console.info('[FitCore Auth] register called', redactAuthPayload(userData));
      const { token, user: newUserData } = await authService.register(userData);
      persistSession({ token, user: newUserData }, true);
      setUser(newUserData);
      console.info('[FitCore Auth] register succeeded', { userId: newUserData?.id, email: newUserData?.email });
      return { success: true };
    } catch (error) {
      console.error('[FitCore Auth] register failed', error);
      return { 
        success: false, 
        error: formatApiError(error, 'Registration failed. Please try again.'),
      };
    }
  };

  const resetPassword = async (email) => {
    try {
      await authService.resetPassword(email);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        error: formatApiError(error, 'Reset request failed.'),
      };
    }
  };

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, resetPassword, logout }),
    [user, loading, logout],
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};
