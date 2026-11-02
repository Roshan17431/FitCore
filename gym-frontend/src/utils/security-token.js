export const captureSecurityToken = () => {
  const { pathname, hash, search } = window.location;
  if (!['/verify-email', '/reset-password'].includes(pathname) || !hash) return null;
  const token = new URLSearchParams(hash.slice(1)).get('token');
  window.history.replaceState(window.history.state, '', pathname + search);
  return { path: pathname, token: token && /^[A-Za-z0-9_-]{43}$/.test(token) ? token : null };
};
export const validateResetPassword = (password, confirmation) => {
  if (password !== confirmation) return 'Passwords do not match.';
  if (password.length < 12 || password.length > 64 || new TextEncoder().encode(password).length > 72 || !/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/[0-9]/.test(password) || !/[^a-zA-Z0-9]/.test(password)) return 'Use 12–64 characters with uppercase, lowercase, a number and a symbol; maximum 72 UTF-8 bytes.';
  return '';
};
