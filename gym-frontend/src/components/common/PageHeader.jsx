import { cx } from '../../utils/formatters';

const PageHeader = ({ actions, description, icon: Icon, kicker, title }) => (
  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
    <div className="flex min-w-0 gap-3">
      {Icon ? (
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10 text-brand ring-1 ring-emerald-500/20">
          <Icon className="h-5 w-5" />
        </div>
      ) : null}
      <div className="min-w-0">
        {kicker ? <p className="text-[10px] font-bold uppercase tracking-[.18em] text-brand">{kicker}</p> : null}
        <h1
          className={cx(
            'text-balance text-2xl font-semibold tracking-tight text-ink sm:text-3xl',
            !description && 'leading-10',
          )}
        >
          {title}
        </h1>
        {description ? <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">{description}</p> : null}
      </div>
    </div>
    {actions ? <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">{actions}</div> : null}
  </div>
);

export default PageHeader;

