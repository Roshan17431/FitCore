import { useCallback, useContext, useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { CalendarCheck, ClipboardCheck, Clock, History, LogIn, LogOut } from 'lucide-react';
import ChartCard from '../components/charts/ChartCard';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import StateBlock from '../components/common/StateBlock';
import DataTable from '../components/tables/DataTable';
import { AuthContext } from '../context/auth-context';
import { attendanceService, formatApiError } from '../services/api';
import { formatDate } from '../utils/formatters';

const tabs = [
  { path: '/attendance/check-in', label: 'Check-In', icon: ClipboardCheck },
  { path: '/attendance/history', label: 'History', icon: History },
  { path: '/attendance/statistics', label: 'Statistics', icon: CalendarCheck },
];

const historyColumns = [
  { key: 'date', header: 'Date', render: (row) => formatDate(row.date) },
  { key: 'checkInTime', header: 'Check-In Time' },
  { key: 'checkOutTime', header: 'Check-Out Time' },
];

const Attendance = ({ section = 'check-in' }) => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [session, setSession] = useState({ checkedIn: false, checkInTime: '', checkOutTime: '', raw: null });
  const [submitting, setSubmitting] = useState(false);

  const loadAttendance = useCallback(async () => {
    if (!user?.id) return;
    setError('');
    try {
      const attendance = await attendanceService.getAttendance(user.id);
      setData(attendance);
      setSession(attendance.currentSession || { checkedIn: false, checkInTime: '', checkOutTime: '', raw: null });
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load attendance.'));
    }
  }, [user?.id]);

  useEffect(() => {
    loadAttendance();
  }, [loadAttendance]);

  if (error) return <StateBlock message={error} onAction={loadAttendance} />;
  if (!data) return <LoadingSpinner label="Loading attendance" />;

  const handleCheckIn = async () => {
    if (!user?.id) return;
    setSubmitting(true);
    try {
      setSession(await attendanceService.checkIn(user.id));
      await loadAttendance();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to check in.'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCheckOut = async () => {
    if (!session.raw?.id) return;
    setSubmitting(true);
    try {
      setSession(await attendanceService.checkOut(session.raw.id));
      await loadAttendance();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to check out.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        description="Record member check-ins and check-outs, review attendance history, and monitor visit patterns."
        icon={ClipboardCheck}
        kicker="Attendance"
        title="Attendance System"
      />
      <ModuleTabs tabs={tabs} />

      {section === 'check-in' ? (
        <div className="grid gap-4 lg:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <div className="flex items-start justify-between gap-3">
              <div>
                <Badge tone={session.checkedIn ? 'emerald' : 'slate'}>
                  {session.checkedIn ? 'Checked In' : 'Ready'}
                </Badge>
                <h2 className="mt-4 text-2xl font-bold text-ink">Member Attendance</h2>
                <p className="mt-2 text-sm text-muted">Use the controls below for today's visit session.</p>
              </div>
              <Clock className="h-6 w-6 text-emerald-500" />
            </div>
            <div className="mt-6 grid gap-3 sm:grid-cols-2">
              <Button disabled={session.checkedIn || submitting} icon={LogIn} isLoading={submitting && !session.checkedIn} onClick={handleCheckIn}>
                Check-In
              </Button>
              <Button disabled={!session.checkedIn || submitting} icon={LogOut} isLoading={submitting && session.checkedIn} onClick={handleCheckOut} variant="outline">
                Check-Out
              </Button>
            </div>
          </Card>

          <Card>
            <h2 className="text-lg font-bold text-ink">Current Session</h2>
            <div className="mt-5 grid gap-4 sm:grid-cols-3">
              <div className="rounded-lg bg-surface-alt p-4">
                <p className="text-sm font-semibold text-muted">Date</p>
                <p className="mt-1 font-bold text-ink">{formatDate(new Date())}</p>
              </div>
              <div className="rounded-lg bg-surface-alt p-4">
                <p className="text-sm font-semibold text-muted">Check-In Time</p>
                <p className="mt-1 font-bold text-ink">{session.checkInTime || '--'}</p>
              </div>
              <div className="rounded-lg bg-surface-alt p-4">
                <p className="text-sm font-semibold text-muted">Check-Out Time</p>
                <p className="mt-1 font-bold text-ink">{session.checkOutTime || '--'}</p>
              </div>
            </div>
          </Card>
        </div>
      ) : null}

      {section === 'history' ? <DataTable columns={historyColumns} data={data.attendanceHistory} /> : null}

      {section === 'statistics' ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <ChartCard subtitle="Visits by day" title="Weekly Attendance">
            <ResponsiveContainer height="100%" width="100%">
              <BarChart data={data.weeklyAttendance}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Bar isAnimationActive={false} dataKey="visits" fill="var(--chart-1)" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard subtitle="Visits by month" title="Monthly Attendance">
            <ResponsiveContainer height="100%" width="100%">
              <LineChart data={data.monthlyAttendance}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Line isAnimationActive={false} dataKey="visits" stroke="var(--chart-2)" strokeWidth={3} type="monotone" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>
      ) : null}
    </div>
  );
};

export default Attendance;
