import { useContext } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { AuthContext } from '../context/auth-context';

const AdminRoute = () => {
  const { user } = useContext(AuthContext);

  if (user?.role !== 'ADMIN') {
    return <Navigate replace to="/dashboard" />;
  }

  return <Outlet />;
};

export default AdminRoute;

