import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Login } from './pages/Login';
import { ForgotPassword } from './pages/ForgotPassword';
import { ResetPassword } from './pages/ResetPassword';
import { StudentPortal } from './pages/student/StudentPortal';
import { AdminPortal } from './pages/admin/AdminPortal';
import { DriverPortal } from './pages/driver/DriverPortal';

const App: React.FC = () => {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          
          <Route 
            path="/student/*" 
            element={
              <ProtectedRoute allowedRoles={['STUDENT', 'SUPER_ADMIN']}>
                <StudentPortal />
              </ProtectedRoute>
            } 
          />
          
          <Route 
            path="/admin/*" 
            element={
              <ProtectedRoute allowedRoles={['ADMIN', 'SUPER_ADMIN']}>
                <AdminPortal />
              </ProtectedRoute>
            } 
          />

          <Route 
            path="/driver/*" 
            element={
              <ProtectedRoute allowedRoles={['DRIVER', 'SUPER_ADMIN']}>
                <DriverPortal />
              </ProtectedRoute>
            } 
          />

          {/* Fallback redirects */}
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
};

export default App;