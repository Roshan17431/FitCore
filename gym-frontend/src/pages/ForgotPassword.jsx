import { useContext, useState } from 'react';
import { Link } from 'react-router-dom';
import { Dumbbell, Mail } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import FormField from '../components/forms/FormField';
import { AuthContext } from '../context/auth-context';

const ForgotPassword = () => {
  const { resetPassword } = useContext(AuthContext);
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (isSubmitting) return;
    setError('');
    setMessage('');
    const normalizedEmail = email.trim();
    if (!normalizedEmail) {
      setError('Email is required.');
      return;
    }
    setIsSubmitting(true);

    try {
      const result = await resetPassword(normalizedEmail);
      if (result.success) {
        setMessage('Contact your gym administrator to complete your password reset.');
      } else {
        setError(result.error);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-8 dark:bg-slate-950 sm:px-6">
      <Card className="w-full max-w-md p-6 sm:p-8">
        <div className="mb-8">
          <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-lg bg-emerald-500 text-white">
            <Dumbbell className="h-6 w-6" />
          </div>
          <h1 className="text-2xl font-bold text-slate-950 dark:text-white">Forgot Password</h1>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Email recovery is not configured yet. Contact your gym administrator for help resetting your password.</p>
        </div>

        {error ? (
          <div role="alert" className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
            {error}
          </div>
        ) : null}
        {message ? (
          <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
            {message}
          </div>
        ) : null}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <FormField
            autoComplete="email"
            icon={Mail}
            label="Email"
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
            type="email"
            value={email}
          />
          <Button className="w-full" isLoading={isSubmitting} type="submit">
            Request Reset
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
          <Link className="font-semibold text-emerald-600 dark:text-emerald-300" to="/login">
            Back to login
          </Link>
        </p>
      </Card>
    </main>
  );
};

export default ForgotPassword;
