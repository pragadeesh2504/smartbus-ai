import React from 'react';
import { Routes, Route, Link, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  Search as SearchIcon,
  MapPin,
  Bell,
  User as UserIcon,
  LogOut,
  Sparkles
} from 'lucide-react';
import { Dashboard } from './Dashboard';
import { Search } from './Search';
import { LiveMap } from './LiveMap';
import { BusDetails } from './BusDetails';
import { Notifications } from './Notifications';
import { Profile } from './Profile';
import { Schedules } from './Schedules';
import { Calendar } from 'lucide-react';

export const StudentPortal: React.FC = () => {
  const { logout, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { name: 'Home', path: '/student/dashboard', icon: LayoutDashboard },
    { name: 'Schedules', path: '/student/schedules', icon: Calendar },
    { name: 'Search', path: '/student/search', icon: SearchIcon },
    { name: 'Live Map', path: '/student/live-map', icon: MapPin },
    { name: 'Alerts', path: '/student/notifications', icon: Bell },
    { name: 'Profile', path: '/student/profile', icon: UserIcon },
  ];

  return (
    <div className="min-h-screen bg-brandBg text-brandTextPrimary flex flex-col font-sans overflow-hidden">
      {/* Top Header Bar */}
      <header className="h-16 bg-white border-b border-brandBorder flex items-center justify-between px-6 shrink-0 z-10 sticky top-0 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
            <Sparkles className="w-5 h-5 animate-pulse" />
          </div>
          <span className="font-bold tracking-tight text-brandNavy uppercase text-sm">
            SmartBus Student
          </span>
        </div>
        <div className="flex items-center gap-3">
          <span className="hidden sm:inline text-xs text-brandTextSecondary font-bold">
            {user?.email}
          </span>
          <button
            onClick={handleLogout}
            className="p-2 hover:bg-brandRed/10 rounded-xl text-brandTextSecondary hover:text-brandRed transition-colors"
            title="Log Out"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Main Panel Content */}
      <main className="flex-1 overflow-y-auto pb-20 sm:pb-4 p-4 max-w-lg mx-auto w-full relative z-0">
        <Routes>
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="schedules" element={<Schedules />} />
          <Route path="search" element={<Search />} />
          <Route path="live-map" element={<LiveMap />} />
          <Route path="bus/:busId" element={<BusDetails />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="profile" element={<Profile />} />
          <Route path="*" element={<Navigate to="dashboard" replace />} />
        </Routes>
      </main>

      {/* Bottom Sticky Mobile Navigation Tabs */}
      <nav className="fixed bottom-0 left-0 right-0 h-16 bg-white border-t border-brandBorder flex justify-around items-center px-2 z-30 shadow-lg backdrop-blur-md bg-opacity-95">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname.startsWith(item.path);
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
                {item.name}
              </span>
            </Link>
          );
        })}
      </nav>
    </div>
  );
};
