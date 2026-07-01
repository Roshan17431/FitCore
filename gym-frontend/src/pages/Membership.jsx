import { useEffect, useState } from 'react';
import { CreditCard, History, RotateCcw, ShieldCheck, Sparkles } from 'lucide-react';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import Toast from '../components/common/Toast';
import DataTable from '../components/tables/DataTable';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { membershipService } from '../services/mockApi';
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
  const [data, setData] = useState(null);
  const [toast, setToast] = useState('');

  useEffect(() => {
    Promise.all([
      membershipService.getPlans(),
      membershipService.getActive(),
      membershipService.getHistory(),
    ]).then(([plans, active, history]) => setData({ plans, active, history }));
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

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
                <Button icon={CreditCard} onClick={() => setToast(`${plan.name} plan purchase started`)}>
                  Purchase Plan
                </Button>
                <Button icon={Sparkles} onClick={() => setToast(`${plan.name} upgrade queued`)} variant="outline">
                  Upgrade Plan
                </Button>
                <Button icon={RotateCcw} onClick={() => setToast(`${plan.name} renewal queued`)} variant="ghost">
                  Renew Plan
                </Button>
              </div>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'active' ? (
        <div className="grid gap-4 lg:grid-cols-[1fr_0.8fr]">
          <Card className="min-h-72">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <Badge tone="emerald">{data.active.status}</Badge>
                <h2 className="mt-4 text-3xl font-bold text-slate-950 dark:text-white">{data.active.plan} Plan</h2>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Current membership plan and lifecycle details.</p>
              </div>
              <Button icon={RotateCcw} onClick={() => setToast('Renewal request submitted')}>
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
            <h2 className="text-lg font-bold text-slate-950 dark:text-white">Plan Usage</h2>
            <div className="mt-6 space-y-5">
              {['Gym floor access', 'Trainer consultation', 'Progress analytics'].map((item, index) => (
                <div key={item}>
                  <div className="mb-2 flex justify-between text-sm font-semibold text-slate-600 dark:text-slate-300">
                    <span>{item}</span>
                    <span>{[92, 68, 78][index]}%</span>
                  </div>
                  <div className="h-2 rounded-full bg-slate-100 dark:bg-slate-800">
                    <div className="h-2 rounded-full bg-emerald-500" style={{ width: `${[92, 68, 78][index]}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      ) : null}

      {section === 'history' ? <DataTable columns={historyColumns} data={data.history} /> : null}

      <Toast message={toast} />
    </div>
  );
};

export default Membership;
