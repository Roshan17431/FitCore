import { Loader2 } from 'lucide-react';
import { cx } from '../../utils/formatters';

const variants = {
  primary:
    'bg-emerald-500 text-white shadow-sm shadow-emerald-500/25 hover:bg-emerald-600 focus-visible:ring-emerald-400',
  secondary:
    'bg-slate-900 text-white hover:bg-slate-800 focus-visible:ring-slate-400 dark:bg-white dark:text-slate-950 dark:hover:bg-slate-200',
  outline:
    'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 focus-visible:ring-slate-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800',
  ghost:
    'text-slate-600 hover:bg-slate-100 focus-visible:ring-slate-300 dark:text-slate-300 dark:hover:bg-slate-800',
  danger:
    'bg-rose-500 text-white shadow-sm shadow-rose-500/25 hover:bg-rose-600 focus-visible:ring-rose-400',
};

const sizes = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-5 text-base',
  icon: 'h-10 w-10 p-0',
};

const Button = ({
  children,
  className,
  icon: Icon,
  isLoading = false,
  size = 'md',
  type = 'button',
  variant = 'primary',
  ...props
}) => (
  <button
    className={cx(
      'inline-flex shrink-0 items-center justify-center gap-2 rounded-lg font-semibold transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-60 dark:focus-visible:ring-offset-slate-950',
      variants[variant],
      sizes[size],
      className,
    )}
    type={type}
    {...props}
  >
    {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : Icon ? <Icon className="h-4 w-4" /> : null}
    {children}
  </button>
);

export default Button;

