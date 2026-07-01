import { CalendarClock } from 'lucide-react';
import Badge from '../common/Badge';
import Card from '../common/Card';
import { formatDate } from '../../utils/formatters';

const UpcomingSessions = ({ sessions }) => (
  <Card>
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-base font-bold text-slate-950 dark:text-white">Upcoming Sessions</h2>
      <CalendarClock className="h-5 w-5 text-sky-500" />
    </div>
    <div className="space-y-3">
      {sessions.map((session) => (
        <div
          className="rounded-lg border border-slate-200 p-3 dark:border-slate-800"
          key={session.id}
        >
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-semibold text-slate-900 dark:text-white">{session.focus}</p>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{session.trainer}</p>
            </div>
            <Badge tone="sky">{session.time}</Badge>
          </div>
          <p className="mt-3 text-sm font-medium text-slate-600 dark:text-slate-300">{formatDate(session.date)}</p>
        </div>
      ))}
    </div>
  </Card>
);

export default UpcomingSessions;

