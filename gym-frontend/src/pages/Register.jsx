import { useContext, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Dumbbell, Lock, Mail, Target, User } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import FormField from '../components/forms/FormField';
import { AuthContext } from '../context/auth-context';

const fitnessGoals = ['Weight Loss', 'Muscle Gain', 'Weight Gain', 'Maintenance'];

const Register = () => {
  const { register } = useContext(AuthContext);
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
    fitnessGoal: fitnessGoals[0],
  });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const updateField = (event) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');

    console.info('[FitCore Register] submit handler executed', {
      fullName: form.fullName,
      email: form.email,
      fitnessGoal: form.fitnessGoal,
      hasPassword: Boolean(form.password),
      hasConfirmPassword: Boolean(form.confirmPassword),
      passwordsMatch: form.password === form.confirmPassword,
    });

    if (!form.fullName || !form.email || !form.password || !form.confirmPassword) {
      console.warn('[FitCore Register] validation stopped submission: missing required fields');
      setError('All fields are required.');
      return;
    }

    if (form.password !== form.confirmPassword) {
      console.warn('[FitCore Register] validation stopped submission: passwords do not match');
      setError('Passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      console.info('[FitCore Register] calling AuthContext.register');
      const result = await register(form);
      console.info('[FitCore Register] AuthContext.register completed', { success: result.success });

      if (result.success) {
        navigate('/dashboard', { replace: true });
      } else {
        setError(result.error);
      }
    } catch (exception) {
      console.error('[FitCore Register] unexpected submit exception', exception);
      setError('Registration failed before the request could complete.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-8 dark:bg-slate-950 sm:px-6">
      <Card className="w-full max-w-xl p-6 sm:p-8">
        <div className="mb-8">
          <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-lg bg-emerald-500 text-white">
            <Dumbbell className="h-6 w-6" />
          </div>
          <h1 className="text-2xl font-bold text-slate-950 dark:text-white">Create Account</h1>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Set up your member profile and training goal.</p>
        </div>

        {error ? (
          <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
            {error}
          </div>
        ) : null}

        <form className="grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit}>
          <FormField
            autoComplete="name"
            className="sm:col-span-2"
            icon={User}
            label="Full name"
            name="fullName"
            onChange={updateField}
            placeholder="Alex Carter"
            value={form.fullName}
          />
          <FormField
            autoComplete="email"
            className="sm:col-span-2"
            icon={Mail}
            label="Email"
            name="email"
            onChange={updateField}
            placeholder="you@example.com"
            type="email"
            value={form.email}
          />
          <FormField
            autoComplete="new-password"
            icon={Lock}
            label="Password"
            name="password"
            onChange={updateField}
            placeholder="Create password"
            type="password"
            value={form.password}
          />
          <FormField
            autoComplete="new-password"
            icon={Lock}
            label="Confirm password"
            name="confirmPassword"
            onChange={updateField}
            placeholder="Confirm password"
            type="password"
            value={form.confirmPassword}
          />
          <FormField
            className="sm:col-span-2"
            icon={Target}
            label="Fitness goal"
            name="fitnessGoal"
            onChange={updateField}
            options={fitnessGoals}
            value={form.fitnessGoal}
          />
          <Button className="sm:col-span-2" isLoading={isSubmitting} type="submit">
            Register
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
          Already have an account?{' '}
          <Link className="font-semibold text-emerald-600 dark:text-emerald-300" to="/login">
            Sign in
          </Link>
        </p>
      </Card>
    </main>
  );
};

export default Register;
