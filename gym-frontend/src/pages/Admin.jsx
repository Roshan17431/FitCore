import { AuthContext } from '../context/auth-context';
import { canEditAccount } from '../utils/tenant-session';
import { useCallback, useContext, useEffect, useMemo, useState } from 'react';
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
import {
  ClipboardCheck,
  CreditCard,
  FileText,
  LayoutDashboard,
  Plus,
  ShieldCheck,
  Trash2,
  UserCog,
  UsersRound,
} from 'lucide-react';
import ChartCard from '../components/charts/ChartCard';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import Modal from '../components/common/Modal';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import SearchBar from '../components/common/SearchBar';
import StateBlock from '../components/common/StateBlock';
import StatCard from '../components/common/StatCard';
import Toast from '../components/common/Toast';
import FormField from '../components/forms/FormField';
import DataTable from '../components/tables/DataTable';
import { adminService, formatApiError } from '../services/api';
import { formatCurrency, formatDate } from '../utils/formatters';

const tabs = [
  { path: '/admin/dashboard', label: 'Overview', icon: LayoutDashboard },
  { path: '/admin/users', label: 'Users', icon: UserCog },
  { path: '/admin/trainers', label: 'Trainers', icon: UsersRound },
  { path: '/admin/memberships', label: 'Memberships', icon: CreditCard },
  { path: '/admin/attendance', label: 'Attendance', icon: ClipboardCheck },
];

const statIcons = [UsersRound, ShieldCheck, UsersRound, CreditCard];

const Admin = ({ section = 'dashboard' }) => {
  const { user: actor } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [toast, setToast] = useState('');
  const [userRows, setUserRows] = useState([]);
  const [trainerRows, setTrainerRows] = useState([]);
  const [planRows, setPlanRows] = useState([]);
  const [trainerForm, setTrainerForm] = useState({ name: '', email: '', password: '', specialization: '', experience: '' });
  const [planForm, setPlanForm] = useState({ name: '', price: '', durationMonths: '', features: '' });
  const [submitting, setSubmitting] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [editingTrainer, setEditingTrainer] = useState(null);
  const [editingPlan, setEditingPlan] = useState(null);
  const [actionError, setActionError] = useState('');

  const runAction = async (action, message) => {
    if (submitting) return;
    setSubmitting(true);
    setActionError('');
    try {
      await action();
      setToast(message);
      await loadAdmin();
      return true;
    } catch (requestError) {
      setActionError(formatApiError(requestError, 'Unable to save changes.'));
      return false;
    } finally {
      setSubmitting(false);
    }
  };

  const loadAdmin = useCallback(async () => {
    setError('');
    try {
      const adminData = await adminService.getDashboard();
      setData(adminData);
      setUserRows(adminData.users);
      setTrainerRows(adminData.trainers);
      setPlanRows(adminData.membershipPlans);
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load admin dashboard.'));
    }
  }, []);

  useEffect(() => {
    loadAdmin();
  }, [loadAdmin]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const filteredUsers = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) return userRows;
    return userRows.filter((user) =>
      [user.fullName, user.email, user.role, user.status].some((value) =>
        String(value ?? '').toLowerCase().includes(normalizedQuery),
      ),
    );
  }, [query, userRows]);

  const addTrainer = async (event) => {
    event.preventDefault();
    if (submitting) return;
    if (!trainerForm.name.trim() || !trainerForm.email.trim() || (!editingTrainer && !trainerForm.password) || !trainerForm.specialization.trim()) return;
    setSubmitting(true);
    setActionError('');
    try {
      const payload = {
        fullName: trainerForm.name.trim(),
        email: trainerForm.email.trim(),
        password: trainerForm.password || undefined,
        specialization: trainerForm.specialization,
        experienceYears: Number(trainerForm.experience) || 0,
        bio: editingTrainer?.bio,
      };
      if (editingTrainer) await adminService.updateTrainer(editingTrainer.id, payload);
      else await adminService.createTrainer(payload);
      setTrainerForm({ name: '', email: '', password: '', specialization: '', experience: '' });
      setToast(editingTrainer ? 'Trainer updated' : 'Trainer added');
      setEditingTrainer(null);
      await loadAdmin();
    } catch (requestError) {
      setActionError(formatApiError(requestError, 'Unable to save trainer.'));
    } finally {
      setSubmitting(false);
    }
  };

  const addPlan = async (event) => {
    event.preventDefault();
    if (submitting) return;
    if (!planForm.name.trim() || Number(planForm.price) <= 0) return;
    setSubmitting(true);
    setActionError('');
    try {
      const payload = {
        name: planForm.name,
        price: Number(planForm.price),
        durationMonths: Number(planForm.durationMonths) || 1,
        features: planForm.features.split(',').map((feature) => feature.trim()).filter(Boolean),
        recommended: editingPlan?.recommended || false,
      };
      if (editingPlan) await adminService.updateMembershipPlan(editingPlan.id, payload);
      else await adminService.createMembershipPlan(payload);
      setPlanForm({ name: '', price: '', durationMonths: '', features: '' });
      setToast(editingPlan ? 'Membership plan updated' : 'Membership plan created');
      setEditingPlan(null);
      await loadAdmin();
    } catch (requestError) {
      setActionError(formatApiError(requestError, 'Unable to save membership plan.'));
    } finally {
      setSubmitting(false);
    }
  };

  if (error) return <StateBlock message={error} onAction={loadAdmin} />;
  if (!data) return <LoadingSpinner label="Loading admin" />;

  const userColumns = [
    { key: 'fullName', header: 'Name' },
    { key: 'email', header: 'Email' },
    { key: 'role', header: 'Role' },
    { key: 'membership', header: 'Membership' },
    { key: 'status', header: 'Status', badge: true },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div className="flex gap-2">
          <Button disabled={!canEditAccount(actor, row)} onClick={() => { setActionError(''); setEditingUser({ ...row }); }} size="sm" variant="outline">
            Edit
          </Button>
          <Button
            aria-label={`Disable ${row.fullName}`}
            disabled={submitting || !row.enabled || !canEditAccount(actor, row)}
            icon={Trash2}
            onClick={() => runAction(() => adminService.deleteUser(row.id), `${row.fullName} disabled`)}
            size="sm"
            variant="danger"
          />
        </div>
      ),
    },
  ];

  const trainerColumns = [
    { key: 'name', header: 'Name' },
    { key: 'specialization', header: 'Specialization' },
    { key: 'experience', header: 'Experience' },
    { key: 'enabled', header: 'Status', render: (row) => row.enabled ? 'Active' : 'Inactive' },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div className="flex gap-2">
          <Button disabled={!canEditAccount(actor, row.user)} onClick={() => {
            setEditingTrainer(row);
            setTrainerForm({ name: row.name, email: row.email, password: '', specialization: row.specialization, experience: row.experienceYears });
            setActionError('');
          }} size="sm" variant="outline">
            Update
          </Button>
          <Button
            aria-label={`Disable ${row.name}`}
            disabled={submitting || !row.enabled || !canEditAccount(actor, row.user)}
            icon={Trash2}
            onClick={() => runAction(() => adminService.deleteTrainer(row.id), `${row.name} disabled`)}
            size="sm"
            variant="danger"
          />
        </div>
      ),
    },
  ];

  const planColumns = [
    { key: 'name', header: 'Plan' },
    { key: 'price', header: 'Price', render: (row) => formatCurrency(row.price) },
    { key: 'duration', header: 'Duration' },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div className="flex gap-2">
          <Button onClick={() => {
            setEditingPlan(row);
            setPlanForm({ name: row.name, price: row.price, durationMonths: row.durationMonths, features: (row.features || []).join(', ') });
            setActionError('');
          }} size="sm" variant="outline">
            Edit
          </Button>
          <Button
            aria-label={`Delete ${row.name} plan`}
            disabled={submitting}
            icon={Trash2}
            onClick={() => runAction(() => adminService.deleteMembershipPlan(row.id), `${row.name} plan deleted`)}
            size="sm"
            variant="danger"
          />
        </div>
      ),
    },
  ];

  const attendanceColumns = [
    { key: 'date', header: 'Date', render: (row) => formatDate(row.date) },
    { key: 'checkInTime', header: 'Check-In' },
    { key: 'checkOutTime', header: 'Check-Out' },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        actions={section === 'users' ? <SearchBar onChange={setQuery} placeholder="Search users" value={query} /> : null}
        description="Monitor gym operations, manage users and trainers, maintain plans, and generate attendance reports."
        icon={ShieldCheck}
        kicker="Admin"
        title="Admin Dashboard"
      />
      <ModuleTabs tabs={tabs} />
      {actionError ? <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-300">{actionError}</p> : null}

      {section === 'dashboard' ? (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {data.adminCards.map((card, index) => (
              <StatCard
                detail={card.detail}
                icon={statIcons[index]}
                key={card.title}
                title={card.title}
                tone={card.tone}
                value={card.value}
              />
            ))}
          </div>
          <div className="grid gap-4 xl:grid-cols-3">
            <ChartCard title="Membership Growth">
              <ResponsiveContainer height="100%" width="100%">
                <LineChart data={data.membershipGrowth}>
                  <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                  <XAxis dataKey="label" tickLine={false} />
                  <YAxis tickLine={false} width={44} />
                  <Tooltip />
                  <Line isAnimationActive={false} dataKey="members" stroke="var(--chart-1)" strokeWidth={3} type="monotone" />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>
            <ChartCard title="Revenue Analytics">
              <ResponsiveContainer height="100%" width="100%">
                <BarChart data={data.revenueAnalytics}>
                  <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                  <XAxis dataKey="label" tickLine={false} />
                  <YAxis tickLine={false} width={44} />
                  <Tooltip />
                  <Bar isAnimationActive={false} dataKey="revenue" fill="var(--chart-3)" radius={[8, 8, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
            <ChartCard title="Attendance Analytics">
              <ResponsiveContainer height="100%" width="100%">
                <BarChart data={data.adminAttendanceAnalytics}>
                  <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                  <XAxis dataKey="label" tickLine={false} />
                  <YAxis tickLine={false} width={44} />
                  <Tooltip />
                  <Bar isAnimationActive={false} dataKey="visits" fill="var(--chart-4)" radius={[8, 8, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>
        </div>
      ) : null}

      {section === 'users' ? <DataTable columns={userColumns} data={filteredUsers} /> : null}

      {section === 'trainers' ? (
        <div className="grid gap-4 xl:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <h2 className="text-lg font-bold text-ink">{editingTrainer ? 'Edit Trainer' : 'Add Trainer'}</h2>
            <form className="mt-4 space-y-4" onSubmit={addTrainer}>
              <FormField
                label="Trainer name"
                required
                name="name"
                onChange={(event) => setTrainerForm((current) => ({ ...current, name: event.target.value }))}
                value={trainerForm.name}
              />
              <FormField
                label="Email"
                required
                name="email"
                onChange={(event) => setTrainerForm((current) => ({ ...current, email: event.target.value }))}
                type="email"
                value={trainerForm.email}
              />
              <FormField
                label="Temporary password"
                required={!editingTrainer}
                minLength={8}
                name="password"
                onChange={(event) => setTrainerForm((current) => ({ ...current, password: event.target.value }))}
                type="password"
                value={trainerForm.password}
              />
              <FormField
                label="Specialization"
                required
                name="specialization"
                onChange={(event) => setTrainerForm((current) => ({ ...current, specialization: event.target.value }))}
                value={trainerForm.specialization}
              />
              <FormField
                label="Experience years"
                min="0"
                name="experience"
                onChange={(event) => setTrainerForm((current) => ({ ...current, experience: event.target.value }))}
                type="number"
                value={trainerForm.experience}
              />
              <Button icon={Plus} isLoading={submitting} type="submit">{editingTrainer ? 'Save Trainer' : 'Add Trainer'}</Button>
              {editingTrainer ? <Button className="ml-2" variant="outline" onClick={() => { setEditingTrainer(null); setTrainerForm({ name: '', email: '', password: '', specialization: '', experience: '' }); }}>Cancel</Button> : null}
            </form>
          </Card>
          <DataTable columns={trainerColumns} data={trainerRows} />
        </div>
      ) : null}

      {section === 'memberships' ? (
        <div className="grid gap-4 xl:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <h2 className="text-lg font-bold text-ink">{editingPlan ? 'Edit Plan' : 'Create Plan'}</h2>
            <form className="mt-4 space-y-4" onSubmit={addPlan}>
              <FormField
                label="Plan name"
                required
                name="name"
                onChange={(event) => setPlanForm((current) => ({ ...current, name: event.target.value }))}
                value={planForm.name}
              />
              <FormField
                label="Price"
                required
                min="0.01"
                step="0.01"
                name="price"
                onChange={(event) => setPlanForm((current) => ({ ...current, price: event.target.value }))}
                type="number"
                value={planForm.price}
              />
              <FormField
                label="Duration months"
                required
                min="1"
                name="durationMonths"
                onChange={(event) => setPlanForm((current) => ({ ...current, durationMonths: event.target.value }))}
                type="number"
                value={planForm.durationMonths}
              />
              <FormField
                label="Features"
                name="features"
                onChange={(event) => setPlanForm((current) => ({ ...current, features: event.target.value }))}
                placeholder="Gym access, Trainer consultation"
                value={planForm.features}
              />
              <Button icon={Plus} isLoading={submitting} type="submit">{editingPlan ? 'Save Plan' : 'Create Plan'}</Button>
              {editingPlan ? <Button className="ml-2" variant="outline" onClick={() => { setEditingPlan(null); setPlanForm({ name: '', price: '', durationMonths: '', features: '' }); }}>Cancel</Button> : null}
            </form>
          </Card>
          <DataTable columns={planColumns} data={planRows} />
        </div>
      ) : null}

      {section === 'attendance' ? (
        <div className="space-y-4">
          <Card className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-bold text-ink">Attendance Management</h2>
              <p className="mt-1 text-sm text-muted">View attendance records and generate operations reports.</p>
            </div>
            <Button icon={FileText} onClick={() => {
              const rows = [['Member ID', 'Date', 'Check-In', 'Check-Out'], ...data.attendanceHistory.map((row) => [row.raw.userId, row.date, row.checkInTime, row.checkOutTime])];
              const csv = rows.map((row) => row.map((cell) => `"${String(cell ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n');
              const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
              const link = document.createElement('a');
              link.href = url;
              link.download = 'fitcore-attendance.csv';
              link.click();
              window.setTimeout(() => URL.revokeObjectURL(url), 1000);
              setToast('Attendance report downloaded');
            }}>
              Generate Reports
            </Button>
          </Card>
          <DataTable columns={attendanceColumns} data={data.attendanceHistory} />
        </div>
      ) : null}

      <Modal isOpen={Boolean(editingUser)} onClose={() => { if (!submitting) setEditingUser(null); }} title="Edit User">
        {editingUser ? <form className="space-y-4" onSubmit={async (event) => {
          event.preventDefault();
          const saved = await runAction(() => adminService.updateUser(editingUser.id, {
            fullName: editingUser.fullName.trim(), email: editingUser.email.trim(), role: editingUser.role,
            fitnessGoal: editingUser.fitnessGoal, enabled: editingUser.enabled,
          }), 'User updated');
          if (saved) setEditingUser(null);
        }}>
          <FormField required label="Full name" value={editingUser.fullName} onChange={(event) => setEditingUser((current) => ({ ...current, fullName: event.target.value }))} />
          <FormField required type="email" label="Email" value={editingUser.email} onChange={(event) => setEditingUser((current) => ({ ...current, email: event.target.value }))} />
          <FormField label="Role" options={actor.role === 'OWNER' ? ['MEMBER', 'TRAINER', 'ADMIN'] : ['MEMBER', 'TRAINER']} value={editingUser.role} onChange={(event) => setEditingUser((current) => ({ ...current, role: event.target.value }))} />
          <FormField label="Fitness goal" value={editingUser.fitnessGoal || ''} onChange={(event) => setEditingUser((current) => ({ ...current, fitnessGoal: event.target.value }))} />
          <label className="flex gap-2 text-sm"><input type="checkbox" checked={editingUser.enabled} onChange={(event) => setEditingUser((current) => ({ ...current, enabled: event.target.checked }))} />Account enabled</label>
          {actionError ? <p role="alert" className="text-sm text-rose-700 dark:text-rose-300">{actionError}</p> : null}
          <Button type="submit" isLoading={submitting}>Save User</Button>
        </form> : null}
      </Modal>
      <Toast message={toast} />
    </div>
  );
};

export default Admin;
