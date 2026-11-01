import { useContext } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { AuthContext } from '../context/auth-context';
export default function OwnerRoute() { const { user } = useContext(AuthContext); return user?.role === 'OWNER' ? <Outlet /> : <Navigate replace to="/dashboard" />; }
