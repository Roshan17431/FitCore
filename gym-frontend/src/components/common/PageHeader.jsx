import { cx } from '../../utils/formatters';

const PageHeader = ({ actions, description, icon: Icon, kicker, title }) => (
  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
    <div className="flex min-w-0 gap-3">
      {Icon ? (
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 ring-1 ring-emerald-500/20 dark:text-emerald-300">
          <Icon className="h-5 w-5" />
        </div>
      ) : null}
      <div className="min-w-0">
        {kicker ? <p className="text-sm font-semibold text-emerald-600 dark:text-emerald-300">{kicker}</p> : null}
        <h1
          className={cx(
            'text-balance text-2xl font-bold text-slate-950 dark:text-white sm:text-3xl',
            !description && 'leading-10',
          )}
        >
          {title}
        </h1>
        {description ? <p className="mt-1 max-w-3xl text-sm text-slate-500 dark:text-slate-400">{description}</p> : null}
      </div>
    </div>
    {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
  </div>
);

export default PageHeader;

