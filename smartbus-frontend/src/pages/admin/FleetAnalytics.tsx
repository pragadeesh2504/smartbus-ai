import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Bus,
  Clock,
  AlertTriangle,
  Radio,
  Download,
  RefreshCw,
  TrendingUp,
  MapPin,
  Calendar,
  X,
  User,
  Compass,
  CheckCircle2,
  ChevronRight,
  Filter,
  BarChart3
} from 'lucide-react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend
} from 'recharts';

interface FleetOverview {
  totalBuses: number;
  activeTrips: number;
  totalPeriodTrips: number;
  completedTrips: number;
  delayedTrips: number;
  onTimePercentage: number;
  averageTripDurationMinutes: number;
  averageDelayMinutes: number;
  routeDeviationCount: number;
  gpsHealthPercentage: number;
  healthyGpsUpdates: number;
  staleGpsEvents: number;
  inactiveBuses?: number;
  liveBuses?: number;
  staleBuses?: number;
  totalDrivers?: number;
  approvedDrivers?: number;
  activeDrivers?: number;
  totalStudents?: number;
  activeStudents?: number;
  tripsToday?: number;
  tripsThisWeek?: number;
  tripsThisMonth?: number;
  cancelledTrips?: number;
  inProgressTrips?: number;
  averageDistanceKm?: number;
  averageSpeedKmh?: number;
}

interface TripTrend {
  date: string;
  totalTrips: number;
  completedTrips: number;
  delayedTrips: number;
  onTimePercentage: number;
}

interface RoutePerformance {
  routeId: string;
  routeName: string;
  totalTrips: number;
  completedTrips: number;
  delayedTrips: number;
  onTimePercentage: number;
  averageDelayMinutes: number;
  averageTripDurationMinutes: number;
  deviationCount: number;
  gpsHealthPercentage: number;
  performanceStatus: 'EXCELLENT' | 'GOOD' | 'ATTENTION' | 'CRITICAL';
}

interface BusPerformance {
  busId: string;
  busNumber: string;
  busCode: string;
  totalTrips: number;
  completedTrips: number;
  delayedTrips: number;
  onTimePercentage: number;
  averageDelayMinutes: number;
  averageTripDurationMinutes: number;
  deviationCount: number;
  gpsStaleCount: number;
  gpsHealthPercentage: number;
  totalDistanceKm: number;
  performanceStatus: 'EXCELLENT' | 'GOOD' | 'ATTENTION' | 'CRITICAL';
}

interface DriverPerformance {
  driverId: string;
  driverName: string;
  employeeId: string;
  assignedTrips: number;
  completedTrips: number;
  delayedTrips: number;
  onTimePercentage: number;
  averageDelayMinutes: number;
  totalActiveDrivingHours: number;
  performanceStatus: 'EXCELLENT' | 'GOOD' | 'ATTENTION' | 'CRITICAL';
}

interface DelayAnalytics {
  totalDelayedTrips: number;
  averageDelayMinutes: number;
  maxDelayMinutes: number;
  delayThresholdMinutes: number;
  dailyDelayTrend: Array<{ date: string; delayedTrips: number; avgDelay: number }>;
  affectedRoutes: Array<{ routeName: string; delayCount: number; avgDelay: number }>;
}

interface TripAnalytics {
  tripId: string;
  busId: string;
  busNumber: string;
  busCode: string;
  driverId: string;
  driverName: string;
  routeId: string;
  routeName: string;
  status: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  scheduledDurationMinutes: number;
  distanceKm: number;
  delayMinutes: number;
  stopCount: number;
  completedStopsCount: number;
  offRoute: boolean;
  gpsStale: boolean;
  stopTimeline: Array<{
    stopId: string;
    stopName: string;
    sequenceNumber: number;
    arrivalTime?: string;
    departureTime?: string;
    completed: boolean;
  }>;
  telemetryTrailSample: Array<{
    lat: number;
    lng: number;
    speed: number;
    time: string;
  }>;
}

const PIE_COLORS = ['#16A34A', '#DC2626'];

export const FleetAnalytics: React.FC = () => {
  const [preset, setPreset] = useState<'today' | 'yesterday' | '7days' | '30days' | 'custom'>('7days');
  const [customFrom, setCustomFrom] = useState(
    new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
  );
  const [customTo, setCustomTo] = useState(new Date().toISOString().split('T')[0]);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const [overview, setOverview] = useState<FleetOverview | null>(null);
  const [trends, setTrends] = useState<TripTrend[]>([]);
  const [routesPerf, setRoutesPerf] = useState<RoutePerformance[]>([]);
  const [busesPerf, setBusesPerf] = useState<BusPerformance[]>([]);
  const [driversPerf, setDriversPerf] = useState<DriverPerformance[]>([]);
  const [delayData, setDelayData] = useState<DelayAnalytics | null>(null);

  const [activeTab, setActiveTab] = useState<'routes' | 'buses' | 'drivers'>('routes');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedTripId, setSelectedTripId] = useState<string | null>(null);
  const [drillDownTrip, setDrillDownTrip] = useState<TripAnalytics | null>(null);
  const [drillDownLoading, setDrillDownLoading] = useState(false);

  // Compute query params based on preset
  const getQueryParams = () => {
    if (preset === 'custom') {
      return { from: customFrom, to: customTo };
    }
    const today = new Date();
    const toStr = today.toISOString().split('T')[0];

    if (preset === 'today') {
      return { from: toStr, to: toStr };
    }
    if (preset === 'yesterday') {
      const y = new Date(Date.now() - 24 * 60 * 60 * 1000);
      const yStr = y.toISOString().split('T')[0];
      return { from: yStr, to: yStr };
    }
    if (preset === '7days') {
      const past = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
      return { from: past.toISOString().split('T')[0], to: toStr };
    }
    if (preset === '30days') {
      const past = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
      return { from: past.toISOString().split('T')[0], to: toStr };
    }
    return { from: toStr, to: toStr };
  };

  const fetchAnalyticsData = async (isManualRefresh = false) => {
    try {
      if (isManualRefresh) setRefreshing(true);
      else setLoading(true);
      setError('');

      const params = getQueryParams();

      const [ovRes, trRes, rtRes, bsRes, drRes, dlRes] = await Promise.all([
        axios.get('/api/admin/analytics/overview', { params }),
        axios.get('/api/admin/analytics/trends', { params }),
        axios.get('/api/admin/analytics/routes', { params }),
        axios.get('/api/admin/analytics/buses', { params }),
        axios.get('/api/admin/analytics/drivers', { params }),
        axios.get('/api/admin/analytics/delays', { params })
      ]);

      if (ovRes.data.success) setOverview(ovRes.data.data);
      if (trRes.data.success) setTrends(trRes.data.data);
      if (rtRes.data.success) setRoutesPerf(rtRes.data.data);
      if (bsRes.data.success) setBusesPerf(bsRes.data.data);
      if (drRes.data.success) setDriversPerf(drRes.data.data);
      if (dlRes.data.success) setDelayData(dlRes.data.data);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to fetch fleet analytics data.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchAnalyticsData();
  }, [preset, customFrom, customTo]);

  const handleExportCsv = async (type: 'routes' | 'buses' | 'drivers' | 'trips') => {
    try {
      const params = { ...getQueryParams(), type };
      const response = await axios.get('/api/admin/analytics/export', {
        params,
        responseType: 'blob'
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `smartbus_${type}_analytics_${params.from}_${params.to}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error('Failed to export CSV', err);
      alert('Failed to download analytics CSV.');
    }
  };

  const handleOpenTripDrillDown = async (tripId: string) => {
    setSelectedTripId(tripId);
    setDrillDownLoading(true);
    try {
      const res = await axios.get(`/api/admin/analytics/trips/${tripId}`);
      if (res.data.success) {
        setDrillDownTrip(res.data.data);
      }
    } catch (err) {
      console.error('Failed to fetch trip drill-down', err);
    } finally {
      setDrillDownLoading(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'EXCELLENT':
        return <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-brandGreen/10 text-brandGreen border border-brandGreen/20">EXCELLENT</span>;
      case 'GOOD':
        return <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-brandBlue/10 text-brandBlue border border-brandBlue/20">GOOD</span>;
      case 'ATTENTION':
        return <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-brandAmber/10 text-brandAmber border border-brandAmber/20">ATTENTION</span>;
      case 'CRITICAL':
        return <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-brandRed/10 text-brandRed border border-brandRed/20">CRITICAL</span>;
      default:
        return <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-gray-100 text-gray-700">NEUTRAL</span>;
    }
  };

  // Filtered lists for tables
  const filteredRoutes = routesPerf.filter(r =>
    r.routeName.toLowerCase().includes(searchTerm.toLowerCase())
  );
  const filteredBuses = busesPerf.filter(b =>
    b.busNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
    b.busCode.toLowerCase().includes(searchTerm.toLowerCase())
  );
  const filteredDrivers = driversPerf.filter(d =>
    d.driverName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    d.employeeId.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const pieData = overview ? [
    { name: 'On-Time', value: Math.max(0, overview.totalPeriodTrips - overview.delayedTrips) },
    { name: 'Delayed', value: overview.delayedTrips }
  ] : [];

  return (
    <div className="space-y-6">
      {/* Header & Controls Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 bg-white p-4 rounded-2xl border border-brandBorder shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-brandBlue" />
            <h2 className="text-lg font-bold text-brandNavy">Fleet Analytics & Intelligence</h2>
          </div>
          <p className="text-xs text-brandTextSecondary mt-0.5">
            Operational metrics, schedules adherence, route reliability and GPS integrity
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Preset Buttons */}
          <div className="flex items-center bg-brandBg p-1 rounded-xl border border-brandBorder text-xs">
            {(['today', 'yesterday', '7days', '30days', 'custom'] as const).map((p) => (
              <button
                key={p}
                onClick={() => setPreset(p)}
                className={`px-2.5 py-1.5 rounded-lg font-semibold capitalize transition-all ${
                  preset === p
                    ? 'bg-brandBlue text-white shadow-sm'
                    : 'text-brandTextSecondary hover:text-brandNavy'
                }`}
              >
                {p === '7days' ? 'Last 7D' : p === '30days' ? 'Last 30D' : p}
              </button>
            ))}
          </div>

          {/* Custom Date Pickers */}
          {preset === 'custom' && (
            <div className="flex items-center gap-1.5 bg-brandBg px-2.5 py-1 rounded-xl border border-brandBorder text-xs">
              <Calendar className="w-3.5 h-3.5 text-brandTextSecondary" />
              <input
                type="date"
                value={customFrom}
                onChange={(e) => setCustomFrom(e.target.value)}
                className="bg-transparent border-none text-xs font-semibold text-brandNavy focus:outline-none"
              />
              <span className="text-brandTextSecondary">to</span>
              <input
                type="date"
                value={customTo}
                onChange={(e) => setCustomTo(e.target.value)}
                className="bg-transparent border-none text-xs font-semibold text-brandNavy focus:outline-none"
              />
            </div>
          )}

          {/* Refresh Action */}
          <button
            onClick={() => fetchAnalyticsData(true)}
            disabled={refreshing}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white border border-brandBorder text-brandTextPrimary rounded-xl hover:bg-brandBg hover:text-brandBlue transition-all text-xs font-semibold shadow-sm disabled:opacity-50"
            title="Refresh analytics data"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-brandBlue' : ''}`} />
            <span>Refresh</span>
          </button>

          {/* Export CSV Dropdown */}
          <div className="relative group">
            <button className="inline-flex items-center gap-1.5 px-3 py-2 bg-brandBlue text-white rounded-xl hover:bg-brandBlue/90 transition-all text-xs font-semibold shadow-sm">
              <Download className="w-3.5 h-3.5" />
              <span>Export CSV</span>
            </button>
            <div className="absolute right-0 mt-1 w-44 bg-white border border-brandBorder rounded-xl shadow-xl py-1 z-30 hidden group-hover:block">
              <button
                onClick={() => handleExportCsv('routes')}
                className="w-full text-left px-3.5 py-2 text-xs text-brandTextPrimary hover:bg-brandBg hover:text-brandBlue font-medium transition-colors"
              >
                Routes Adherence CSV
              </button>
              <button
                onClick={() => handleExportCsv('buses')}
                className="w-full text-left px-3.5 py-2 text-xs text-brandTextPrimary hover:bg-brandBg hover:text-brandBlue font-medium transition-colors"
              >
                Fleet Buses CSV
              </button>
              <button
                onClick={() => handleExportCsv('drivers')}
                className="w-full text-left px-3.5 py-2 text-xs text-brandTextPrimary hover:bg-brandBg hover:text-brandBlue font-medium transition-colors"
              >
                Driver Performance CSV
              </button>
              <button
                onClick={() => handleExportCsv('trips')}
                className="w-full text-left px-3.5 py-2 text-xs text-brandTextPrimary hover:bg-brandBg hover:text-brandBlue font-medium transition-colors border-t border-brandBorder/50"
              >
                Period Trips History CSV
              </button>
            </div>
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs font-semibold text-center">
          {error}
        </div>
      )}

      {/* 7 Phase 6 KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7 gap-4">
        {/* KPI 1: Total Buses */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">Total Fleet</span>
            <div className="p-2 rounded-xl bg-brandNavy text-white"><Bus className="w-3.5 h-3.5" /></div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.totalBuses ?? 0}</p>
          <p className="text-[10px] text-brandTextSecondary mt-1 font-medium">Registered Vehicles</p>
        </div>

        {/* KPI 2: Active Trips */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">Active Moving</span>
            <div className="p-2 rounded-xl bg-brandTeal text-white"><Compass className="w-3.5 h-3.5 animate-spin" /></div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.activeTrips ?? 0}</p>
          <p className="text-[10px] text-brandTeal mt-1 font-semibold">Live on Campus</p>
        </div>

        {/* KPI 3: Total Period Trips */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">Period Trips</span>
            <div className="p-2 rounded-xl bg-brandBlue text-white"><TrendingUp className="w-3.5 h-3.5" /></div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.totalPeriodTrips ?? 0}</p>
          <p className="text-[10px] text-brandTextSecondary mt-1 font-medium">{overview?.completedTrips ?? 0} Completed</p>
        </div>

        {/* KPI 4: On-Time Performance % */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">On-Time Rate</span>
            <div className={`p-2 rounded-xl text-white ${
              (overview?.onTimePercentage ?? 100) >= 90 ? 'bg-brandGreen' : (overview?.onTimePercentage ?? 100) >= 75 ? 'bg-brandAmber' : 'bg-brandRed'
            }`}>
              <CheckCircle2 className="w-3.5 h-3.5" />
            </div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.onTimePercentage ?? 100}%</p>
          <p className="text-[10px] text-brandGreen font-medium">Target: ≥ 90%</p>
        </div>

        {/* KPI 5: Delayed Trips */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">Delayed Trips</span>
            <div className="p-2 rounded-xl bg-brandAmber text-white"><Clock className="w-3.5 h-3.5" /></div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.delayedTrips ?? 0}</p>
          <p className="text-[10px] text-brandAmber mt-1 font-semibold">Avg +{overview?.averageDelayMinutes ?? 0}m delay</p>
        </div>

        {/* KPI 6: Route Deviations */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">Route Deviations</span>
            <div className="p-2 rounded-xl bg-brandRed text-white"><AlertTriangle className="w-3.5 h-3.5" /></div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.routeDeviationCount ?? 0}</p>
          <p className="text-[10px] text-brandRed mt-1 font-medium">&gt; 500m geofence off</p>
        </div>

        {/* KPI 7: GPS Health */}
        <div className="bg-white border border-brandBorder rounded-2xl p-4 shadow-sm hover:shadow-md transition-all">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase tracking-wider text-brandTextSecondary">GPS Integrity</span>
            <div className="p-2 rounded-xl bg-brandTeal text-white"><Radio className="w-3.5 h-3.5" /></div>
          </div>
          <p className="text-2xl font-black text-brandNavy mt-2">{overview?.gpsHealthPercentage ?? 98.5}%</p>
          <p className="text-[10px] text-brandTeal mt-1 font-medium">{overview?.staleGpsEvents ?? 0} stale warnings</p>
        </div>
      </div>

      {/* Fleet & Personnel Operational Aggregation Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-slate-900 text-white rounded-2xl p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase text-slate-400">Drivers Pool</span>
            <User className="w-3.5 h-3.5 text-emerald-400" />
          </div>
          <p className="text-xl font-black text-white mt-1.5">{overview?.totalDrivers ?? 0}</p>
          <p className="text-[10px] text-slate-400 mt-0.5 font-medium">
            {overview?.approvedDrivers ?? 0} approved • {overview?.activeDrivers ?? 0} active
          </p>
        </div>

        <div className="bg-slate-900 text-white rounded-2xl p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase text-slate-400">Enrolled Students</span>
            <User className="w-3.5 h-3.5 text-blue-400" />
          </div>
          <p className="text-xl font-black text-white mt-1.5">{overview?.totalStudents ?? 0}</p>
          <p className="text-[10px] text-slate-400 mt-0.5 font-medium">
            {overview?.activeStudents ?? 0} active commuters
          </p>
        </div>

        <div className="bg-slate-900 text-white rounded-2xl p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase text-slate-400">Avg Trip Distance</span>
            <MapPin className="w-3.5 h-3.5 text-amber-400" />
          </div>
          <p className="text-xl font-black text-white mt-1.5">{(overview?.averageDistanceKm ?? 0).toFixed(1)} km</p>
          <p className="text-[10px] text-slate-400 mt-0.5 font-medium">
            Per scheduled journey
          </p>
        </div>

        <div className="bg-slate-900 text-white rounded-2xl p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase text-slate-400">Avg Fleet Speed</span>
            <Compass className="w-3.5 h-3.5 text-indigo-400" />
          </div>
          <p className="text-xl font-black text-white mt-1.5">{(overview?.averageSpeedKmh ?? 0).toFixed(0)} km/h</p>
          <p className="text-[10px] text-slate-400 mt-0.5 font-medium">
            Authoritative GPS telemetry
          </p>
        </div>
      </div>

      {/* Visual Analytics Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Trend Chart: Daily Trips Volume */}
        <div className="lg:col-span-2 bg-white border border-brandBorder rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-bold text-brandNavy text-sm">Trip Volumes & Adherence Trend</h3>
              <p className="text-[11px] text-brandTextSecondary">Total trips scheduled vs completed vs delayed</p>
            </div>
            <span className="text-[10px] font-bold uppercase text-brandBlue bg-brandBlue/10 px-2.5 py-1 rounded-full">
              Timeline
            </span>
          </div>

          <div className="h-[250px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trends} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="totalGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563EB" stopOpacity={0.4}/>
                    <stop offset="95%" stopColor="#2563EB" stopOpacity={0.0}/>
                  </linearGradient>
                  <linearGradient id="completedGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#16A34A" stopOpacity={0.4}/>
                    <stop offset="95%" stopColor="#16A34A" stopOpacity={0.0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" vertical={false} />
                <XAxis dataKey="date" stroke="#94A3B8" fontSize={10} tickLine={false} />
                <YAxis stroke="#94A3B8" fontSize={10} tickLine={false} allowDecimals={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0B1F3A', borderRadius: '12px', border: 'none', color: '#fff', fontSize: '11px' }}
                />
                <Area type="monotone" dataKey="totalTrips" stroke="#2563EB" strokeWidth={2} fillOpacity={1} fill="url(#totalGrad)" name="Total Trips" />
                <Area type="monotone" dataKey="completedTrips" stroke="#16A34A" strokeWidth={2} fillOpacity={1} fill="url(#completedGrad)" name="Completed" />
                <Area type="monotone" dataKey="delayedTrips" stroke="#DC2626" strokeWidth={1.5} fill="#DC2626" fillOpacity={0.2} name="Delayed" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* On-Time Ratio Donut Chart */}
        <div className="bg-white border border-brandBorder rounded-2xl p-5 shadow-sm flex flex-col">
          <div className="flex items-center justify-between mb-2">
            <div>
              <h3 className="font-bold text-brandNavy text-sm">On-Time Distribution</h3>
              <p className="text-[11px] text-brandTextSecondary">Adherence vs Delay breakdown</p>
            </div>
            <span className="text-[10px] font-bold text-brandGreen bg-brandGreen/10 px-2 py-0.5 rounded-md">
              {overview?.onTimePercentage ?? 100}%
            </span>
          </div>

          <div className="h-[200px] w-full flex items-center justify-center">
            {pieData.length > 0 && (pieData[0].value > 0 || pieData[1].value > 0) ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={75}
                    paddingAngle={4}
                    dataKey="value"
                  >
                    {pieData.map((_entry, index) => (
                      <Cell key={`cell-${index}`} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0B1F3A', borderRadius: '10px', border: 'none', color: '#fff', fontSize: '11px' }}
                  />
                  <Legend verticalAlign="bottom" height={36} iconType="circle" wrapperStyle={{ fontSize: '11px' }} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="text-center text-xs text-brandTextSecondary italic py-10">
                No trip data recorded in this period.
              </div>
            )}
          </div>

          <div className="mt-auto pt-3 border-t border-brandBorder flex justify-around text-center text-xs">
            <div>
              <p className="text-[10px] text-brandTextSecondary font-bold">AVG DURATION</p>
              <p className="font-extrabold text-brandNavy mt-0.5">{overview?.averageTripDurationMinutes ?? 0}m</p>
            </div>
            <div className="w-px bg-brandBorder" />
            <div>
              <p className="text-[10px] text-brandTextSecondary font-bold">AVG DELAY</p>
              <p className="font-extrabold text-brandAmber mt-0.5">{overview?.averageDelayMinutes ?? 0}m</p>
            </div>
          </div>
        </div>
      </div>

      {/* Top Delayed Routes & Daily Delay Chart */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Daily Delay Trend */}
        <div className="bg-white border border-brandBorder rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-bold text-brandNavy text-sm">Average Delay Trend</h3>
              <p className="text-[11px] text-brandTextSecondary">Daily minutes of deviation from timetable</p>
            </div>
            <Clock className="w-4 h-4 text-brandAmber" />
          </div>

          <div className="h-[220px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={delayData?.dailyDelayTrend || []} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" vertical={false} />
                <XAxis dataKey="date" stroke="#94A3B8" fontSize={10} tickLine={false} />
                <YAxis stroke="#94A3B8" fontSize={10} tickLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0B1F3A', borderRadius: '12px', border: 'none', color: '#fff', fontSize: '11px' }}
                />
                <Line type="monotone" dataKey="avgDelay" stroke="#F59E0B" strokeWidth={2.5} dot={{ r: 3, fill: '#F59E0B' }} name="Avg Delay (min)" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Top Delayed Routes Bar Chart */}
        <div className="bg-white border border-brandBorder rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-bold text-brandNavy text-sm">Routes with Highest Delays</h3>
              <p className="text-[11px] text-brandTextSecondary">Routes experiencing most frequent timetable lags</p>
            </div>
            <AlertTriangle className="w-4 h-4 text-brandRed" />
          </div>

          <div className="h-[220px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={delayData?.affectedRoutes?.slice(0, 5) || []}
                layout="vertical"
                margin={{ top: 5, right: 10, left: 10, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" horizontal={false} />
                <XAxis type="number" stroke="#94A3B8" fontSize={10} />
                <YAxis type="category" dataKey="routeName" stroke="#94A3B8" fontSize={10} width={100} tickLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0B1F3A', borderRadius: '12px', border: 'none', color: '#fff', fontSize: '11px' }}
                />
                <Bar dataKey="delayCount" fill="#DC2626" radius={[0, 6, 6, 0]} name="Delay Incidents" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Detailed Performance Tables with Tab Navigation */}
      <div className="bg-white border border-brandBorder rounded-2xl p-5 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-4 border-b border-brandBorder">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('routes')}
              className={`px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'routes'
                  ? 'bg-brandBlue text-white shadow-sm'
                  : 'bg-brandBg text-brandTextSecondary hover:text-brandNavy'
              }`}
            >
              Route Adherence ({routesPerf.length})
            </button>
            <button
              onClick={() => setActiveTab('buses')}
              className={`px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'buses'
                  ? 'bg-brandBlue text-white shadow-sm'
                  : 'bg-brandBg text-brandTextSecondary hover:text-brandNavy'
              }`}
            >
              Bus Performance ({busesPerf.length})
            </button>
            <button
              onClick={() => setActiveTab('drivers')}
              className={`px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'drivers'
                  ? 'bg-brandBlue text-white shadow-sm'
                  : 'bg-brandBg text-brandTextSecondary hover:text-brandNavy'
              }`}
            >
              Driver Scorecard ({driversPerf.length})
            </button>
          </div>

          <div className="flex items-center gap-2">
            <div className="relative">
              <Filter className="w-3.5 h-3.5 text-brandTextSecondary absolute left-3 top-2.5" />
              <input
                type="text"
                placeholder={`Search ${activeTab}...`}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-8 pr-3 py-1.5 bg-brandBg border border-brandBorder rounded-xl text-xs text-brandNavy placeholder-brandTextSecondary focus:outline-none focus:border-brandBlue w-48"
              />
            </div>
          </div>
        </div>

        {/* Tab 1: Route Performance Table */}
        {activeTab === 'routes' && (
          <div className="overflow-x-auto mt-4">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="bg-brandBg text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold border-b border-brandBorder">
                  <th className="py-3 px-4">Route Name</th>
                  <th className="py-3 px-4">Total Trips</th>
                  <th className="py-3 px-4">Completed</th>
                  <th className="py-3 px-4">Delayed</th>
                  <th className="py-3 px-4">On-Time %</th>
                  <th className="py-3 px-4">Avg Delay</th>
                  <th className="py-3 px-4">Avg Duration</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredRoutes.length > 0 ? (
                  filteredRoutes.map((r) => (
                    <tr key={r.routeId} className="border-b border-brandBorder last:border-0 hover:bg-brandBg/60 transition-colors">
                      <td className="py-3 px-4 font-bold text-brandNavy">{r.routeName}</td>
                      <td className="py-3 px-4 font-semibold">{r.totalTrips}</td>
                      <td className="py-3 px-4 text-brandGreen font-semibold">{r.completedTrips}</td>
                      <td className="py-3 px-4 text-brandAmber font-semibold">{r.delayedTrips}</td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <span className="font-black text-brandNavy">{r.onTimePercentage}%</span>
                          <div className="w-16 bg-gray-200 h-1.5 rounded-full overflow-hidden">
                            <div
                              className={`h-full ${r.onTimePercentage >= 90 ? 'bg-brandGreen' : r.onTimePercentage >= 75 ? 'bg-brandAmber' : 'bg-brandRed'}`}
                              style={{ width: `${r.onTimePercentage}%` }}
                            />
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-4 font-medium">{r.averageDelayMinutes} mins</td>
                      <td className="py-3 px-4 font-medium">{r.averageTripDurationMinutes} mins</td>
                      <td className="py-3 px-4">{getStatusBadge(r.performanceStatus)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={8} className="py-8 text-center text-brandTextSecondary italic">
                      No matching route performance records found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Tab 2: Bus Performance Table */}
        {activeTab === 'buses' && (
          <div className="overflow-x-auto mt-4">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="bg-brandBg text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold border-b border-brandBorder">
                  <th className="py-3 px-4">Bus</th>
                  <th className="py-3 px-4">Code</th>
                  <th className="py-3 px-4">Total Trips</th>
                  <th className="py-3 px-4">Completed</th>
                  <th className="py-3 px-4">Delayed</th>
                  <th className="py-3 px-4">On-Time %</th>
                  <th className="py-3 px-4">Avg Delay</th>
                  <th className="py-3 px-4">Distance (km)</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredBuses.length > 0 ? (
                  filteredBuses.map((b) => (
                    <tr key={b.busId} className="border-b border-brandBorder last:border-0 hover:bg-brandBg/60 transition-colors">
                      <td className="py-3 px-4 font-bold text-brandNavy flex items-center gap-1.5">
                        <Bus className="w-3.5 h-3.5 text-brandBlue" />
                        {b.busNumber}
                      </td>
                      <td className="py-3 px-4 font-semibold text-brandTextSecondary">{b.busCode || '—'}</td>
                      <td className="py-3 px-4 font-semibold">{b.totalTrips}</td>
                      <td className="py-3 px-4 text-brandGreen font-semibold">{b.completedTrips}</td>
                      <td className="py-3 px-4 text-brandAmber font-semibold">{b.delayedTrips}</td>
                      <td className="py-3 px-4 font-black text-brandNavy">{b.onTimePercentage}%</td>
                      <td className="py-3 px-4 font-medium">{b.averageDelayMinutes} mins</td>
                      <td className="py-3 px-4 font-medium">{b.totalDistanceKm} km</td>
                      <td className="py-3 px-4">{getStatusBadge(b.performanceStatus)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={9} className="py-8 text-center text-brandTextSecondary italic">
                      No matching bus records found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Tab 3: Driver Performance Table */}
        {activeTab === 'drivers' && (
          <div className="overflow-x-auto mt-4">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="bg-brandBg text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold border-b border-brandBorder">
                  <th className="py-3 px-4">Driver Name</th>
                  <th className="py-3 px-4">Employee ID</th>
                  <th className="py-3 px-4">Assigned Trips</th>
                  <th className="py-3 px-4">Completed</th>
                  <th className="py-3 px-4">Delayed</th>
                  <th className="py-3 px-4">On-Time %</th>
                  <th className="py-3 px-4">Avg Delay</th>
                  <th className="py-3 px-4">Driving Hours</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredDrivers.length > 0 ? (
                  filteredDrivers.map((d) => (
                    <tr key={d.driverId} className="border-b border-brandBorder last:border-0 hover:bg-brandBg/60 transition-colors">
                      <td className="py-3 px-4 font-bold text-brandNavy flex items-center gap-1.5">
                        <User className="w-3.5 h-3.5 text-brandTeal" />
                        {d.driverName}
                      </td>
                      <td className="py-3 px-4 font-semibold text-brandTextSecondary">{d.employeeId || '—'}</td>
                      <td className="py-3 px-4 font-semibold">{d.assignedTrips}</td>
                      <td className="py-3 px-4 text-brandGreen font-semibold">{d.completedTrips}</td>
                      <td className="py-3 px-4 text-brandAmber font-semibold">{d.delayedTrips}</td>
                      <td className="py-3 px-4 font-black text-brandNavy">{d.onTimePercentage}%</td>
                      <td className="py-3 px-4 font-medium">{d.averageDelayMinutes} mins</td>
                      <td className="py-3 px-4 font-medium">{d.totalActiveDrivingHours} hrs</td>
                      <td className="py-3 px-4">{getStatusBadge(d.performanceStatus)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={9} className="py-8 text-center text-brandTextSecondary italic">
                      No matching driver records found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Trip Drill-Down Modal */}
      {selectedTripId && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-2xl w-full p-6 shadow-2xl max-h-[85vh] overflow-y-auto relative animate-in fade-in zoom-in duration-200">
            <button
              onClick={() => { setSelectedTripId(null); setDrillDownTrip(null); }}
              className="absolute top-4 right-4 p-2 rounded-xl text-brandTextSecondary hover:bg-brandBg hover:text-brandNavy transition-colors"
            >
              <X className="w-4 h-4" />
            </button>

            {drillDownLoading ? (
              <div className="py-16 text-center">
                <RefreshCw className="w-6 h-6 animate-spin text-brandBlue mx-auto mb-2" />
                <p className="text-xs text-brandTextSecondary font-medium">Loading trip timeline & telemetry audit...</p>
              </div>
            ) : drillDownTrip ? (
              <div className="space-y-4">
                <div className="flex items-center gap-2 border-b border-brandBorder pb-3">
                  <Bus className="w-5 h-5 text-brandBlue" />
                  <div>
                    <h3 className="font-black text-brandNavy text-base">Trip Details: {drillDownTrip.busNumber}</h3>
                    <p className="text-xs text-brandTextSecondary">Route: {drillDownTrip.routeName} • Driver: {drillDownTrip.driverName}</p>
                  </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-brandBg p-3 rounded-xl text-xs">
                  <div>
                    <span className="text-[10px] text-brandTextSecondary uppercase font-bold">Status</span>
                    <p className="font-bold text-brandNavy mt-0.5">{drillDownTrip.status}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-brandTextSecondary uppercase font-bold">Distance</span>
                    <p className="font-bold text-brandNavy mt-0.5">{drillDownTrip.distanceKm} km</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-brandTextSecondary uppercase font-bold">Duration</span>
                    <p className="font-bold text-brandNavy mt-0.5">{drillDownTrip.durationMinutes ?? '—'} mins</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-brandTextSecondary uppercase font-bold">Adherence</span>
                    <p className={`font-bold mt-0.5 ${(drillDownTrip.delayMinutes || 0) > 5 ? 'text-brandRed' : 'text-brandGreen'}`}>
                      {(drillDownTrip.delayMinutes || 0) > 0 ? `+${drillDownTrip.delayMinutes}m delay` : 'On-Time'}
                    </p>
                  </div>
                </div>

                {/* Stop Timeline */}
                <div>
                  <h4 className="text-xs font-bold text-brandNavy mb-2 flex items-center gap-1.5">
                    <MapPin className="w-3.5 h-3.5 text-brandTeal" />
                    Stop-by-Stop Progress Timeline
                  </h4>
                  <div className="space-y-2 border border-brandBorder rounded-xl p-3 bg-white max-h-48 overflow-y-auto">
                    {drillDownTrip.stopTimeline && drillDownTrip.stopTimeline.length > 0 ? (
                      drillDownTrip.stopTimeline.map((s, idx) => (
                        <div key={s.stopId || idx} className="flex items-center justify-between text-xs py-1.5 border-b border-brandBorder/60 last:border-0">
                          <div className="flex items-center gap-2">
                            <span className={`w-2 h-2 rounded-full ${s.completed ? 'bg-brandGreen' : 'bg-gray-300'}`} />
                            <span className="font-bold text-brandNavy">{s.sequenceNumber}. {s.stopName}</span>
                          </div>
                          <span className={`text-[10px] font-semibold ${s.completed ? 'text-brandGreen' : 'text-brandTextSecondary'}`}>
                            {s.completed ? 'Passed / Reached' : 'Pending'}
                          </span>
                        </div>
                      ))
                    ) : (
                      <p className="text-xs text-brandTextSecondary italic py-2 text-center">No stop checkpoints configured.</p>
                    )}
                  </div>
                </div>

                {/* Telemetry Trail Sample */}
                <div>
                  <h4 className="text-xs font-bold text-brandNavy mb-2 flex items-center gap-1.5">
                    <Radio className="w-3.5 h-3.5 text-brandBlue" />
                    Telemetry Trail Audit ({drillDownTrip.telemetryTrailSample?.length || 0} sample points)
                  </h4>
                  <div className="border border-brandBorder rounded-xl overflow-hidden max-h-36 overflow-y-auto text-[11px]">
                    <table className="w-full text-left">
                      <thead className="bg-brandBg text-brandTextSecondary text-[9px] uppercase font-bold sticky top-0">
                        <tr>
                          <th className="py-2 px-3">Time</th>
                          <th className="py-2 px-3">Coordinates</th>
                          <th className="py-2 px-3">Speed (km/h)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {drillDownTrip.telemetryTrailSample?.map((pt, idx) => (
                          <tr key={idx} className="border-b border-brandBorder/50 last:border-0">
                            <td className="py-1.5 px-3 font-mono">{pt.time ? new Date(pt.time).toLocaleTimeString() : '—'}</td>
                            <td className="py-1.5 px-3 font-mono">{pt.lat.toFixed(4)}, {pt.lng.toFixed(4)}</td>
                            <td className="py-1.5 px-3 font-bold">{pt.speed.toFixed(1)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-xs text-brandRed font-semibold py-8 text-center">Failed to load trip analytics.</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
