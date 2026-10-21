import { cx } from '../../utils/formatters';

const tones = {
  emerald: 'bg-brand-soft text-brand ring-emerald-200 dark:bg-emerald-500/10 dark:ring-emerald-500/20',
  sky: 'bg-sky-50 text-sky-700 ring-sky-200 dark:bg-sky-500/10 dark:text-sky-300 dark:ring-sky-500/20',
  amber: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/20',
  rose: 'bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-500/10 dark:text-rose-300 dark:ring-rose-500/20',
  violet: 'bg-violet-50 text-violet-700 ring-violet-200 dark:bg-violet-500/10 dark:text-violet-300 dark:ring-violet-500/20',
  slate: 'bg-surface-alt text-ink ring-slate-200 dark:ring-slate-700',
};

const Badge = ({ children, className, tone = 'slate' }) => (
  <span
    className={cx(
      'inline-flex min-h-7 items-center rounded-full px-2.5 py-1 text-xs font-semibold ring-1',
      tones[tone],
      className,
    )}
  >
    {children}
  </span>
);

export default Badge;

