import React, { useState } from 'react';
import { Routes, Route, Link, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  Bus,
  UserCheck,
  Map,
  CalendarDays,
  FileSpreadsheet,
  Settings as SettingsIcon,
  LogOut,
  Bell,
  Menu,
  X,
  Shield,
  Sliders,
  BarChart3
} from 'lucide-react';
import { Dashboard } from './Dashboard';
import { FleetAnalytics } from './FleetAnalytics';
import { Buses } from './Buses';
import { Drivers } from './Drivers';
import { RoutesPage } from './Routes';
import { Schedules } from './Schedules';
import { Assignments } from './Assignments';
import { AuditLogs } from './AuditLogs';
import { Settings } from './Settings';

export const AdminPortal: React.FC = () => {
  const { logout, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [notificationsCount] = useState(3);

  const menuItems = [
    { name: 'Dashboard', path: '/admin/dashboard', icon: LayoutDashboard },
    { name: 'Fleet Analytics', path: '/admin/analytics', icon: BarChart3 },
    { name: 'Buses', path: '/admin/buses', icon: Bus },
    { name: 'Drivers', path: '/admin/drivers', icon: UserCheck },
    { name: 'Routes & Stops', path: '/admin/routes', icon: Map },
    { name: 'Schedules', path: '/admin/schedules', icon: CalendarDays },
    { name: 'Assignments', path: '/admin/assignments', icon: Sliders },
    { name: 'Audit Logs', path: '/admin/audit-logs', icon: FileSpreadsheet },
    { name: 'Settings', path: '/admin/settings', icon: SettingsIcon },
  ];

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getHeaderTitle = () => {
    const activeItem = menuItems.find(item => location.pathname.startsWith(item.path));
    return activeItem ? activeItem.name : 'Operations Console';
  };

  return (
    <div className="min-h-screen bg-brandBg text-brandTextPrimary flex font-sans overflow-hidden">
      {/* Sidebar Panel */}
      <aside
        className={`${
          sidebarOpen ? 'w-64' : 'w-20'
        } bg-brandNavy text-white transition-all duration-300 flex flex-col z-20 shrink-0 shadow-lg`}
      >
        {/* Header/Logo */}
        <div className="h-16 flex items-center justify-between px-4 border-b border-white/10 shrink-0">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="p-2 bg-brandBlue/20 border border-brandBlue/30 rounded-xl text-brandBlue">
              <Shield className="w-5 h-5" />
            </div>
            {sidebarOpen && (
              <span className="font-bold text-sm tracking-wider text-white uppercase whitespace-nowrap">
                SmartBus Admin
              </span>
            )}
          </div>
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-1.5 hover:bg-white/10 rounded-lg text-white/70 hover:text-white transition-colors"
          >
            {sidebarOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
          </button>
        </div>

        {/* Navigation Links */}
        <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.name}
                to={item.path}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-xs font-semibold tracking-wide transition-all group ${
                  isActive
                    ? 'bg-brandBlue text-white shadow-md shadow-brandBlue/10'
                    : 'text-white/70 hover:bg-white/5 hover:text-white'
                }`}
              >
                <Icon className={`w-4 h-4 shrink-0 ${isActive ? 'text-white' : 'text-white/60 group-hover:text-white'}`} />
                {sidebarOpen && <span className="truncate">{item.name}</span>}
              </Link>
            );
          })}
        </nav>

        {/* Footer info/Logout */}
        <div className="p-3 border-t border-white/10 shrink-0 bg-black/10">
          {sidebarOpen && (
            <div className="mb-3 px-3 py-2 bg-white/5 rounded-xl border border-white/10">
              <p className="text-xs font-bold text-white truncate">{user?.name || 'Administrator'}</p>
              <p className="text-[10px] text-white/50 truncate font-semibold">{user?.email || 'admin@smartbus.ai'}</p>
            </div>
          )}
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3 py-2 hover:bg-brandRed/10 border border-transparent hover:border-brandRed/20 text-white/70 hover:text-brandRed rounded-xl text-xs font-semibold transition-all"
          >
            <LogOut className="w-4 h-4 shrink-0" />
            {sidebarOpen && <span>Sign Out</span>}
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top Header */}
        <header className="h-16 bg-white border-b border-brandBorder flex items-center justify-between px-6 shrink-0 z-10">
          <div className="flex items-center gap-3">
            <h1 className="font-bold text-lg text-brandNavy tracking-tight">{getHeaderTitle()}</h1>
            {user?.collegeName && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-brandBlue/10 text-brandBlue border border-brandBlue/20">
                {user.collegeName} ({user.collegeCode})
              </span>
            )}
            {user?.role === 'SUPER_ADMIN' && (
              <Link
                to="/super-admin"
                className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-100 text-purple-800 border border-purple-200 hover:bg-purple-200 transition-colors"
              >
                Super Admin Control Plane →
              </Link>
            )}
          </div>

          <div className="flex items-center gap-4">
            {/* Notification trigger */}
            <button className="p-2 hover:bg-brandBg border border-transparent hover:border-brandBorder rounded-xl text-brandTextSecondary hover:text-brandTextPrimary relative transition-all">
              <Bell className="w-4 h-4" />
              {notificationsCount > 0 && (
                <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-brandBlue rounded-full"></span>
              )}
            </button>
            <div className="w-px h-6 bg-brandBorder" />
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-brandBlue flex items-center justify-center text-white font-bold text-xs uppercase shadow-sm">
                {user?.name?.charAt(0) || 'A'}
              </div>
              <span className="text-xs font-bold text-brandTextPrimary hidden sm:inline-block">
                {user?.name || 'Admin'}
              </span>
            </div>
          </div>
        </header>

        {/* Workspace Canvas */}
        <main className="flex-1 overflow-auto bg-brandBg p-6 relative">
          <Routes>
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="analytics" element={<FleetAnalytics />} />
            <Route path="buses" element={<Buses />} />
            <Route path="drivers" element={<Drivers />} />
            <Route path="routes" element={<RoutesPage />} />
            <Route path="schedules" element={<Schedules />} />
            <Route path="assignments" element={<Assignments />} />
            <Route path="audit-logs" element={<AuditLogs />} />
            <Route path="settings" element={<Settings />} />
            <Route path="*" element={<Navigate to="dashboard" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};
