import { useCallback, useContext, useEffect, useRef, useState } from 'react';
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
import { formatCurrency } from '../utils/formatters';

const formatMembershipDate = (date) => new Intl.DateTimeFormat('en-US', {
  month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC',
}).format(new Date(`${date}T00:00:00Z`));

const tabs = [
  { path: '/membership/plans', label: 'Plans', icon: Sparkles },
  { path: '/membership/active', label: 'Active', icon: ShieldCheck },
  { path: '/membership/history', label: 'History', icon: History },
];

const historyColumns = [
  { key: 'planName', header: 'Plan Name' },
  { key: 'purchaseDate', header: 'Purchase Date (UTC)', render: (row) => formatMembershipDate(row.purchaseDate) },
  { key: 'expiryDate', header: 'Expiry Date (UTC)', render: (row) => formatMembershipDate(row.expiryDate) },
  { key: 'status', header: 'Status', badge: true },
];

const Membership = ({ section = 'plans' }) => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submissionLock = useRef(false);

  const loadMemberships = useCallback(async ({ preserveError = false } = {}) => {
    if (!user?.id) return;
    if (!preserveError) setError('');
    try {
      const [plans, active, history] = await Promise.all([
        membershipService.getPlans(),
        membershipService.getActive(user.id),
        membershipService.getHistory(user.id),
      ]);
      setData({ plans, active, history });
    } catch (requestError) {
      setError((current) => preserveError && current ? current : formatApiError(requestError, 'Unable to load memberships.'));
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

  const today = data?.active?.serverDate || new Date().toISOString().slice(0, 10);
  const upcomingMembership = data?.history.find((membership) => membership.status === 'Upcoming'
    || (membership.status === 'Active' && membership.startDate > today));
  const latestExpired = data?.history.filter((membership) => membership.status === 'Expired')
    .sort((left, right) => right.startDate.localeCompare(left.startDate) || right.id - left.id)[0];
  const renewalTarget = data?.active || (!upcomingMembership ? latestExpired : null);

  const submitPlanAction = async (plan, action) => {
    if (submissionLock.current || !user?.id) return;
    const canRenew = renewalTarget?.id && renewalTarget.endDate && String(renewalTarget.planId) === String(plan.id);
    const canUpgrade = data.active?.id && data.active.endDate && plan.price > data.active.planPrice;
    if ((action === 'purchased' && (data.active || upcomingMembership))
      || (action === 'renewed' && !canRenew) || (action === 'upgraded' && !canUpgrade)) return;
    setToast('');
    submissionLock.current = true;
    setSubmitting(true);
    setError('');
    try {
      if (action === 'renewed') {
        await membershipService.renew(user.id, renewalTarget.id, renewalTarget.endDate);
      } else if (action === 'upgraded') {
        await membershipService.upgrade(user.id, plan.id, data.active.id, data.active.endDate);
      } else {
        await membershipService.subscribe(user.id, plan.id);
      }
      setToast(`${plan.name} plan ${action}`);
      await loadMemberships();
    } catch (requestError) {
      setError(formatApiError(requestError, `Unable to ${action} ${plan.name} plan.`));
      if (requestError.response?.status === 409) await loadMemberships({ preserveError: true });
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  };

  if (error && !data) return <StateBlock message={error} onAction={() => loadMemberships()} />;
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

      {error ? <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-300" role="alert">{error}</div> : null}
      <p className="text-sm text-slate-500 dark:text-slate-400">Membership dates use UTC. Access ends at the start of the expiry date.</p>
      {upcomingMembership ? <p className="text-sm text-slate-600 dark:text-slate-300">An upcoming membership starts {formatMembershipDate(upcomingMembership.startDate)}. Another purchase or renewal is unavailable until that membership is current.</p> : null}
      {section === 'plans' && data.active ? <p className="text-sm text-slate-600 dark:text-slate-300">Upgrade to a higher total package price and retain your current expiry date, {formatMembershipDate(data.active.expiryDate)}. No payment is processed for these membership actions.</p> : null}

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
                {!data.active && !upcomingMembership ? <Button disabled={submitting} icon={CreditCard} onClick={() => submitPlanAction(plan, 'purchased')}>
                  Purchase Plan
                </Button> : null}
                {data.active && plan.price > data.active.planPrice ? <Button disabled={submitting} icon={Sparkles} onClick={() => submitPlanAction(plan, 'upgraded')} variant="outline">
                  Upgrade Plan
                </Button> : null}
                {renewalTarget && String(renewalTarget.planId) === String(plan.id) ? <Button disabled={submitting} icon={RotateCcw} onClick={() => submitPlanAction(plan, 'renewed')} variant="ghost">
                  Renew Plan
                </Button> : null}
                {data.active && String(data.active.planId) !== String(plan.id) && plan.price <= data.active.planPrice ? <p className="text-center text-sm text-slate-500 dark:text-slate-400">A higher total package price is required to upgrade.</p> : null}
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
              <Button disabled={submitting} icon={RotateCcw} onClick={() => submitPlanAction({ id: data.active.planId, name: data.active.plan }, 'renewed')}>
                Renew Plan
              </Button>
            </div>

            <div className="mt-8 grid gap-4 sm:grid-cols-3">
              <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Start Date</p>
                <p className="mt-1 font-bold text-slate-950 dark:text-white">{formatMembershipDate(data.active.startDate)}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Expiry Date</p>
                <p className="mt-1 font-bold text-slate-950 dark:text-white">{formatMembershipDate(data.active.expiryDate)}</p>
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
        <StateBlock title={upcomingMembership ? 'Upcoming membership' : 'No active membership'} message={upcomingMembership ? 'Your membership will become active on its start date (UTC).' : 'Choose a membership plan or renew your latest expired plan.'} />
      ) : null}

      {section === 'history' ? <DataTable columns={historyColumns} data={data.history} /> : null}

      <Toast message={toast} />
    </div>
  );
};

export default Membership;
