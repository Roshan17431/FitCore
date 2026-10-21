import { useContext, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Lock, Mail, Target, User } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import AuthLayout from '../layouts/AuthLayout';
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
    if (isSubmitting) return;
    setError('');

    console.info('[FitCore Register] submit handler executed', {
      fullName: form.fullName,
      email: form.email,
      fitnessGoal: form.fitnessGoal,
      hasPassword: Boolean(form.password),
      hasConfirmPassword: Boolean(form.confirmPassword),
      passwordsMatch: form.password === form.confirmPassword,
    });

    if (!form.fullName.trim() || !form.email.trim() || !form.password || !form.confirmPassword) {
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
      const result = await register({ ...form, fullName: form.fullName.trim(), email: form.email.trim() });
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
    <AuthLayout>
      <Card className="w-full border-0 bg-transparent p-0 shadow-none sm:p-0">
        <div className="mb-8">
          <p className="mb-3 text-xs font-bold uppercase tracking-[.18em] text-brand">Start your next chapter</p>
          <h1 className="text-3xl font-semibold sm:text-4xl text-ink">Create Account</h1>
          <p className="mt-2 text-sm text-muted">Set up your member profile and training goal.</p>
        </div>

        {error ? (
          <div role="alert" className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
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

        <p className="mt-6 text-center text-sm text-muted">
          Already have an account?{' '}
          <Link className="font-semibold text-brand" to="/login">
            Sign in
          </Link>
        </p>
      </Card>
    </AuthLayout>
  );
};

export default Register;
