import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Login from './pages/Login';
import Register from './pages/Register';
import MainLayout from './layouts/MainLayout';
import Dashboard from './pages/Dashboard';
import Workouts from './pages/Workouts';
import Schedule from './pages/Schedule';
import Nutrition from './pages/Nutrition';

function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          
          <Route element={<MainLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/workouts" element={<Workouts />} />
            <Route path="/schedule" element={<Schedule />} />
            <Route path="/nutrition" element={<Nutrition />} />
            <Route path="/dashboard/workouts" element={<Navigate to="/workouts" replace />} />
            <Route path="/dashboard/schedule" element={<Navigate to="/schedule" replace />} />
            <Route path="/dashboard/nutrition" element={<Navigate to="/nutrition" replace />} />
          </Route>
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;
