import { useCallback, useContext, useEffect, useState } from 'react';
import { CreditCard, History, RotateCcw, ShieldCheck, Sparkles } from 'lucide-react';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import Toast from '../components/common/Toast';
import DataTable from '../components/tables/DataTable';
import LoadingSpinner from '../components/common/LoadingSpinner';
import StateBlock from '../components/common/StateBlock';
import { AuthContext } from '../context/auth-context';
import { formatApiError, membershipService } from '../services/api';
import { formatCurrency, formatDate } from '../utils/formatters';

const tabs = [
  { path: '/membership/plans', label: 'Plans', icon: Sparkles },
  { path: '/membership/active', label: 'Active', icon: ShieldCheck },
  { path: '/membership/history', label: 'History', icon: History },
];

const historyColumns = [
  { key: 'planName', header: 'Plan Name' },
  { key: 'purchaseDate', header: 'Purchase Date', render: (row) => formatDate(row.purchaseDate) },
  { key: 'expiryDate', header: 'Expiry Date', render: (row) => formatDate(row.expiryDate) },
  { key: 'status', header: 'Status', badge: true },
];

const Membership = ({ section = 'plans' }) => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');

  const loadMemberships = useCallback(async () => {
    if (!user?.id) return;
    setError('');
    try {
      const [plans, active, history] = await Promise.all([
        membershipService.getPlans(),
        membershipService.getActive(user.id),
        membershipService.getHistory(user.id),
      ]);
      setData({ plans, active, history });
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load memberships.'));
    }
  }, [user?.id]);

  useEffect(() => {
    loadMemberships();
  }, [loadMemberships]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const submitPlanAction = async (plan, action) => {
    if (!user?.id) return;
    setError('');
    try {
      await membershipService.subscribe(user.id, plan.id);
      setToast(`${plan.name} plan ${action}`);
      await loadMemberships();
    } catch (requestError) {
      setError(formatApiError(requestError, `Unable to ${action} ${plan.name} plan.`));
    }
  };

  if (error) return <StateBlock message={error} onAction={loadMemberships} />;
  if (!data) return <LoadingSpinner label="Loading memberships" />;

  return (
    <div className="space-y-6">
      <PageHeader
        description="Manage membership plans, current plan details, renewals, upgrades, and purchase history."
        icon={CreditCard}
        kicker="Membership"
        title="Membership Management"
      />
      <ModuleTabs tabs={tabs} />

      {section === 'plans' ? (
        <div className="grid gap-4 lg:grid-cols-3">
          {data.plans.map((plan) => (
            <Card className="relative flex min-h-96 flex-col" key={plan.id}>
              {plan.recommended ? <Badge className="absolute right-4 top-4" tone="emerald">Recommended</Badge> : null}
              <div className="mb-6">
                <h2 className="text-xl font-bold text-slate-950 dark:text-white">{plan.name} Plan</h2>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">{plan.duration} access package</p>
                <p className="mt-5 text-4xl font-bold text-slate-950 dark:text-white">
                  {formatCurrency(plan.price)}
                </p>
              </div>
              <ul className="space-y-3 text-sm text-slate-600 dark:text-slate-300">
                {plan.features.map((feature) => (
                  <li className="flex gap-2" key={feature}>
                    <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>
              <div className="mt-auto grid gap-2 pt-6">
                <Button icon={CreditCard} onClick={() => submitPlanAction(plan, 'purchased')}>
                  Purchase Plan
                </Button>
                <Button icon={Sparkles} onClick={() => submitPlanAction(plan, 'upgraded')} variant="outline">
                  Upgrade Plan
                </Button>
                <Button icon={RotateCcw} onClick={() => submitPlanAction(plan, 'renewed')} variant="ghost">
                  Renew Plan
                </Button>
              </div>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'active' && data.active ? (
        <div className="grid gap-4 lg:grid-cols-[1fr_0.8fr]">
          <Card className="min-h-72">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <Badge tone="emerald">{data.active.status}</Badge>
                <h2 className="mt-4 text-3xl font-bold text-slate-950 dark:text-white">{data.active.plan} Plan</h2>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Current membership plan and lifecycle details.</p>
              </div>
              <Button icon={RotateCcw} onClick={() => setToast('Select a plan to renew from the plans tab')}>
                Renew Plan
              </Button>
            </div>

            <div className="mt-8 grid gap-4 sm:grid-cols-3">
              <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Start Date</p>
                <p className="mt-1 font-bold text-slate-950 dark:text-white">{formatDate(data.active.startDate)}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Expiry Date</p>
                <p className="mt-1 font-bold text-slate-950 dark:text-white">{formatDate(data.active.expiryDate)}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Remaining Days</p>
                <p className="mt-1 font-bold text-slate-950 dark:text-white">{data.active.remainingDays}</p>
              </div>
            </div>
          </Card>

          <Card>
            <h2 className="text-lg font-bold text-slate-950 dark:text-white">Included Benefits</h2>
            <div className="mt-6 space-y-3">
              {(data.active.features || []).map((item) => (
                <div className="flex gap-2 rounded-lg bg-slate-50 p-3 text-sm font-semibold text-slate-700 dark:bg-slate-800/70 dark:text-slate-200" key={item}>
                  <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-500" />
                  <span>{item}</span>
                </div>
              ))}
            </div>
          </Card>
        </div>
      ) : null}

      {section === 'active' && !data.active ? (
        <StateBlock title="No active membership" message="Choose a membership plan to create an active subscription." />
      ) : null}

      {section === 'history' ? <DataTable columns={historyColumns} data={data.history} /> : null}

      <Toast message={toast} />
    </div>
  );
};

export default Membership;
