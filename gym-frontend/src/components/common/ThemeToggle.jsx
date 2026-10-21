import { useTheme } from '../../hooks/useTheme';

const ThemeToggle = () => {
  const { theme, setTheme } = useTheme();
  return (
    <label className="relative block shrink-0">
      <span className="sr-only">Color theme</span>
      <select aria-label="Color theme" className="h-11 w-[5.75rem] rounded-xl border border-control-line bg-surface px-2 text-xs font-semibold text-ink sm:w-24" value={theme} onChange={(event) => setTheme(event.target.value)}>
        <option value="light">Light</option>
        <option value="dark">Dark</option>
        <option value="system">System</option>
      </select>
    </label>
  );
};
export default ThemeToggle;
