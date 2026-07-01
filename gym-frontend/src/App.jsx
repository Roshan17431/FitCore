import { lazy, Suspense, useContext } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import LoadingSpinner from './components/common/LoadingSpinner';
import { AuthContext } from './context/auth-context';
import MainLayout from './layouts/MainLayout';
import AdminRoute from './routes/AdminRoute';
import ProtectedRoute from './routes/ProtectedRoute';

const Admin = lazy(() => import('./pages/Admin'));
const Attendance = lazy(() => import('./pages/Attendance'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const Login = lazy(() => import('./pages/Login'));
const Membership = lazy(() => import('./pages/Membership'));
const Nutrition = lazy(() => import('./pages/Nutrition'));
const Progress = lazy(() => import('./pages/Progress'));
const Register = lazy(() => import('./pages/Register'));
const Trainers = lazy(() => import('./pages/Trainers'));
const Workouts = lazy(() => import('./pages/Workouts'));

const RootRedirect = () => {
  const { loading, user } = useContext(AuthContext);

  if (loading) return <LoadingSpinner label="Opening FitCore" />;

  return <Navigate replace to={user ? '/dashboard' : '/login'} />;
};

const App = () => (
  <BrowserRouter>
    <Suspense fallback={<LoadingSpinner label="Loading page" />}>
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<MainLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />

            <Route path="/membership" element={<Navigate replace to="/membership/plans" />} />
            <Route path="/membership/plans" element={<Membership section="plans" />} />
            <Route path="/membership/active" element={<Membership section="active" />} />
            <Route path="/membership/history" element={<Membership section="history" />} />

            <Route path="/workouts" element={<Navigate replace to="/workouts/library" />} />
            <Route path="/workouts/library" element={<Workouts section="library" />} />
            <Route path="/workouts/plans" element={<Workouts section="plans" />} />
            <Route path="/workouts/tracker" element={<Workouts section="tracker" />} />
            <Route path="/workouts/history" element={<Workouts section="history" />} />

            <Route path="/nutrition" element={<Navigate replace to="/nutrition/dashboard" />} />
            <Route path="/nutrition/dashboard" element={<Nutrition section="dashboard" />} />
            <Route path="/nutrition/diet-plans" element={<Nutrition section="diet-plans" />} />
            <Route path="/nutrition/food-logger" element={<Nutrition section="food-logger" />} />
            <Route path="/nutrition/calorie-tracker" element={<Nutrition section="calorie-tracker" />} />

            <Route path="/progress" element={<Navigate replace to="/progress/weight" />} />
            <Route path="/progress/weight" element={<Progress section="weight" />} />
            <Route path="/progress/measurements" element={<Progress section="measurements" />} />
            <Route path="/progress/bmi" element={<Progress section="bmi" />} />
            <Route path="/progress/analytics" element={<Progress section="analytics" />} />

            <Route path="/trainers" element={<Navigate replace to="/trainers/list" />} />
            <Route path="/trainers/list" element={<Trainers section="list" />} />
            <Route path="/trainers/profiles" element={<Trainers section="profiles" />} />
            <Route path="/trainers/booking" element={<Trainers section="booking" />} />
            <Route path="/trainers/history" element={<Trainers section="history" />} />

            <Route path="/attendance" element={<Navigate replace to="/attendance/check-in" />} />
            <Route path="/attendance/check-in" element={<Attendance section="check-in" />} />
            <Route path="/attendance/history" element={<Attendance section="history" />} />
            <Route path="/attendance/statistics" element={<Attendance section="statistics" />} />

            <Route element={<AdminRoute />}>
              <Route path="/admin" element={<Navigate replace to="/admin/dashboard" />} />
              <Route path="/admin/dashboard" element={<Admin section="dashboard" />} />
              <Route path="/admin/users" element={<Admin section="users" />} />
              <Route path="/admin/trainers" element={<Admin section="trainers" />} />
              <Route path="/admin/memberships" element={<Admin section="memberships" />} />
              <Route path="/admin/attendance" element={<Admin section="attendance" />} />
            </Route>

            <Route path="/schedule" element={<Navigate replace to="/trainers/booking" />} />
            <Route path="/dashboard/workouts" element={<Navigate replace to="/workouts/library" />} />
            <Route path="/dashboard/schedule" element={<Navigate replace to="/trainers/booking" />} />
            <Route path="/dashboard/nutrition" element={<Navigate replace to="/nutrition/dashboard" />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate replace to="/" />} />
      </Routes>
    </Suspense>
  </BrowserRouter>
);

export default App;
