import React from 'react';
import { Routes, Route, Link, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  QrCode,
  MapPin,
  History,
  Bell,
  User as UserIcon,
  LogOut,
  Shield,
  Calendar
} from 'lucide-react';
import { Dashboard } from './Dashboard';
import { Scan } from './Scan';
import { TripScreen } from './TripScreen';
import { TripsHistory } from './TripsHistory';
import { Notifications } from './Notifications';
import { Profile } from './Profile';
import { DriverSchedules } from './DriverSchedules';
import { ErrorBoundary } from '../../components/common/ErrorBoundary';

export const DriverPortal: React.FC = () => {
  const { logout, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { name: 'Dashboard', path: '/driver/dashboard', icon: LayoutDashboard },
    { name: 'Schedules', path: '/driver/schedules', icon: Calendar },
    { name: 'Scan QR', path: '/driver/scan', icon: QrCode },
    { name: 'Active Trip', path: '/driver/active', icon: MapPin },
    { name: 'History', path: '/driver/trips', icon: History },
    { name: 'Alerts', path: '/driver/notifications', icon: Bell },
    { name: 'Profile', path: '/driver/profile', icon: UserIcon },
  ];

  return (
    <div className="min-h-screen bg-brandBg text-brandTextPrimary flex flex-col font-sans overflow-hidden">
      {/* Top Header Bar */}
      <header className="h-16 bg-white border-b border-brandBorder flex items-center justify-between px-4 shrink-0 z-10">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
            <Shield className="w-5 h-5" />
          </div>
          <span className="font-bold tracking-tight text-brandNavy uppercase text-sm">
            SmartBus Driver
          </span>
        </div>
        <div className="flex items-center gap-3">
          <span className="hidden sm:inline text-xs text-brandTextSecondary font-bold">
            {user?.email}
          </span>
          <button
            onClick={handleLogout}
            className="p-2 hover:bg-brandRed/10 rounded-xl text-brandTextSecondary hover:text-brandRed transition-colors"
            title="Sign Out"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Main Panel Content */}
      <main className="flex-1 overflow-y-auto pb-20 sm:pb-4 p-4 max-w-lg mx-auto w-full relative z-0">
        <ErrorBoundary componentName="Driver Portal Route Container">
          <Routes>
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="schedules" element={<DriverSchedules />} />
            <Route path="scan" element={<Scan />} />
            <Route path="active" element={<TripScreen />} />
            <Route path="Active" element={<Navigate to="/driver/active" replace />} />
            <Route path="trip" element={<Navigate to="/driver/active" replace />} />
            <Route path="trips" element={<TripsHistory />} />
            <Route path="notifications" element={<Notifications />} />
            <Route path="profile" element={<Profile />} />
            <Route path="*" element={<Navigate to="dashboard" replace />} />
          </Routes>
        </ErrorBoundary>
      </main>

      {/* Bottom Sticky Mobile Navigation Tabs */}
      <nav className="fixed bottom-0 left-0 right-0 h-16 bg-white border-t border-brandBorder flex justify-around items-center px-2 z-30 shadow-lg backdrop-blur-md bg-opacity-95">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.path ||
            (item.path === '/driver/active' && (location.pathname === '/driver/trip' || location.pathname.toLowerCase() === '/driver/active'));
          return (
            <Link
              key={item.name}
              to={item.path}
              className={`flex flex-col items-center justify-center w-12 h-12 rounded-xl transition-all ${
                isActive
                  ? 'text-brandBlue bg-brandBlue/5 border border-brandBlue/10 font-bold'
                  : 'text-brandTextSecondary hover:text-brandTextPrimary'
              }`}
            >
              <Icon className="w-4 h-4" />
              <span className="text-[9px] mt-1 font-bold tracking-tight truncate w-full text-center">
                {item.name.split(' ')[0]}
              </span>
            </Link>
          );
        })}
      </nav>
    </div>
  );
};
