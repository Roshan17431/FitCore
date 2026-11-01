import { useContext } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { AuthContext } from '../context/auth-context';

const AdminRoute = () => {
  const { user } = useContext(AuthContext);

  if (!['OWNER', 'ADMIN'].includes(user?.role)) {
    return <Navigate replace to="/dashboard" />;
  }

  return <Outlet />;
};

export default AdminRoute;

