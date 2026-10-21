import { Loader2 } from 'lucide-react';
import { cx } from '../../utils/formatters';
const variants = {
  primary: 'bg-brand text-on-brand shadow-sm hover:bg-brand-hover',
  secondary: 'bg-ink text-surface hover:opacity-90',
  outline: 'border border-control-line bg-surface text-ink hover:bg-surface-alt',
  ghost: 'text-muted hover:bg-surface-alt hover:text-ink',
  danger: 'bg-rose-700 text-white hover:bg-rose-800 dark:bg-rose-300 dark:text-rose-950 dark:hover:bg-rose-200',
};
const sizes = { sm: 'min-h-11 px-3 text-sm', md: 'min-h-11 px-4 py-2.5 text-sm', lg: 'min-h-12 px-5 py-3 text-base', icon: 'h-11 w-11 p-0' };
const Button = ({ children, className, disabled = false, icon: Icon, isLoading = false, size = 'md', type = 'button', variant = 'primary', ...props }) => (
  <button className={cx('inline-flex shrink-0 items-center justify-center gap-2 rounded-xl font-semibold transition duration-150 disabled:cursor-not-allowed disabled:opacity-60', variants[variant], sizes[size], className)} aria-busy={isLoading || undefined} disabled={disabled || isLoading} type={type} {...props}>
    {isLoading ? <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" /> : Icon ? <Icon aria-hidden="true" className="h-4 w-4" /> : null}
    {children}
  </button>
);
export default Button;
