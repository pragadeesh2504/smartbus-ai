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
import { SuperAdminPortal } from './pages/admin/SuperAdminPortal';
import { StudentRegister } from './pages/StudentRegister';
import { CollegeAdminRegister } from './pages/CollegeAdminRegister';

const App: React.FC = () => {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<StudentRegister />} />
          <Route path="/register/college" element={<CollegeAdminRegister />} />
          <Route path="/register-college" element={<CollegeAdminRegister />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          
          <Route 
            path="/super-admin/*" 
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                <SuperAdminPortal />
              </ProtectedRoute>
            } 
          />

          <Route 
            path="/student/*" 
            element={
              <ProtectedRoute allowedRoles={['STUDENT']}>
                <StudentPortal />
              </ProtectedRoute>
            } 
          />
          
          <Route 
            path="/admin/*" 
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminPortal />
              </ProtectedRoute>
            } 
          />

          <Route 
            path="/driver/*" 
            element={
              <ProtectedRoute allowedRoles={['DRIVER']}>
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