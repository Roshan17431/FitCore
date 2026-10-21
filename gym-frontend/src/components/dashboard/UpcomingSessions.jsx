import { CalendarClock } from 'lucide-react';
import Badge from '../common/Badge';
import Card from '../common/Card';
import { formatDate } from '../../utils/formatters';

const UpcomingSessions = ({ sessions }) => (
  <Card>
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-base font-bold text-ink">Upcoming Sessions</h2>
      <CalendarClock className="h-5 w-5 text-sky-500" />
    </div>
    <div className="space-y-3">
      {sessions.length ? sessions.map((session) => (
        <div
          className="rounded-lg border border-line p-3"
          key={session.id}
        >
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-semibold text-ink">{session.focus}</p>
              <p className="mt-1 text-sm text-muted">{session.trainer}</p>
            </div>
            <Badge tone="sky">{session.time}</Badge>
          </div>
          <p className="mt-3 text-sm font-medium text-muted">{formatDate(session.date)}</p>
        </div>
      )) : <p className="text-sm text-muted">No upcoming sessions found.</p>}
    </div>
  </Card>
);

export default UpcomingSessions;
