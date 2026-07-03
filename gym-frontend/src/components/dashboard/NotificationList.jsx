import { Bell } from 'lucide-react';
import Badge from '../common/Badge';
import Card from '../common/Card';

const NotificationList = ({ notifications }) => (
  <Card>
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-base font-bold text-slate-950 dark:text-white">Notifications</h2>
      <Bell className="h-5 w-5 text-amber-500" />
    </div>
    <div className="space-y-3">
      {notifications.length ? notifications.map((notification) => (
        <div
          className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 p-3 dark:bg-slate-800/70"
          key={notification.id}
        >
          <p className="text-sm font-medium text-slate-700 dark:text-slate-200">{notification.title}</p>
          <Badge tone="amber">{notification.type}</Badge>
        </div>
      )) : <p className="text-sm text-slate-500 dark:text-slate-400">No notifications found.</p>}
    </div>
  </Card>
);

export default NotificationList;
