import { useContext, useEffect, useMemo, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { Apple, Bell, ClipboardCheck, CreditCard, Dumbbell, LayoutDashboard, LogOut, Menu, Search, ShieldCheck, TrendingUp, UsersRound } from 'lucide-react';
import Button from '../components/common/Button';
import Brand from '../components/common/Brand';
import Avatar from '../components/common/Avatar';
import Modal from '../components/common/Modal';
import NotificationList from '../components/dashboard/NotificationList';
import ThemeToggle from '../components/common/ThemeToggle';
import { AuthContext } from '../context/auth-context';
import { canManage } from '../utils/tenant-session';
import { cx } from '../utils/formatters';
import { formatApiError, notificationService } from '../services/api';

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
  { match: '/owner', title: 'Owner Dashboard' },
  { match: '/gym/settings', title: 'Gym Settings' },
  { match: '/gym/invitations', title: 'Invitations' },
  { match: '/membership', title: 'Membership Management' },
  { match: '/workouts', title: 'Workout Management' },
  { match: '/nutrition', title: 'Nutrition Management' },
  { match: '/progress', title: 'Progress Tracking' },
  { match: '/trainers', title: 'Trainer Module' },
  { match: '/attendance', title: 'Attendance System' },
  { match: '/admin', title: 'Admin Dashboard' },
  { match: '/dashboard', title: 'Dashboard Overview' },
];

const SidebarLink = ({ item, onClick }) => {
  const { pathname } = useLocation();
  const active = pathname === item.path || (!['admin', 'gym', 'owner', 'dashboard'].includes(pathname.split('/')[1]) && pathname.split('/')[1] === item.path.split('/')[1]);
  return <NavLink to={item.path} onClick={onClick} aria-current={active ? 'page' : undefined} className={cx('flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-semibold transition', active ? 'bg-brand-soft text-brand' : 'text-muted hover:bg-surface-alt hover:text-ink')}><item.icon aria-hidden="true" className="h-[18px] w-[18px] shrink-0" /><span>{item.label}</span></NavLink>;
};
const SidebarBody = ({ items, user, logout, onNavigate }) => <>
  <Brand />
  <p className="mb-3 mt-10 px-3 text-[10px] font-bold uppercase tracking-[.2em] text-muted">Workspace</p>
  <nav aria-label="Primary navigation" className="flex flex-1 flex-col gap-1.5">{items.map(item => <SidebarLink item={item} key={item.path} onClick={onNavigate} />)}</nav>
  <div className="mt-8 border-t border-line pt-5">
    <div className="flex items-center gap-3"><Avatar name={user?.fullName} className="h-10 w-10 [&_span]:text-sm" /><div className="min-w-0"><p className="truncate text-sm font-semibold text-ink">{user?.fullName}</p><p className="mt-0.5 text-[10px] font-semibold uppercase tracking-wider text-muted">{user?.role}</p></div></div>
    <Button className="mt-4 w-full" icon={LogOut} onClick={logout} variant="outline">Sign Out</Button>
  </div>
</>;

const MainLayout = () => {
  const { logout, user } = useContext(AuthContext);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [notificationError, setNotificationError] = useState('');
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [query, setQuery] = useState('');
  const location = useLocation();

  const pageTitle = useMemo(() => {
    return routeTitles.find((item) => location.pathname.startsWith(item.match))?.title || 'FitCore';
  }, [location.pathname]);

  const personal = user?.role === 'OWNER' ? navigation.map(item => item.path === '/dashboard' ? { ...item, path: '/owner', label: 'Owner Dashboard' } : item) : navigation;
  const visibleNavigation = canManage(user) ? [...personal, ...adminNavigation, { path: '/admin/users', label: 'Members', icon: UsersRound }, { path: '/admin/trainers', label: 'Manage trainers', icon: UsersRound }, { path: '/gym/invitations', label: 'Invitations', icon: UsersRound }, ...(user.role === 'OWNER' ? [{ path: '/gym/settings', label: 'Gym Settings', icon: ShieldCheck }] : [])] : personal;
  const firstName = user?.fullName?.split(' ')[0] || 'Member';
  const searchItems = visibleNavigation;
  const searchResults = query.trim()
    ? searchItems.filter((item) => item.label.toLowerCase().includes(query.trim().toLowerCase()))
    : [];

  const openNotifications = async () => {
    setNotificationsOpen(true);
    setNotificationsLoading(true);
    setNotificationError('');
    try {
      setNotifications(await notificationService.getNotifications(user.id));
    } catch (error) {
      setNotificationError(formatApiError(error, 'Unable to load notifications.'));
    } finally {
      setNotificationsLoading(false);
    }
  };

  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 1024px)');
    const closeOnDesktop = () => { if (desktop.matches) setSidebarOpen(false); };
    desktop.addEventListener('change', closeOnDesktop);
    return () => desktop.removeEventListener('change', closeOnDesktop);
  }, []);

  return (
    <div className="min-h-dvh text-ink">
      <a className="skip-link" href="#main-content">Skip to main content</a>
      <aside aria-label="Workspace sidebar" className="fixed inset-y-0 left-0 z-40 hidden w-64 flex-col overflow-y-auto border-r border-line bg-surface px-5 py-7 lg:flex">
        <SidebarBody items={visibleNavigation} user={user} logout={logout} />
      </aside>
      <div className="min-w-0 lg:pl-64">
        <header className="sticky top-0 z-30 border-b border-line bg-surface/95 px-4 py-4 backdrop-blur-lg sm:px-6 lg:px-8">
          <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <Button aria-label="Open navigation" aria-haspopup="dialog" aria-expanded={sidebarOpen} icon={Menu} onClick={() => setSidebarOpen(true)} size="icon" variant="outline" className="lg:hidden" />
              <div className="min-w-0"><p className="text-xs font-medium text-muted">{user.gymName || 'FitCore'} · Hello, {firstName}</p><p className="truncate text-sm font-semibold text-ink sm:text-base">{pageTitle}</p></div>
            </div>
            <div role="search" className="relative hidden w-60 shrink-0 lg:block" onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setQuery(''); }}>
              <Search aria-hidden="true" className="pointer-events-none absolute left-3.5 top-3.5 h-4 w-4 text-muted" />
              <input type="search" aria-label="Search pages" aria-controls={query.trim() ? 'page-search-results' : undefined} placeholder="Find a page…" className="h-11 w-full rounded-xl border border-control-line bg-canvas pl-10 pr-3 text-sm text-ink" value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === 'Escape') setQuery(''); if (event.key === 'ArrowDown') { event.preventDefault(); document.querySelector('#page-search-results a')?.focus(); } }} />
              {query.trim() ? <div id="page-search-results" className="absolute inset-x-0 top-full mt-2 rounded-xl border border-line bg-surface p-2 shadow-panel" onKeyDown={(event) => { if (event.key === 'Escape') { setQuery(''); event.currentTarget.parentElement.querySelector('input').focus(); } }}>
                {searchResults.length ? searchResults.map(item => <Link className="block rounded-lg p-3 text-sm hover:bg-surface-alt" key={item.path} to={item.path} onClick={() => setQuery('')}>{item.label}</Link>) : <p role="status" className="p-2 text-sm text-muted">No matching pages.</p>}
              </div> : null}
            </div>
            <div className="flex shrink-0 items-center gap-2"><Button aria-label="Notifications" icon={Bell} isLoading={notificationsLoading} onClick={openNotifications} size="icon" variant="outline" /><ThemeToggle /></div>
          </div>
        </header>
        <main id="main-content" tabIndex={-1} className="mx-auto flex min-h-[calc(100dvh-80px)] w-full max-w-[1440px] flex-col px-4 py-7 outline-none sm:px-6 lg:px-8 lg:py-9">
          <div className="animate-fade-up min-w-0 flex-1"><Outlet /></div>
          <footer className="mt-12 flex flex-col gap-2 border-t border-line py-6 text-xs text-muted sm:flex-row sm:items-center sm:justify-between"><p className="font-semibold">FitCore · Your fitness workspace</p><p>Train with purpose. Track your progress.</p></footer>
        </main>
      </div>
      <Modal drawer isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} title="Main navigation"><SidebarBody items={visibleNavigation} user={user} logout={logout} onNavigate={() => setSidebarOpen(false)} /></Modal>
      <Modal isOpen={notificationsOpen} onClose={() => setNotificationsOpen(false)} title="Notifications">
        {notificationsLoading ? <p role="status">Loading notifications...</p> : notificationError ? <p role="alert">{notificationError}</p> : <NotificationList notifications={notifications} />}
      </Modal>
    </div>
  );
};
export default MainLayout;
