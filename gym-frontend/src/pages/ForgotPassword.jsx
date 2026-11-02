import { useContext, useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import AuthLayout from '../layouts/AuthLayout';
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
        setMessage('If this account is eligible and delivery is configured, recovery instructions will arrive. Otherwise contact your gym administrator.');
      } else {
        setError(result.error);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout>
      <Card className="w-full border-0 bg-transparent p-0 shadow-none sm:p-0">
        <div className="mb-8">
          <p className="mb-3 text-xs font-bold uppercase tracking-[.18em] text-brand">Account recovery</p>
          <h1 className="text-3xl font-semibold sm:text-4xl text-ink">Forgot Password</h1>
          <p className="mt-2 text-sm text-muted">Request a recovery link. External email delivery is disabled until separately configured and approved.</p>
        </div>

        {error ? (
          <div role="alert" className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
            {error}
          </div>
        ) : null}
        {message ? (
          <div role="status" className="mb-4 rounded-lg border border-emerald-200 bg-brand-soft px-3 py-2 text-sm font-medium text-brand dark:border-emerald-500/30">
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

        <p className="mt-6 text-center text-sm text-muted">
          <Link className="font-semibold text-brand" to="/login">
            Back to login
          </Link>
        </p>
      </Card>
    </AuthLayout>
  );
};

export default ForgotPassword;
