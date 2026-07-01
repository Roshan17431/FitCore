import { useState, useEffect, useContext } from 'react';
import { AuthContext } from '../context/auth-context';
import { Activity, Flame, TrendingUp, Trophy } from 'lucide-react';
import './Dashboard.css';

const Dashboard = () => {
  const { user } = useContext(AuthContext);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // In a real app, we'd fetch actual dashboard stats from the backend.
    // For now, we simulate a loading state and show dummy data for visual wow factor.
    const fetchDashboardStats = async () => {
      try {
        // Example actual API call we might make later:
        // const response = await api.get(`/tracking/progress/${user.id}`);
        
        // Simulating network delay
        setTimeout(() => {
          setStats({
            workoutsCompleted: 12,
            activeStreak: 4,
            caloriesBurned: 8400,
            weightLost: 2.5
          });
          setLoading(false);
        }, 800);
      } catch (error) {
        console.error("Failed to load stats", error);
        setLoading(false);
      }
    };
    
    fetchDashboardStats();
  }, [user.id]);

  if (loading) {
    return <div className="d-flex justify-center align-center" style={{ height: '50vh' }}><div className="spinner"></div></div>;
  }

  const statCards = [
    { title: 'Workouts Completed', value: stats.workoutsCompleted, icon: Activity, color: 'var(--primary)' },
    { title: 'Active Streak', value: `${stats.activeStreak} days`, icon: Flame, color: 'var(--warning)' },
    { title: 'Calories Burned', value: `${stats.caloriesBurned} kcal`, icon: TrendingUp, color: 'var(--secondary)' },
    { title: 'Weight Target', value: `-${stats.weightLost} kg`, icon: Trophy, color: 'var(--success)' },
  ];

  return (
    <div className="dashboard-container animate-fade-in">
      <div className="stats-grid">
        {statCards.map((stat, index) => {
          const Icon = stat.icon;
          return (
            <div key={index} className="glass-card stat-card">
              <div className="stat-icon-wrapper" style={{ background: `${stat.color}15`, color: stat.color }}>
                <Icon size={24} />
              </div>
              <div className="stat-content">
                <p className="stat-title text-muted">{stat.title}</p>
                <h3 className="stat-value">{stat.value}</h3>
              </div>
            </div>
          );
        })}
      </div>

      <div className="dashboard-content-grid">
        <div className="glass-card main-widget">
          <div className="widget-header">
            <h3>Recent Activity</h3>
            <button className="btn btn-outline" style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>View All</button>
          </div>
          <div className="widget-body empty-state">
            <Activity size={48} color="var(--border-color)" />
            <p className="text-muted mt-2">No recent workouts logged.</p>
            <button className="btn btn-primary mt-4" style={{ padding: '0.5rem 1rem' }}>Log a Workout</button>
          </div>
        </div>
        
        <div className="glass-card side-widget">
          <div className="widget-header">
            <h3>Current Plan</h3>
          </div>
          <div className="widget-body plan-details">
            <div className="plan-badge">Free Tier</div>
            <p className="text-muted mt-2">Upgrade to unlock advanced analytics and personal trainer booking.</p>
            <button className="btn btn-outline mt-4 w-100">View Plans</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
