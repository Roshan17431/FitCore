import { useContext } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { AuthContext } from '../context/auth-context';

const ProtectedRoute = () => {
  const { loading, user } = useContext(AuthContext);
  const location = useLocation();

  if (loading) return <LoadingSpinner label="Checking session" />;

  if (!user) {
    return <Navigate replace state={{ from: location }} to="/login" />;
  }

  return <Outlet />;
};

export default ProtectedRoute;

