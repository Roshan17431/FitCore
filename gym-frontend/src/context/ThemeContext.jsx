import { useEffect, useMemo, useState } from 'react';
import { ThemeContext } from './theme-context';
import { applyTheme, normalizeTheme, readTheme, THEME_KEY } from '../utils/theme';

export const ThemeProvider = ({ children }) => {
  const [theme, updateTheme] = useState(readTheme);
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches);
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => setSystemDark(media.matches);
    media.addEventListener('change', onChange);
    onChange();
    return () => media.removeEventListener('change', onChange);
  }, []);
  useEffect(() => {
    applyTheme(theme, systemDark);
    try { localStorage.setItem(THEME_KEY, theme); } catch { /* Theme remains usable when preference storage is blocked. */ }
  }, [theme, systemDark]);
  useEffect(() => {
    const onStorage = (event) => { if (event.key === THEME_KEY || event.key === null) updateTheme(readTheme()); };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);
  const value = useMemo(() => ({
    theme, isDark: theme === 'dark' || (theme === 'system' && systemDark),
    setTheme: (next) => updateTheme(normalizeTheme(next)),
    toggleTheme: () => updateTheme((current) => (current === 'dark' || (current === 'system' && systemDark)) ? 'light' : 'dark'),
  }), [theme, systemDark]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};
