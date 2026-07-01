import { ArrowUpRight } from 'lucide-react';
import Badge from './Badge';
import Card from './Card';

const toneClasses = {
  emerald: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
  sky: 'bg-sky-500/10 text-sky-600 dark:text-sky-300',
  amber: 'bg-amber-500/10 text-amber-600 dark:text-amber-300',
  rose: 'bg-rose-500/10 text-rose-600 dark:text-rose-300',
  violet: 'bg-violet-500/10 text-violet-600 dark:text-violet-300',
};

const StatCard = ({ detail, icon: Icon, title, tone = 'emerald', trend, value }) => (
  <Card className="min-h-36">
    <div className="flex items-start justify-between gap-3">
      <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${toneClasses[tone]}`}>
        {Icon ? <Icon className="h-5 w-5" /> : <ArrowUpRight className="h-5 w-5" />}
      </div>
      {trend ? <Badge tone={tone}>{trend}</Badge> : null}
    </div>
    <p className="mt-5 text-sm font-medium text-slate-500 dark:text-slate-400">{title}</p>
    <p className="mt-1 text-2xl font-bold text-slate-950 dark:text-white">{value}</p>
    {detail ? <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{detail}</p> : null}
  </Card>
);

export default StatCard;

