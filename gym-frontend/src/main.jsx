import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import { AuthProvider } from './context/AuthContext.jsx';
import { ThemeProvider } from './context/ThemeContext.jsx';
import './index.css';
import { captureInvitation } from './utils/tenant-session';
const initialInvitation = captureInvitation();

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ThemeProvider>
      <AuthProvider initialInvitation={initialInvitation}>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
);
