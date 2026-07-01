import { useEffect, useState } from 'react';
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
import StatCard from '../components/common/StatCard';
import ActivityFeed from '../components/dashboard/ActivityFeed';
import NotificationList from '../components/dashboard/NotificationList';
import UpcomingSessions from '../components/dashboard/UpcomingSessions';
import { dashboardService } from '../services/mockApi';

const statIcons = [CreditCard, Target, Flame, CalendarCheck, Activity];

const Dashboard = () => {
  const [data, setData] = useState(null);

  useEffect(() => {
    dashboardService.getOverview().then(setData);
  }, []);

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
        {data.dashboardCards.map((card, index) => (
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
              <LineChart data={data.weightProgress} margin={{ left: 0, right: 16, top: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Line dataKey="weight" name="Weight kg" stroke="#10b981" strokeWidth={3} type="monotone" />
                <Line dataKey="bodyFat" name="Body Fat %" stroke="#0ea5e9" strokeWidth={3} type="monotone" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>

        <ChartCard subtitle="Daily completion percentage" title="Workout Completion Chart">
          <ResponsiveContainer height="100%" width="100%">
            <BarChart data={data.workoutCompletion}>
              <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
              <XAxis dataKey="label" tickLine={false} />
              <YAxis tickLine={false} width={34} />
              <Tooltip />
              <Bar dataKey="completed" fill="#f59e0b" name="Completed %" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <ChartCard subtitle="Visits by week" title="Attendance Chart">
          <ResponsiveContainer height="100%" width="100%">
            <AreaChart data={data.attendanceChart} margin={{ left: 0, right: 16, top: 8 }}>
              <defs>
                <linearGradient id="attendanceFill" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.45} />
                  <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0.04} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
              <XAxis dataKey="label" tickLine={false} />
              <YAxis tickLine={false} width={34} />
              <Tooltip />
              <Area dataKey="visits" fill="url(#attendanceFill)" name="Visits" stroke="#8b5cf6" strokeWidth={3} type="monotone" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>
        <UpcomingSessions sessions={data.upcomingSessions} />
        <NotificationList notifications={data.notifications} />
      </div>

      <ActivityFeed activities={data.recentActivities} />
    </div>
  );
};

export default Dashboard;
