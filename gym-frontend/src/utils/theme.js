export const THEME_KEY = 'fitcore_theme';
export const normalizeTheme = (theme) => ['light', 'dark', 'system'].includes(theme) ? theme : 'system';
export const readTheme = () => {
  try { return normalizeTheme(localStorage.getItem(THEME_KEY)); } catch { return 'system'; }
};
export const applyTheme = (theme, prefersDark) => {
  const dark = theme === 'dark' || (theme === 'system' && prefersDark);
  document.documentElement.classList.toggle('dark', dark);
  document.documentElement.dataset.theme = theme;
  return dark;
};
