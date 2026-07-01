import { useContext, useMemo, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  Apple,
  Bell,
  ClipboardCheck,
  CreditCard,
  Dumbbell,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  ShieldCheck,
  TrendingUp,
  UserCircle,
  UsersRound,
  X,
} from 'lucide-react';
import Button from '../components/common/Button';
import ThemeToggle from '../components/common/ThemeToggle';
import { AuthContext } from '../context/auth-context';
import { cx } from '../utils/formatters';

const navigation = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/membership/plans', label: 'Membership', icon: CreditCard },
  { path: '/workouts/library', label: 'Workouts', icon: Dumbbell },
  { path: '/nutrition/dashboard', label: 'Nutrition', icon: Apple },
  { path: '/progress/weight', label: 'Progress', icon: TrendingUp },
  { path: '/trainers/list', label: 'Trainers', icon: UsersRound },
  { path: '/attendance/check-in', label: 'Attendance', icon: ClipboardCheck },
];

const adminNavigation = [{ path: '/admin/dashboard', label: 'Admin', icon: ShieldCheck }];

const routeTitles = [
  { match: '/membership', title: 'Membership Management' },
  { match: '/workouts', title: 'Workout Management' },
  { match: '/nutrition', title: 'Nutrition Management' },
  { match: '/progress', title: 'Progress Tracking' },
  { match: '/trainers', title: 'Trainer Module' },
  { match: '/attendance', title: 'Attendance System' },
  { match: '/admin', title: 'Admin Dashboard' },
  { match: '/dashboard', title: 'Dashboard Overview' },
];

const SidebarLink = ({ item, onClick }) => (
  <NavLink
    className={({ isActive }) =>
      cx(
        'flex h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold transition',
        isActive
          ? 'bg-emerald-500 text-white shadow-sm shadow-emerald-500/25'
          : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white',
      )
    }
    onClick={onClick}
    to={item.path}
  >
    <item.icon className="h-5 w-5 shrink-0" />
    <span>{item.label}</span>
  </NavLink>
);

const MainLayout = () => {
  const { logout, user } = useContext(AuthContext);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  const pageTitle = useMemo(() => {
    return routeTitles.find((item) => location.pathname.startsWith(item.match))?.title || 'FitCore';
  }, [location.pathname]);

  const visibleNavigation = user?.role === 'ADMIN' ? [...navigation, ...adminNavigation] : navigation;
  const firstName = user?.fullName?.split(' ')[0] || 'Member';

  return (
    <div className="min-h-screen text-slate-950 dark:text-white">
      <div
        className={cx(
          'fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm transition lg:hidden',
          sidebarOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={() => setSidebarOpen(false)}
      />

      <aside
        className={cx(
          'fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-slate-200 bg-white/95 p-4 shadow-xl transition-transform dark:border-slate-800 dark:bg-slate-950/95 lg:translate-x-0 lg:shadow-none',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center justify-between gap-3">
          <Link className="flex items-center gap-3" to="/dashboard">
            <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-emerald-500 text-white">
              <Dumbbell className="h-6 w-6" />
            </span>
            <span>
              <span className="block text-lg font-bold">FitCore</span>
              <span className="block text-xs font-semibold uppercase text-slate-500 dark:text-slate-400">
                Gym Management
              </span>
            </span>
          </Link>
          <Button aria-label="Close navigation" icon={X} onClick={() => setSidebarOpen(false)} size="icon" variant="ghost" className="lg:hidden" />
        </div>

        <nav className="mt-8 flex flex-1 flex-col gap-1">
          {visibleNavigation.map((item) => (
            <SidebarLink item={item} key={item.path} onClick={() => setSidebarOpen(false)} />
          ))}
        </nav>

        <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-300">
              <UserCircle className="h-6 w-6" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold text-slate-900 dark:text-white">{user?.fullName}</p>
              <p className="text-xs font-semibold uppercase text-slate-500 dark:text-slate-400">{user?.role}</p>
            </div>
          </div>
          <Button className="mt-3 w-full" icon={LogOut} onClick={logout} variant="outline">
            Sign Out
          </Button>
        </div>
      </aside>

      <div className="lg:pl-72">
        <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/80 px-4 py-3 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-950/80 sm:px-6">
          <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <Button aria-label="Open navigation" icon={Menu} onClick={() => setSidebarOpen(true)} size="icon" variant="outline" className="lg:hidden" />
              <div className="min-w-0">
                <p className="text-xs font-bold uppercase text-slate-500 dark:text-slate-400">Hello, {firstName}</p>
                <h1 className="truncate text-lg font-bold text-slate-950 dark:text-white sm:text-xl">{pageTitle}</h1>
              </div>
            </div>

            <div className="hidden min-w-64 items-center rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400 md:flex">
              <Search className="mr-2 h-4 w-4" />
              <span>Search members, workouts, trainers</span>
            </div>

            <div className="flex items-center gap-2">
              <Button aria-label="Notifications" icon={Bell} size="icon" variant="outline" />
              <ThemeToggle />
            </div>
          </div>
        </header>

        <main className="mx-auto flex min-h-[calc(100vh-72px)] w-full max-w-7xl flex-col px-4 py-6 sm:px-6 lg:px-8">
          <div className="animate-fade-up flex-1">
            <Outlet />
          </div>

          <footer className="mt-10 flex flex-col gap-2 border-t border-slate-200 py-6 text-sm text-slate-500 dark:border-slate-800 dark:text-slate-400 sm:flex-row sm:items-center sm:justify-between">
            <p>FitCore Gym Management System</p>
            <div className="flex flex-wrap gap-4">
              <a href="mailto:support@fitcore.test">support@fitcore.test</a>
              <a href="tel:+15550192500">+1 555 019 2500</a>
              <span>@fitcore</span>
            </div>
          </footer>
        </main>
      </div>
    </div>
  );
};

export default MainLayout;

