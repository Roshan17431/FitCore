import { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/auth-context';
import { UserPlus, Mail, Lock, User, AlertCircle } from 'lucide-react';
import './Auth.css';

const Register = () => {
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { register } = useContext(AuthContext);
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    
    if (!formData.fullName || !formData.email || !formData.password) {
      setError('Please fill in all required fields');
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setIsSubmitting(true);
    const result = await register({
      fullName: formData.fullName,
      email: formData.email,
      password: formData.password
    });
    
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
            <UserPlus size={28} color="var(--primary)" />
          </div>
          <h2>Create Account</h2>
          <p className="text-muted">Join FitCore to track your progress</p>
        </div>

        {error && (
          <div className="auth-error-alert animate-fade-in">
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label className="form-label">Full Name</label>
            <div className="input-with-icon">
              <User size={18} className="input-icon" />
              <input 
                type="text" 
                name="fullName"
                className="form-input" 
                placeholder="John Doe"
                value={formData.fullName}
                onChange={handleChange}
                disabled={isSubmitting}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <div className="input-with-icon">
              <Mail size={18} className="input-icon" />
              <input 
                type="email" 
                name="email"
                className="form-input" 
                placeholder="you@example.com"
                value={formData.email}
                onChange={handleChange}
                disabled={isSubmitting}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <div className="input-with-icon">
              <Lock size={18} className="input-icon" />
              <input 
                type="password" 
                name="password"
                className="form-input" 
                placeholder="••••••••"
                value={formData.password}
                onChange={handleChange}
                disabled={isSubmitting}
              />
            </div>
          </div>
          
          <div className="form-group">
            <label className="form-label">Confirm Password</label>
            <div className="input-with-icon">
              <Lock size={18} className="input-icon" />
              <input 
                type="password" 
                name="confirmPassword"
                className="form-input" 
                placeholder="••••••••"
                value={formData.confirmPassword}
                onChange={handleChange}
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
                <div className="spinner"></div> Creating Account...
              </span>
            ) : 'Sign Up'}
          </button>
        </form>

        <div className="auth-footer">
          <p className="text-muted">
            Already have an account? <Link to="/login" className="text-gradient">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Register;
