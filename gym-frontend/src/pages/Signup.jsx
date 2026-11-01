import { useContext, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import FormField from '../components/forms/FormField';
import { AuthContext } from '../context/auth-context';
import { dashboardPath, validateSignup } from '../utils/tenant-session';

export default function Signup({ owner = false }) {
  const auth = useContext(AuthContext), navigate = useNavigate(), lock = useRef(false);
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '', gymName: '', slug: '' });
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [success, setSuccess] = useState(false);
  const update = event => setForm(current => ({ ...current, [event.target.name]: event.target.value }));
  const unavailable = !owner && !auth.invitationToken;
  const submit = async event => {
    event.preventDefault(); if (lock.current || unavailable) return;
    const validation = validateSignup(form, owner); setError(validation); if (validation) return;
    lock.current = true; setBusy(true);
    const input = { fullName: form.fullName.trim(), email: form.email.trim(), password: form.password };
    try {
      const result = owner ? await auth.registerOwner({ ...input, gymName: form.gymName.trim(), slug: form.slug }) : await auth.register({ ...input, invitationToken: auth.invitationToken });
      if (result.success) { setSuccess(true); setForm(current => ({ ...current, password: '', confirmPassword: '' })); navigate(dashboardPath(result.user), { replace: true, state: { welcome: true } }); }
      else setError(result.error.includes('DEVELOPMENT_ONBOARDING_DISABLED') ? 'Public onboarding is unavailable. This environment has not enabled registration.' : result.error.includes('Invalid or unavailable invitation') ? 'This invitation is invalid, expired, revoked or already used. Check the invited email or request a new link.' : result.error);
    } finally { lock.current = false; setBusy(false); }
  };
  return <AuthLayout><Card className="border-0 bg-transparent p-0 shadow-none">
    <p className="mb-3 text-xs font-bold uppercase tracking-[.18em] text-brand">{owner ? 'Your gym. Your workspace.' : 'Your invitation to train'}</p>
    <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{owner ? 'Create your gym' : 'Join your gym'}</h1>
    <p className="mb-6 mt-3 text-sm leading-6 text-muted">{owner ? 'Bring your team and members together in one FitCore workspace.' : 'Use the exact email address invited by your gym. Your role and gym are assigned securely by the invitation.'}</p>
    <p className="mb-6 rounded-xl border border-line bg-surface-alt p-3 text-xs leading-5 text-muted">Registration is available only in approved local demos. Email verification and invitation email delivery are not enabled.</p>
    {unavailable ? <div role="status" className="space-y-4"><p>Open a valid invitation link to join a gym. Public member registration is unavailable.</p><Link className="font-semibold text-brand" to="/create-gym">Create a gym workspace</Link></div> : <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      {error ? <p role="alert" className="rounded-xl border border-line bg-surface-alt p-3 text-sm sm:col-span-2">{error}</p> : null}
      {success ? <p role="status">Account created. Opening your workspace…</p> : null}
      <FormField className="sm:col-span-2" required minLength={3} maxLength={100} autoComplete="name" label="Full name" name="fullName" value={form.fullName} onChange={update} />
      <FormField className="sm:col-span-2" required maxLength={254} autoComplete="email" type="email" label={owner ? 'Email' : 'Invited email'} name="email" value={form.email} onChange={update} />
      <FormField required minLength={12} maxLength={64} autoComplete="new-password" type="password" label="Password" name="password" value={form.password} onChange={update} />
      <FormField required minLength={12} maxLength={64} autoComplete="new-password" type="password" label="Confirm password" name="confirmPassword" value={form.confirmPassword} onChange={update} />
      <p className="text-xs leading-5 text-muted sm:col-span-2">Use 12–64 characters, including uppercase, lowercase, a number and a symbol.</p>
      {owner ? <><FormField required maxLength={100} label="Gym name" name="gymName" value={form.gymName} onChange={update} /><FormField required pattern="[a-z0-9][a-z0-9\-]{2,62}" label="Gym slug" name="slug" value={form.slug} onChange={update} /><p className="text-xs text-muted sm:col-span-2">Slug: 3–63 lowercase letters, numbers and hyphens. It must be unique.</p></> : null}
      <Button className="sm:col-span-2" type="submit" isLoading={busy}>{owner ? 'Create gym workspace' : 'Accept invitation'}</Button>
    </form>}
    <p className="mt-6 text-sm text-muted">Already registered? <Link className="font-semibold text-brand" to="/login">Sign in</Link></p>
  </Card></AuthLayout>;
}
