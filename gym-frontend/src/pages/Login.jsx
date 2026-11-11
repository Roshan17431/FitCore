import { useContext, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Lock, Mail } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import { FreshContextGuidance } from '../components/common/CookieSessionNotice';
import FormField from '../components/forms/FormField';
import { AuthContext } from '../context/auth-context';
import AuthLayout from '../layouts/AuthLayout';
import { dashboardPath } from '../utils/tenant-session';

const Login = () => {
  const { login, logout, recoverSession, sessionError, cookieTransport, sessionRecoveryAvailable, sessionSignOutRequired, sessionFreshContextRequired } = useContext(AuthContext);
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '', rememberMe: true });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const updateField = (event) => {
    const { checked, name, type, value } = event.target;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (isSubmitting || sessionFreshContextRequired) return;
    setError('');

    if (!form.email.trim() || !form.password) {
      setError('Email and password are required.');
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await login(form.email.trim(), form.password, form.rememberMe);
      if (result.success) {
        navigate(result.user?.role === 'OWNER' ? '/owner' : location.state?.from?.pathname || dashboardPath(result.user), { replace: true });
      } else {
        setError(result.error);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const checkSession = async () => {
    if (isSubmitting || sessionFreshContextRequired) return;
    setIsSubmitting(true); setError('');
    try {
      const result = await recoverSession();
      if (result.success && result.user) navigate(dashboardPath(result.user), {replace:true});
      else if (!result.success) setError(result.error);
    } finally { setIsSubmitting(false); }
  };
  const signOutPrevious = async () => {
    if (isSubmitting || sessionFreshContextRequired) return;
    setIsSubmitting(true); setError('');
    try { await logout(); }
    finally { setIsSubmitting(false); }
  };

  return (
    <AuthLayout>
        <Card className="w-full border-0 bg-transparent p-0 shadow-none sm:p-0">
          <div className="mb-8">
            <p className="mb-3 text-xs font-bold uppercase tracking-[.18em] text-brand">Your fitness workspace</p>
            <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">Welcome Back</h1>
            <p className="mt-2 text-sm text-muted">Sign in to continue your fitness workspace.</p>
          </div>

          {!sessionFreshContextRequired && (error || sessionError) ? (
            <div role="alert" className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
              {error || sessionError}
            </div>
          ) : null}

          {cookieTransport && sessionFreshContextRequired ? <FreshContextGuidance /> : null}
          {cookieTransport && !sessionFreshContextRequired && sessionRecoveryAvailable ? (
            <Button className="mb-4 w-full" disabled={isSubmitting} onClick={checkSession} type="button">Check sign-in status</Button>
          ) : null}
          {cookieTransport && !sessionFreshContextRequired && sessionSignOutRequired ? (
            <Button className="mb-4 w-full" disabled={isSubmitting} onClick={signOutPrevious} type="button">Sign out previous session</Button>
          ) : null}

          <form className="space-y-4" onSubmit={handleSubmit}>
            <FormField
              autoComplete="email"
              icon={Mail}
              label="Email"
              name="email"
              onChange={updateField}
              placeholder="you@example.com"
              type="email"
              value={form.email}
            />
            <FormField
              autoComplete="current-password"
              icon={Lock}
              label="Password"
              name="password"
              onChange={updateField}
              placeholder="Enter password"
              type="password"
              value={form.password}
            />

            <div className="flex items-center justify-between gap-3 text-sm">
              {!cookieTransport ? <label className="flex items-center gap-2 font-medium text-muted">
                <input
                  checked={form.rememberMe}
                  className="h-4 w-4 rounded border-line text-emerald-500 focus:ring-emerald-400"
                  name="rememberMe"
                  onChange={updateField}
                  type="checkbox"
                />
                Remember me
              </label> : <span className="text-muted">Secure browser session</span>}
              <Link className="font-semibold text-brand" to="/forgot-password">
                Forgot password
              </Link>
            </div>

            <Button className="w-full" isLoading={isSubmitting} disabled={cookieTransport && (sessionRecoveryAvailable || sessionSignOutRequired || sessionFreshContextRequired)} type="submit">
              Login
            </Button>
          </form>

          {cookieTransport ? <p className="mt-4 text-center text-sm"><Link className="font-semibold text-brand" to="/verify-email">Request email verification</Link></p> : null}
          <p className="mt-6 text-center text-sm text-muted">
            New to FitCore?{' '}
            <Link className="font-semibold text-brand" to="/create-gym">
              Create your gym
            </Link>
          </p>
        </Card>
    </AuthLayout>
  );
};

export default Login;

