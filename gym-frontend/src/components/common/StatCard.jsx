import { ArrowUpRight } from 'lucide-react';
import Badge from './Badge';
import Card from './Card';

const toneClasses = {
  emerald: 'bg-emerald-500/10 text-brand',
  sky: 'bg-sky-500/10 text-sky-700 dark:text-sky-300',
  amber: 'bg-amber-500/10 text-amber-700 dark:text-amber-300',
  rose: 'bg-rose-500/10 text-rose-600 dark:text-rose-300',
  violet: 'bg-violet-500/10 text-violet-600 dark:text-violet-300',
};

const StatCard = ({ detail, icon: Icon, title, tone = 'emerald', trend, value }) => (
  <Card className="min-h-40">
    <div className="flex items-start justify-between gap-3">
      <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${toneClasses[tone]}`}>
        {Icon ? <Icon className="h-5 w-5" /> : <ArrowUpRight className="h-5 w-5" />}
      </div>
      {trend ? <Badge tone={tone}>{trend}</Badge> : null}
    </div>
    <p className="mt-5 text-xs font-medium text-muted">{title}</p>
    <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums text-ink">{value}</p>
    {detail ? <p className="mt-2 text-xs leading-5 text-muted">{detail}</p> : null}
  </Card>
);

export default StatCard;

