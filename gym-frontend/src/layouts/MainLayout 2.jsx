import { useContext } from 'react';
import { Outlet, Navigate, Link, useLocation } from 'react-router-dom';
import { AuthContext } from '../context/auth-context';
import { LayoutDashboard, Dumbbell, Calendar, Apple, LogOut, User } from 'lucide-react';
import './MainLayout.css';

const MainLayout = () => {
  const { user, loading, logout } = useContext(AuthContext);
  const location = useLocation();

  if (loading) {
    return <div className="d-flex justify-center align-center" style={{ height: '100vh' }}><div className="spinner"></div></div>;
  }

  if (!user) {
    return <Navigate to="/login" />;
  }

  const navItems = [
    { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { path: '/workouts', label: 'Workouts', icon: Dumbbell },
    { path: '/schedule', label: 'Schedule', icon: Calendar },
    { path: '/nutrition', label: 'Nutrition', icon: Apple },
  ];

  return (
    <div className="layout-container">
      {/* Sidebar */}
      <aside className="glass sidebar">
        <div className="sidebar-header">
          <div className="brand-logo">
            <Dumbbell size={24} color="var(--primary)" />
          </div>
          <h2>FitCore</h2>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;
            
            return (
              <Link 
                key={item.path} 
                to={item.path} 
                className={`nav-item ${isActive ? 'active' : ''}`}
              >
                <Icon size={20} className="nav-icon" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          <div className="user-profile">
            <div className="user-avatar">
              <User size={20} />
            </div>
            <div className="user-info">
              <p className="user-name">{user.fullName}</p>
              <p className="user-role">{user.role}</p>
            </div>
          </div>
          <button onClick={logout} className="logout-btn">
            <LogOut size={18} />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        <header className="top-header glass">
          <div className="header-greeting">
            <h1>Hello, {user.fullName.split(' ')[0]} 👋</h1>
            <p className="text-muted">Welcome back to your fitness journey</p>
          </div>
        </header>
        
        <div className="page-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default MainLayout;
