import { useCallback, useEffect, useMemo, useState } from 'react';
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
        value.toLowerCase().includes(normalizedQuery),
      ),
    );
  }, [query, userRows]);

  const addTrainer = async (event) => {
    event.preventDefault();
    if (!trainerForm.name || !trainerForm.email || !trainerForm.password || !trainerForm.specialization) return;
    setSubmitting(true);
    setError('');
    try {
      await adminService.createTrainer({
        fullName: trainerForm.name,
        email: trainerForm.email,
        password: trainerForm.password,
        specialization: trainerForm.specialization,
        experienceYears: Number(trainerForm.experience) || 0,
      });
      setTrainerForm({ name: '', email: '', password: '', specialization: '', experience: '' });
      setToast('Trainer added');
      await loadAdmin();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to add trainer.'));
    } finally {
      setSubmitting(false);
    }
  };

  const addPlan = async (event) => {
    event.preventDefault();
    if (!planForm.name || !planForm.price) return;
    setSubmitting(true);
    setError('');
    try {
      await adminService.createMembershipPlan({
        name: planForm.name,
        price: Number(planForm.price),
        durationMonths: Number(planForm.durationMonths) || 1,
        features: planForm.features.split(',').map((feature) => feature.trim()).filter(Boolean),
        recommended: false,
      });
      setPlanForm({ name: '', price: '', durationMonths: '', features: '' });
      setToast('Membership plan created');
      await loadAdmin();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to create membership plan.'));
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
          <Button onClick={() => setToast(`${row.fullName} opened for editing`)} size="sm" variant="outline">
            Edit
          </Button>
          <Button
            icon={Trash2}
            onClick={async () => {
              await adminService.deleteUser(row.id);
              setUserRows((current) => current.filter((user) => user.id !== row.id));
              setToast(`${row.fullName} disabled`);
            }}
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
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div className="flex gap-2">
          <Button onClick={() => setToast(`${row.name} update queued`)} size="sm" variant="outline">
            Update
          </Button>
          <Button
            icon={Trash2}
            onClick={async () => {
              await adminService.deleteTrainer(row.id);
              setTrainerRows((current) => current.filter((trainer) => trainer.id !== row.id));
              setToast(`${row.name} disabled`);
            }}
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
          <Button onClick={() => setToast(`${row.name} plan opened for editing`)} size="sm" variant="outline">
            Edit
          </Button>
          <Button
            icon={Trash2}
            onClick={async () => {
              await adminService.deleteMembershipPlan(row.id);
              setPlanRows((current) => current.filter((plan) => plan.id !== row.id));
              setToast(`${row.name} plan deleted`);
            }}
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
                  <Line dataKey="members" stroke="#10b981" strokeWidth={3} type="monotone" />
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
                  <Bar dataKey="revenue" fill="#f59e0b" radius={[8, 8, 0, 0]} />
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
                  <Bar dataKey="visits" fill="#8b5cf6" radius={[8, 8, 0, 0]} />
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
            <h2 className="text-lg font-bold text-slate-950 dark:text-white">Add Trainer</h2>
            <form className="mt-4 space-y-4" onSubmit={addTrainer}>
              <FormField
                label="Trainer name"
                name="name"
                onChange={(event) => setTrainerForm((current) => ({ ...current, name: event.target.value }))}
                value={trainerForm.name}
              />
              <FormField
                label="Email"
                name="email"
                onChange={(event) => setTrainerForm((current) => ({ ...current, email: event.target.value }))}
                type="email"
                value={trainerForm.email}
              />
              <FormField
                label="Temporary password"
                name="password"
                onChange={(event) => setTrainerForm((current) => ({ ...current, password: event.target.value }))}
                type="password"
                value={trainerForm.password}
              />
              <FormField
                label="Specialization"
                name="specialization"
                onChange={(event) => setTrainerForm((current) => ({ ...current, specialization: event.target.value }))}
                value={trainerForm.specialization}
              />
              <FormField
                label="Experience years"
                name="experience"
                onChange={(event) => setTrainerForm((current) => ({ ...current, experience: event.target.value }))}
                type="number"
                value={trainerForm.experience}
              />
              <Button icon={Plus} isLoading={submitting} type="submit">Add Trainer</Button>
            </form>
          </Card>
          <DataTable columns={trainerColumns} data={trainerRows} />
        </div>
      ) : null}

      {section === 'memberships' ? (
        <div className="grid gap-4 xl:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <h2 className="text-lg font-bold text-slate-950 dark:text-white">Create Plan</h2>
            <form className="mt-4 space-y-4" onSubmit={addPlan}>
              <FormField
                label="Plan name"
                name="name"
                onChange={(event) => setPlanForm((current) => ({ ...current, name: event.target.value }))}
                value={planForm.name}
              />
              <FormField
                label="Price"
                name="price"
                onChange={(event) => setPlanForm((current) => ({ ...current, price: event.target.value }))}
                type="number"
                value={planForm.price}
              />
              <FormField
                label="Duration months"
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
              <Button icon={Plus} isLoading={submitting} type="submit">Create Plan</Button>
            </form>
          </Card>
          <DataTable columns={planColumns} data={planRows} />
        </div>
      ) : null}

      {section === 'attendance' ? (
        <div className="space-y-4">
          <Card className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-950 dark:text-white">Attendance Management</h2>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">View attendance records and generate operations reports.</p>
            </div>
            <Button icon={FileText} onClick={() => setToast('Attendance report generated')}>
              Generate Reports
            </Button>
          </Card>
          <DataTable columns={attendanceColumns} data={data.attendanceHistory} />
        </div>
      ) : null}

      <Toast message={toast} />
    </div>
  );
};

export default Admin;
