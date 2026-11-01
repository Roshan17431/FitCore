export const dashboardPath = user => user?.role === 'OWNER' ? '/owner' : '/dashboard';
export const canManage = user => ['OWNER', 'ADMIN'].includes(user?.role);
export const canEditAccount = (actor, target) => canManage(actor) && target?.role !== 'OWNER' && (actor.role === 'OWNER' || target?.role !== 'ADMIN');
export const workoutKey = user => `workoutPlan:${user.gymId}:${user.id}`;
export const clearTrainingState = () => {
  for (const storage of [localStorage, sessionStorage]) {
    const keys = Array.from({ length: storage.length }, (_, i) => storage.key(i));
    keys.filter(key => key?.startsWith('workoutPlan:')).forEach(key => storage.removeItem(key));
  }
};
export const captureInvitation = () => {
  if (window.location.pathname !== '/register') return null;
  const params = new URLSearchParams(window.location.hash.slice(1));
  const token = params.get('invitationToken');
  if (window.location.hash) window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
  return token && /^[A-Za-z0-9_-]{43}$/.test(token) ? token : null;
};
export const validateSignup = (form, owner = false) => {
  if (form.fullName.trim().length < 3 || form.fullName.trim().length > 100) return 'Full name must contain 3–100 characters.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) || form.email.length > 254) return 'Enter a valid email address.';
  if (form.password !== form.confirmPassword) return 'Passwords do not match.';
  if (form.password.length < 12 || form.password.length > 64 || new TextEncoder().encode(form.password).length > 72 || !/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password) || !/[0-9]/.test(form.password) || !/[^a-zA-Z0-9]/.test(form.password)) return 'Use 12–64 characters with uppercase, lowercase, a number and a symbol (maximum 72 UTF-8 bytes).';
  if (owner && (!form.gymName.trim() || form.gymName.trim().length > 100 || !/^[a-z0-9][a-z0-9-]{2,62}$/.test(form.slug))) return 'Enter a gym name and a 3–63 character slug using lowercase letters, numbers and hyphens.';
  return '';
};

export const clearForeignTrainingState = user => {
  for (const storage of [localStorage, sessionStorage]) {
    const keys = Array.from({ length: storage.length }, (_, i) => storage.key(i));
    keys.filter(key => key?.startsWith('workoutPlan:') && key !== workoutKey(user)).forEach(key => storage.removeItem(key));
  }
};

// A remembered identity supersedes any older identity private to this tab.
export const readSessionToken = () => {
  const remembered = localStorage.getItem('token');
  if (remembered) {
    sessionStorage.removeItem('token'); sessionStorage.removeItem('user');
    return remembered;
  }
  return sessionStorage.getItem('token');
};
