import { Activity } from 'lucide-react';
import Card from '../common/Card';

const ActivityFeed = ({ activities }) => (
  <Card>
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-base font-bold text-ink">Recent Activities</h2>
      <Activity className="h-5 w-5 text-emerald-500" />
    </div>
    <div className="space-y-4">
      {activities.length ? activities.map((activity) => (
        <div className="flex gap-3" key={activity.id}>
          <div className="mt-1 h-2.5 w-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-500/10" />
          <div className="min-w-0">
            <p className="font-semibold text-ink">{activity.title}</p>
            <p className="text-sm text-muted">{activity.meta}</p>
            <p className="mt-1 text-xs font-semibold uppercase text-muted">{activity.time}</p>
          </div>
        </div>
      )) : <p className="text-sm text-muted">No recent activities found.</p>}
    </div>
  </Card>
);

export default ActivityFeed;
