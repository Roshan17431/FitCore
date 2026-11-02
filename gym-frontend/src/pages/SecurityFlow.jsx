import { useContext, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import FormField from '../components/forms/FormField';
import { AuthContext } from '../context/auth-context';
import { authService, formatApiError } from '../services/api';
import { dashboardPath } from '../utils/tenant-session';
import { validateResetPassword } from '../utils/security-token';

export default function SecurityFlow({ reset = false }) {
  const auth = useContext(AuthContext), navigate = useNavigate();
  const path = reset ? '/reset-password' : '/verify-email';
  const token = auth.securityToken?.path === path ? auth.securityToken.token : null;
  const [email, setEmail] = useState(''), [password, setPassword] = useState(''), [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  const generation = useRef(0), lock = useRef(false);
  useEffect(() => { generation.current++; setError(''); if (token) setMessage(''); const identity = generation; return () => { identity.current++; }; }, [token, reset]);
  const submit = async event => {
    event.preventDefault(); if (lock.current) return;
    const validation = reset ? validateResetPassword(password, confirmation) : '';
    if (validation) { setError(validation); return; }
    lock.current = true; setBusy(true); setError(''); const attempt = ++generation.current;
    try {
      if (!token) {
        await authService.requestVerification(email.trim());
        if (attempt === generation.current) setMessage('If eligible and local test delivery is configured, verification instructions will arrive. External sending is disabled.');
      } else if (reset) {
        await auth.completeReset({ email: email.trim(), token, password });
        if (attempt !== generation.current) return;
        auth.clearSecurity(); setPassword(''); setConfirmation(''); setMessage('Password changed. Sign in again; earlier sessions are invalid.');
      } else {
        const result = await auth.verifyEmail(email.trim(), token);
        if (attempt !== generation.current) return;
        if (!result.success) throw new Error(result.error);
        if (result.user) navigate(dashboardPath(result.user), { replace: true });
        else { auth.clearSecurity(); setMessage('Email verified. Sign in to continue.'); }
      }
    } catch (failure) { if (attempt === generation.current) setError(failure.response?.data?.message === 'SECURITY_ONBOARDING_DISABLED' ? 'Verification and recovery are unavailable in this environment.' : formatApiError(failure, 'This link is invalid, expired or already used.')); }
    finally { lock.current = false; setBusy(false); }
  };
  return <AuthLayout><Card className="w-full"><h1 className="text-3xl font-semibold">{reset ? 'Reset password' : 'Verify email'}</h1>
    <p className="my-4 text-sm text-muted">Use the exact account email. Links expire and work once. External email sending remains disabled.</p>
    {error ? <p role="alert" className="mb-4 text-sm">{error}</p> : null}{message ? <p role="status" className="mb-4 text-sm text-brand">{message}</p> : null}
    {reset && !token ? <p className="my-4 text-sm">Open a valid recovery link or <Link className="text-brand" to="/forgot-password">request another</Link>.</p> : <form className="space-y-4" onSubmit={submit}>
      <FormField label="Account email" required type="email" maxLength={254} autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} />
      {reset ? <><FormField label="New password" type="password" required autoComplete="new-password" minLength={12} maxLength={64} value={password} onChange={e => setPassword(e.target.value)} /><FormField label="Confirm password" type="password" required autoComplete="new-password" value={confirmation} onChange={e => setConfirmation(e.target.value)} /></> : null}
      <Button type="submit" isLoading={busy}>{reset ? 'Change password' : token ? 'Verify email' : 'Request verification'}</Button>
    </form>}<Link className="mt-5 inline-block text-sm font-semibold text-brand" to="/login">Sign in</Link>
  </Card></AuthLayout>;
}
