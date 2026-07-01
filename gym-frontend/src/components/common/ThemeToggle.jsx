import { Moon, Sun } from 'lucide-react';
import Button from './Button';
import { useTheme } from '../../hooks/useTheme';

const ThemeToggle = () => {
  const { isDark, toggleTheme } = useTheme();
  const Icon = isDark ? Sun : Moon;

  return (
    <Button
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      icon={Icon}
      onClick={toggleTheme}
      size="icon"
      title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      variant="outline"
    />
  );
};

export default ThemeToggle;
