import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAuth } from '../../context/AuthContext';
import {
  Building2,
  RefreshCw,
  Shield,
  LogOut,
  CheckCircle2,
  AlertTriangle,
  Search,
  Bus,
  Users,
  UserCheck
} from 'lucide-react';

interface College {
  id: string;
  name: string;
  collegeCode: string | null;
  status: 'ACTIVE' | 'DISABLED';
  logoUrl?: string;
  contactEmail?: string;
  studentCount?: number;
  driverCount?: number;
  busCount?: number;
  routeCount?: number;
  activeTripCount?: number;
  createdAt: string;
  updatedAt: string;
}

interface PlatformMetrics {
  totalColleges: number;
  activeColleges: number;
  disabledColleges: number;
  totalStudents: number;
  totalDrivers: number;
  totalBuses: number;
}

export const SuperAdminPortal: React.FC = () => {
  const { user, logout } = useAuth();
  const [colleges, setColleges] = useState<College[]>([]);
  const [metrics, setMetrics] = useState<PlatformMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  const fetchData = async () => {
    try {
      setLoading(true);
      setError('');
      const [collegesRes, metricsRes] = await Promise.all([
        axios.get('/api/super-admin/colleges'),
        axios.get('/api/super-admin/metrics').catch(() => null)
      ]);
      setColleges(collegesRes.data.data || []);
      if (metricsRes?.data?.data) {
        setMetrics(metricsRes.data.data);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to fetch colleges.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const filteredColleges = colleges.filter(
    (c) =>
      c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (c.collegeCode && c.collegeCode.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (c.contactEmail && c.contactEmail.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  const totalCount = metrics ? metrics.totalColleges : colleges.length;
  const activeCount = metrics ? metrics.activeColleges : colleges.filter((c) => c.status === 'ACTIVE').length;
  const disabledCount = metrics ? metrics.disabledColleges : totalCount - activeCount;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      {/* Super Admin Top Navigation */}
      <header className="bg-slate-900 text-white border-b border-slate-800 sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-indigo-600 rounded-xl">
              <Shield className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold tracking-tight text-white text-lg">SmartBus AI</span>
                <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 rounded-md">
                  Super Admin
                </span>
              </div>
              <span className="text-xs text-slate-400">Platform Overview & College Directory (Read-Only)</span>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="text-right hidden sm:block">
              <div className="text-xs font-semibold text-slate-200">{user?.name || user?.email}</div>
              <div className="text-[11px] text-indigo-400">Platform Super Administrator</div>
            </div>
            <button
              onClick={logout}
              className="p-2 text-slate-400 hover:text-rose-400 hover:bg-slate-800 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
              <span className="hidden sm:inline">Sign Out</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        {/* Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Colleges</div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-2xl font-black text-slate-900">{totalCount}</span>
              <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                <Building2 className="w-4 h-4" />
              </div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Active Colleges</div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-2xl font-black text-emerald-600">{activeCount}</span>
              <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Inactive Colleges</div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-2xl font-black text-amber-600">{disabledCount}</span>
              <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
                <AlertTriangle className="w-4 h-4" />
              </div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Platform Fleet</div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-2xl font-black text-slate-900">{metrics?.totalBuses ?? 0}</span>
              <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
                <Bus className="w-4 h-4" />
              </div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Drivers</div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-2xl font-black text-slate-900">{metrics?.totalDrivers ?? 0}</span>
              <div className="p-2 bg-cyan-50 text-cyan-600 rounded-lg">
                <UserCheck className="w-4 h-4" />
              </div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Students</div>
            <div className="flex items-center justify-between mt-2">
              <span className="text-2xl font-black text-slate-900">{metrics?.totalStudents ?? 0}</span>
              <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
                <Users className="w-4 h-4" />
              </div>
            </div>
          </div>
        </div>

        {/* Action Header */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="relative w-full sm:w-96">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
            <input
              type="text"
              placeholder="Filter directory by college name, code, contact..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:border-indigo-500 focus:bg-white transition-all"
            />
          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto justify-between sm:justify-end">
            <span className="text-xs text-slate-400 font-medium hidden md:inline">
              Directory view only &bull; Managed by College Admins
            </span>
            <button
              onClick={fetchData}
              className="p-2 text-slate-600 hover:text-slate-900 border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors flex items-center gap-1.5 text-xs font-semibold"
              title="Refresh Directory"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Colleges Table */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          {loading ? (
            <div className="p-12 text-center text-slate-400 text-sm">
              <div className="w-6 h-6 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin mx-auto mb-2" />
              Loading college directory...
            </div>
          ) : error ? (
            <div className="p-8 text-center text-rose-600 text-sm">{error}</div>
          ) : filteredColleges.length === 0 ? (
            <div className="p-12 text-center text-slate-400 text-sm">
              No colleges found matching your search.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 border-b border-slate-200 text-slate-500 font-semibold uppercase tracking-wider">
                  <tr>
                    <th className="px-6 py-3.5">College</th>
                    <th className="px-6 py-3.5">College Code</th>
                    <th className="px-6 py-3.5">Contact Email</th>
                    <th className="px-4 py-3.5 text-center">Buses</th>
                    <th className="px-4 py-3.5 text-center">Drivers</th>
                    <th className="px-4 py-3.5 text-center">Students</th>
                    <th className="px-6 py-3.5">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredColleges.map((college) => (
                    <tr key={college.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-6 py-4">
                        <div className="font-bold text-slate-900 text-sm">{college.name}</div>
                        <div className="text-[11px] text-slate-400">
                          Created: {new Date(college.createdAt).toLocaleDateString()}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        {college.collegeCode ? (
                          <span className="px-2.5 py-1 font-mono font-bold bg-slate-100 text-indigo-700 rounded-lg text-xs border border-slate-200">
                            {college.collegeCode}
                          </span>
                        ) : (
                          <span className="italic text-slate-400 text-xs">Not configured</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-slate-600">
                        {college.contactEmail || '—'}
                      </td>
                      <td className="px-4 py-4 text-center font-semibold text-slate-700">
                        {college.busCount ?? 0}
                      </td>
                      <td className="px-4 py-4 text-center font-semibold text-slate-700">
                        {college.driverCount ?? 0}
                      </td>
                      <td className="px-4 py-4 text-center font-semibold text-slate-700">
                        {college.studentCount ?? 0}
                      </td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-bold ${
                            college.status === 'ACTIVE'
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-rose-50 text-rose-700 border border-rose-200'
                          }`}
                        >
                          {college.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
