import { Activity } from 'lucide-react';
import Card from '../common/Card';

const ActivityFeed = ({ activities }) => (
  <Card>
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-base font-bold text-slate-950 dark:text-white">Recent Activities</h2>
      <Activity className="h-5 w-5 text-emerald-500" />
    </div>
    <div className="space-y-4">
      {activities.map((activity) => (
        <div className="flex gap-3" key={activity.id}>
          <div className="mt-1 h-2.5 w-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-500/10" />
          <div className="min-w-0">
            <p className="font-semibold text-slate-800 dark:text-slate-100">{activity.title}</p>
            <p className="text-sm text-slate-500 dark:text-slate-400">{activity.meta}</p>
            <p className="mt-1 text-xs font-semibold uppercase text-slate-400 dark:text-slate-500">{activity.time}</p>
          </div>
        </div>
      ))}
    </div>
  </Card>
);

export default ActivityFeed;

