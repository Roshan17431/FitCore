import { useContext, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Dumbbell, Lock, Mail } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import FormField from '../components/forms/FormField';
import { AuthContext } from '../context/auth-context';
import heroImage from '../assets/hero.png';

const Login = () => {
  const { login } = useContext(AuthContext);
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
    setError('');

    if (!form.email || !form.password) {
      setError('Email and password are required.');
      return;
    }

    setIsSubmitting(true);
    const result = await login(form.email, form.password, form.rememberMe);
    setIsSubmitting(false);

    if (result.success) {
      navigate(location.state?.from?.pathname || '/dashboard', { replace: true });
    } else {
      setError(result.error);
    }
  };

  return (
    <main className="grid min-h-screen bg-slate-950 text-white lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden lg:block">
        <img alt="Gym training floor" className="h-full w-full object-cover" src={heroImage} />
        <div className="absolute inset-0 bg-slate-950/45" />
        <div className="absolute inset-x-0 bottom-0 p-10">
          <div className="max-w-xl">
            <p className="text-sm font-bold uppercase text-emerald-300">FitCore Operations</p>
            <h1 className="mt-3 text-5xl font-bold leading-tight">Member, training, nutrition, and attendance control.</h1>
            <p className="mt-4 max-w-lg text-base text-slate-200">
              A focused dashboard for teams that need gym operations and member progress in one place.
            </p>
          </div>
        </div>
      </section>

      <section className="flex items-center justify-center px-4 py-8 sm:px-6">
        <Card className="w-full max-w-md border-slate-800 bg-white p-6 text-slate-950 dark:bg-slate-950 dark:text-white sm:p-8">
          <div className="mb-8">
            <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-lg bg-emerald-500 text-white">
              <Dumbbell className="h-6 w-6" />
            </div>
            <h1 className="text-2xl font-bold">Welcome Back</h1>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Sign in to continue your fitness workspace.</p>
          </div>

          {error ? (
            <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
              {error}
            </div>
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
              <label className="flex items-center gap-2 font-medium text-slate-600 dark:text-slate-300">
                <input
                  checked={form.rememberMe}
                  className="h-4 w-4 rounded border-slate-300 text-emerald-500 focus:ring-emerald-400"
                  name="rememberMe"
                  onChange={updateField}
                  type="checkbox"
                />
                Remember me
              </label>
              <Link className="font-semibold text-emerald-600 dark:text-emerald-300" to="/forgot-password">
                Forgot password
              </Link>
            </div>

            <Button className="w-full" isLoading={isSubmitting} type="submit">
              Login
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
            New to FitCore?{' '}
            <Link className="font-semibold text-emerald-600 dark:text-emerald-300" to="/register">
              Create account
            </Link>
          </p>
        </Card>
      </section>
    </main>
  );
};

export default Login;

