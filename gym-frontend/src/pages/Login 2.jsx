import { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/auth-context';
import { LogIn, Mail, Lock, AlertCircle } from 'lucide-react';
import './Auth.css';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { login } = useContext(AuthContext);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    
    if (!email || !password) {
      setError('Please fill in all fields');
      return;
    }

    setIsSubmitting(true);
    const result = await login(email, password);
    
    if (result.success) {
      navigate('/dashboard');
    } else {
      setError(result.error);
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-background-decoration"></div>
      <div className="glass-card auth-card animate-fade-in">
        <div className="auth-header">
          <div className="auth-logo">
            <LogIn size={28} color="var(--primary)" />
          </div>
          <h2>Welcome Back</h2>
          <p className="text-muted">Enter your credentials to access your account</p>
        </div>

        {error && (
          <div className="auth-error-alert animate-fade-in">
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <div className="input-with-icon">
              <Mail size={18} className="input-icon" />
              <input 
                type="email" 
                className="form-input" 
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isSubmitting}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="d-flex justify-between">
              <label className="form-label">Password</label>
              <a href="#" className="forgot-password">Forgot?</a>
            </div>
            <div className="input-with-icon">
              <Lock size={18} className="input-icon" />
              <input 
                type="password" 
                className="form-input" 
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isSubmitting}
              />
            </div>
          </div>

          <button 
            type="submit" 
            className="btn btn-primary auth-submit-btn" 
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <span className="d-flex align-center gap-2">
                <div className="spinner"></div> Signing in...
              </span>
            ) : 'Sign In'}
          </button>
        </form>

        <div className="auth-footer">
          <p className="text-muted">
            Don't have an account? <Link to="/register" className="text-gradient">Sign up</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
