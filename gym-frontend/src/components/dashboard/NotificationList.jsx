import { Bell } from 'lucide-react';
import Badge from '../common/Badge';
import Card from '../common/Card';

const NotificationList = ({ notifications }) => (
  <Card>
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-base font-bold text-ink">Notifications</h2>
      <Bell className="h-5 w-5 text-amber-500" />
    </div>
    <div className="space-y-3">
      {notifications.length ? notifications.map((notification) => (
        <div
          className="flex items-start justify-between gap-3 rounded-lg bg-surface-alt p-3"
          key={notification.id}
        >
          <p className="text-sm font-medium text-ink">{notification.title}</p>
          <Badge tone="amber">{notification.type}</Badge>
        </div>
      )) : <p className="text-sm text-muted">No notifications found.</p>}
    </div>
  </Card>
);

export default NotificationList;
