import { useContext, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { UsersRound, ShieldCheck, Dumbbell, CreditCard } from 'lucide-react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import PageHeader from '../components/common/PageHeader';
import StatCard from '../components/common/StatCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import FormField from '../components/forms/FormField';
import { gymService, membershipService, formatApiError } from '../services/api';
import { AuthContext } from '../context/auth-context';

export default function GymWorkspace({ settings = false }) {
  const { refresh } = useContext(AuthContext);
  const [gym, setGym] = useState(null), [plans, setPlans] = useState([]), [name, setName] = useState('');
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const lock = useRef(false);
  useEffect(() => { let active = true; Promise.all([gymService.get(), membershipService.getPlans()]).then(([current, packages]) => { if (active) { setGym(current); setName(current.name); setPlans(packages); } }).catch(e => { if (active) setError(formatApiError(e)); }); return () => { active = false; }; }, []);
  const save = async event => { event.preventDefault(); if (lock.current || !name.trim()) return; lock.current = true; setBusy(true); setError(''); setMessage(''); try { const saved = await gymService.update({ name: name.trim() }); setGym(saved); await refresh(); setMessage('Gym name updated'); } catch (e) { setError(formatApiError(e)); } finally { lock.current = false; setBusy(false); } };
  if (!gym) return error ? <p role="alert">{error}</p> : <LoadingSpinner label="Loading your gym" />;
  return <div className="space-y-6"><PageHeader kicker="Your workspace" title={settings ? 'Gym settings' : gym.name} description={settings ? 'Manage the details your team sees.' : 'Your people, training and memberships in one place.'} />
    <div className="grid gap-4 sm:grid-cols-3"><StatCard icon={UsersRound} title="Members" value={gym.members} detail="In your gym" /><StatCard icon={Dumbbell} title="Trainers" value={gym.trainers} detail="In your gym" /><StatCard icon={ShieldCheck} title="Administrators" value={gym.administrators} detail="In your gym" /></div>
    {settings ? <Card><h2 className="mb-4 text-lg font-semibold">Workspace details</h2><form className="max-w-xl space-y-4" onSubmit={save}><FormField required maxLength={100} label="Gym name" value={name} onChange={e => setName(e.target.value)} /><p className="text-sm text-muted">Workspace slug: <span className="font-medium text-ink">{gym.slug}</span></p><p className="text-xs leading-5 text-muted">Ownership, status and gym identity are managed securely and cannot be edited here.</p>{error ? <p role="alert">{error}</p> : null}{message ? <p role="status" className="text-brand">{message}</p> : null}<Button type="submit" isLoading={busy}>Save gym name</Button></form></Card> : <>
      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]"><Card><div className="mb-5 flex items-center gap-3"><CreditCard className="h-5 w-5 text-brand" /><h2 className="text-lg font-semibold">Your membership catalog</h2></div>{plans.length ? <ul className="divide-y divide-line">{plans.map(plan => <li key={plan.id} className="py-4"><p className="font-semibold">{plan.name}</p><p className="mt-1 text-sm text-muted">{plan.durationMonths} months · ${plan.price}</p></li>)}</ul> : <div className="rounded-xl border border-dashed border-line p-6"><p className="font-medium">Start with your first membership plan</p><p className="mt-2 text-sm text-muted">Create packages for your gym through membership management.</p></div>}<Link className="mt-5 inline-block font-semibold text-brand" to="/admin/memberships">Manage membership plans →</Link></Card>
      <Card><h2 className="text-lg font-semibold">Build your team</h2><p className="mb-6 mt-2 text-sm leading-6 text-muted">Invite members and trainers to your workspace. Every invitation belongs to this gym.</p><div className="flex flex-col gap-4"><Link className="rounded-xl bg-brand px-4 py-3 text-center text-sm font-semibold text-on-brand" to="/gym/invitations">Invite people</Link><Link className="font-semibold text-brand" to="/admin/users">Manage members →</Link><Link className="font-semibold text-brand" to="/gym/settings">Update gym details →</Link></div></Card></div>
    </>}
  </div>;
}
