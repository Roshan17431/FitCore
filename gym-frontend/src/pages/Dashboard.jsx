import { useCallback, useContext, useEffect, useState } from 'react';
import {
  Area,
  AreaChart,
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
import { Activity, CalendarCheck, CreditCard, Flame, Target } from 'lucide-react';
import ChartCard from '../components/charts/ChartCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/common/PageHeader';
import StateBlock from '../components/common/StateBlock';
import StatCard from '../components/common/StatCard';
import ActivityFeed from '../components/dashboard/ActivityFeed';
import NotificationList from '../components/dashboard/NotificationList';
import UpcomingSessions from '../components/dashboard/UpcomingSessions';
import { AuthContext } from '../context/auth-context';
import { dashboardService, formatApiError } from '../services/api';

const statIcons = [CreditCard, Target, Flame, CalendarCheck, Activity];

const Dashboard = () => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const loadDashboard = useCallback(async () => {
    if (!user?.id) return;
    setError('');
    try {
      setData(await dashboardService.getOverview(user.id));
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load dashboard.'));
    }
  }, [user?.id]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  if (error) {
    return <StateBlock message={error} onAction={loadDashboard} />;
  }

  if (!data) return <LoadingSpinner label="Loading dashboard" />;

  return (
    <div className="space-y-6">
      <PageHeader
        description="Track memberships, workouts, nutrition, attendance, and progress from one operational view."
        icon={Activity}
        kicker="Overview"
        title="Dashboard Overview"
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {(data.dashboardCards || []).map((card, index) => (
          <StatCard
            detail={card.detail}
            icon={statIcons[index]}
            key={card.title}
            title={card.title}
            tone={card.tone}
            trend={card.trend}
            value={card.value}
          />
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <ChartCard subtitle="Body weight and body fat trend" title="Weight Progress Chart">
            <ResponsiveContainer height="100%" width="100%">
              <LineChart data={data.weightProgress || []} margin={{ left: 0, right: 16, top: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Line isAnimationActive={false} dataKey="weight" name="Weight kg" stroke="var(--chart-1)" strokeWidth={3} type="monotone" />
                <Line isAnimationActive={false} dataKey="bodyFat" name="Body Fat %" stroke="var(--chart-2)" strokeWidth={3} type="monotone" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>

        <ChartCard subtitle="Daily completion percentage" title="Workout Completion Chart">
          <ResponsiveContainer height="100%" width="100%">
            <BarChart data={data.workoutCompletion || []}>
              <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
              <XAxis dataKey="label" tickLine={false} />
              <YAxis tickLine={false} width={34} />
              <Tooltip />
              <Bar isAnimationActive={false} dataKey="completed" fill="var(--chart-3)" name="Completed %" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <ChartCard subtitle="Visits by week" title="Attendance Chart">
          <ResponsiveContainer height="100%" width="100%">
            <AreaChart data={data.attendanceChart || []} margin={{ left: 0, right: 16, top: 8 }}>
              <defs>
                <linearGradient id="attendanceFill" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="5%" stopColor="var(--chart-4)" stopOpacity={0.45} />
                  <stop offset="95%" stopColor="var(--chart-4)" stopOpacity={0.04} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
              <XAxis dataKey="label" tickLine={false} />
              <YAxis tickLine={false} width={34} />
              <Tooltip />
              <Area isAnimationActive={false} dataKey="visits" fill="url(#attendanceFill)" name="Visits" stroke="var(--chart-4)" strokeWidth={3} type="monotone" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>
        <UpcomingSessions sessions={data.upcomingSessions || []} />
        <NotificationList notifications={data.notifications || []} />
      </div>

      <ActivityFeed activities={data.recentActivities || []} />
    </div>
  );
};

export default Dashboard;
