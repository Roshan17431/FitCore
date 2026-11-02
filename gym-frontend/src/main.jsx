import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import { AuthProvider } from './context/AuthContext.jsx';
import { ThemeProvider } from './context/ThemeContext.jsx';
import './index.css';
import { captureInvitation } from './utils/tenant-session';
import { captureSecurityToken } from './utils/security-token';
const initialInvitation = captureInvitation();
const initialSecurity = captureSecurityToken();

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ThemeProvider>
      <AuthProvider initialInvitation={initialInvitation} initialSecurity={initialSecurity}>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
);
