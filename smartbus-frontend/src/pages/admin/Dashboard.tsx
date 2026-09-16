import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import {
  Bus,
  UserCheck,
  Map as MapIcon,
  CalendarDays,
  ShieldAlert,
  AlertTriangle,
  History,
  RefreshCw,
  BarChart3
} from 'lucide-react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { FleetAnalytics } from './FleetAnalytics';
import { AdminLiveFleetMap } from './AdminLiveFleetMap';
import { SmartWebSocketClient, ConnectionState } from '../../services/websocketService';
import { ConnectionStatusBadge } from '../../components/ConnectionStatusBadge';

interface AuditLog {
  id: string;
  userEmail: string;
  action: string;
  details: string;
  ipAddress: string;
  createdAt: string;
}

interface DashboardStats {
  totalBuses: number;
  activeBuses: number;
  maintenanceBuses: number;
  totalDrivers: number;
  approvedDrivers: number;
  totalRoutes: number;
  totalStops: number;
  todaySchedules: number;
  activeSchedules: number;
  offlineGps: number;
  recentLogs: AuditLog[];
}

// Custom Bus Icon Creator
const createBusIcon = (busNumber: string, source: string) => {
  const color = source === 'GPS_DEVICE' ? 'bg-brandTeal' : 'bg-brandBlue';
  return L.divIcon({
    html: `<div class="relative flex items-center justify-center">
             <div class="absolute w-8 h-8 ${color} opacity-30 rounded-full animate-ping"></div>
             <div class="w-8 h-8 ${color} border-2 border-white rounded-full flex items-center justify-center text-white text-[9px] font-black shadow-lg">
               ${busNumber.slice(-3)}
             </div>
           </div>`,
    className: 'custom-bus-marker-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16]
  });
};

export const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeBuses, setActiveBuses] = useState<Record<string, any>>({});
  const [viewMode, setViewMode] = useState<'live' | 'analytics'>('live');
  const [wsState, setWsState] = useState<ConnectionState>('DISCONNECTED');
  const [wsRetry, setWsRetry] = useState<number>(0);
  const clientRef = useRef<SmartWebSocketClient | null>(null);

  const fetchStats = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/admin/dashboard');
      if (res.data.success) {
        setStats(res.data.data);
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to fetch dashboard metrics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();

    // SEC-12: Managed WebSocket with exponential backoff & subscription recovery
    const client = new SmartWebSocketClient({
      subscriptions: [{ type: 'SUBSCRIBE_CLIENT' }],
      onStateChange: (state, retry) => {
        setWsState(state);
        setWsRetry(retry);
      },
      onMessage: (data) => {
        try {
          if (data && data.type === 'BUS_LOCATION_UPDATE') {
            setActiveBuses(prev => ({
              ...prev,
              [data.busCode]: {
                ...(prev[data.busCode] || {}),
                busCode: data.busCode,
                busNumber: data.busNumber || data.busCode,
                latitude: data.latitude,
                longitude: data.longitude,
                trackingSource: data.trackingSource,
                lastUpdated: new Date().toLocaleTimeString()
              }
            }));
          } else if (data && data.type === 'ETA_UPDATED') {
            const key = data.busCode || data.busNumber || data.busId;
            setActiveBuses(prev => {
              const existingKey = Object.keys(prev).find(k => prev[k].busNumber === data.busNumber) || key;
              return {
                ...prev,
                [existingKey]: {
                  ...(prev[existingKey] || { busNumber: data.busNumber, busCode: data.busNumber }),
                  eta: data.minutesRemaining,
                  etaStatus: data.etaStatus,
                  offRoute: data.offRoute,
                  gpsStale: data.gpsStale,
                  delayMinutes: data.delayMinutes,
                  routeDeviationMeters: data.routeDeviationMeters,
                  nextStopName: data.nextStopName,
                  lastUpdated: new Date().toLocaleTimeString()
                }
              };
            });
          } else if (data && data.type === 'BUS_OFF_ROUTE') {
            const key = data.busNumber || data.busId;
            setActiveBuses(prev => {
              const existingKey = Object.keys(prev).find(k => prev[k].busNumber === data.busNumber) || key;
              return {
                ...prev,
                [existingKey]: {
                  ...(prev[existingKey] || { busNumber: data.busNumber, busCode: data.busNumber }),
                  offRoute: true,
                  routeDeviationMeters: data.deviationMeters,
                  lastUpdated: new Date().toLocaleTimeString()
                }
              };
            });
          } else if (data && data.type === 'BUS_BACK_ON_ROUTE') {
            const key = data.busNumber || data.busId;
            setActiveBuses(prev => {
              const existingKey = Object.keys(prev).find(k => prev[k].busNumber === data.busNumber) || key;
              return {
                ...prev,
                [existingKey]: {
                  ...(prev[existingKey] || { busNumber: data.busNumber, busCode: data.busNumber }),
                  offRoute: false,
                  lastUpdated: new Date().toLocaleTimeString()
                }
              };
            });
          }
        } catch (err) {
          console.error('Error parsing live WS payload', err);
        }
      }
    });

    clientRef.current = client;
    client.connect();

    return () => {
      client.disconnect();
      clientRef.current = null;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-5 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-center font-semibold text-sm">
        {error}
      </div>
    );
  }

  const kpis = [
    { name: 'Total Buses', value: stats?.totalBuses || 0, sub: `${stats?.activeBuses || 0} moving, ${stats?.maintenanceBuses || 0} maintenance`, icon: Bus, color: 'bg-brandNavy' },
    { name: 'Total Drivers', value: stats?.totalDrivers || 0, sub: `${stats?.approvedDrivers || 0} verified & approved`, icon: UserCheck, color: 'bg-brandBlue' },
    { name: 'Total Routes', value: stats?.totalRoutes || 0, sub: `${stats?.totalStops || 0} active stops configured`, icon: MapIcon, color: 'bg-brandTeal' },
    { name: "Today's Schedules", value: stats?.todaySchedules || 0, sub: `${stats?.activeSchedules || 0} running duties today`, icon: CalendarDays, color: 'bg-brandAmber' },
  ];

  const attentionItems = Object.values(activeBuses).filter(
    (b: any) => b.offRoute || b.etaStatus === 'DELAYED' || b.gpsStale
  );

  return (
    <div className="space-y-6">
      {/* Page Header with Mode Switcher */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-xl font-bold text-brandNavy tracking-tight">Operations Command Center</h2>
            <ConnectionStatusBadge state={wsState} retryAttempt={wsRetry} />
          </div>
          <p className="text-xs text-brandTextSecondary mt-0.5">Real-time status across the campus transit network</p>
        </div>

        <div className="flex items-center gap-2.5">
          <div className="flex items-center bg-white p-1 rounded-xl border border-brandBorder shadow-sm text-xs font-semibold">
            <button
              onClick={() => setViewMode('live')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
                viewMode === 'live'
                  ? 'bg-brandBlue text-white shadow-sm'
                  : 'text-brandTextSecondary hover:text-brandNavy'
              }`}
            >
              <MapIcon className="w-3.5 h-3.5" />
              <span>Live Fleet Map</span>
            </button>
            <button
              onClick={() => setViewMode('analytics')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
                viewMode === 'analytics'
                  ? 'bg-brandBlue text-white shadow-sm'
                  : 'text-brandTextSecondary hover:text-brandNavy'
              }`}
            >
              <BarChart3 className="w-3.5 h-3.5" />
              <span>Fleet Analytics</span>
            </button>
          </div>

          {viewMode === 'live' && (
            <button
              onClick={fetchStats}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-white border border-brandBorder text-brandTextPrimary rounded-xl hover:bg-brandBg hover:text-brandBlue transition-all text-xs font-semibold shadow-sm"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Refresh Stats
            </button>
          )}
        </div>
      </div>

      {viewMode === 'analytics' ? (
        <FleetAnalytics />
      ) : (
        <>
          {/* Real-Time Requires Attention Incident Banner */}
          {attentionItems.length > 0 && (
            <div className="bg-brandRed/10 border border-brandRed/30 rounded-2xl p-4 text-xs shadow-sm">
              <div className="flex items-center gap-2 mb-2 font-bold text-brandRed">
                <AlertTriangle className="w-4 h-4 animate-bounce" />
                <span>Requires Attention ({attentionItems.length} Active Incident{attentionItems.length > 1 ? 's' : ''})</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
                {attentionItems.map((bus: any) => (
                  <div key={bus.busNumber || bus.busCode} className="bg-white p-2.5 rounded-xl border border-brandRed/20 flex items-center justify-between shadow-sm">
                    <div>
                      <span className="font-black text-brandNavy">{bus.busNumber}</span>
                      <p className="text-[10px] text-brandRed font-medium mt-0.5">
                        {bus.offRoute
                          ? `Off route by ${Math.round(bus.routeDeviationMeters || 0)}m`
                          : bus.etaStatus === 'DELAYED'
                          ? `Delayed +${bus.delayMinutes || 5}m (${bus.nextStopName ? `→ ${bus.nextStopName}` : 'in transit'})`
                          : 'Stale GPS Telemetry'}
                      </p>
                    </div>
                    <span className="px-2 py-0.5 rounded text-[9px] font-black uppercase bg-brandRed/15 text-brandRed">
                      {bus.offRoute ? 'DEVIATION' : bus.etaStatus === 'DELAYED' ? 'DELAY' : 'STALE GPS'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* KPI Tiles */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {kpis.map((kpi) => {
          const Icon = kpi.icon;
          return (
            <div key={kpi.name} className="bg-white border border-brandBorder rounded-2xl p-6 shadow-sm relative overflow-hidden transition-all hover:shadow-md hover:border-brandBorder/80">
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider">{kpi.name}</p>
                  <p className="text-3xl font-extrabold text-brandNavy mt-2 tracking-tight">
                    {kpi.value}
                  </p>
                  <p className="text-[11px] text-brandTextSecondary mt-2 font-medium">{kpi.sub}</p>
                </div>
                <div className={`p-2.5 rounded-xl text-white ${kpi.color} shadow-sm`}>
                  <Icon className="w-5 h-5" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Grid: Map Monitor & Critical Warnings */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Real-time Tracking Command Center Map */}
        <div className="lg:col-span-2">
          <AdminLiveFleetMap />
        </div>

        {/* Telemetry Alerts Panel */}
        <div className="bg-white border border-brandBorder rounded-3xl p-5 shadow-sm flex flex-col h-[520px]">
          <h3 className="font-bold text-brandNavy text-sm mb-4">Active Telemetry Alerts</h3>
          <div className="flex-1 overflow-y-auto space-y-3 pr-1">
            {stats?.offlineGps && stats.offlineGps > 0 ? (
              <div className="p-3 bg-brandAmber/10 border border-brandAmber/20 rounded-xl flex gap-3 text-brandAmber">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <div>
                  <p className="text-xs font-bold">Offline Tracking Hardware</p>
                  <p className="text-[10px] text-brandTextSecondary mt-1 font-medium">{stats.offlineGps} configured GPS device(s) are reporting offline. Verify physical hardware power.</p>
                </div>
              </div>
            ) : null}

            {stats?.maintenanceBuses && stats.maintenanceBuses > 0 ? (
              <div className="p-3 bg-brandBlue/10 border border-brandBlue/20 rounded-xl flex gap-3 text-brandBlue">
                <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5" />
                <div>
                  <p className="text-xs font-bold">Depot Maintenance</p>
                  <p className="text-[10px] text-brandTextSecondary mt-1 font-medium">{stats.maintenanceBuses} bus(es) are currently flagged under depot inspection.</p>
                </div>
              </div>
            ) : null}

            {(!stats?.offlineGps && !stats?.maintenanceBuses) && (
              <div className="flex flex-col items-center justify-center h-full text-brandTextSecondary text-center py-6">
                <UserCheck className="w-8 h-8 mb-2 text-brandGreen opacity-70" />
                <p className="text-xs font-bold text-brandNavy">All Fleet Systems Active</p>
                <p className="text-[11px] text-brandTextSecondary mt-1">No hardware interruptions or alerts reported.</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Recent Audit Log Registry */}
      <div className="bg-white border border-brandBorder rounded-2xl p-5 shadow-sm">
        <div className="flex items-center gap-2 mb-4">
          <History className="w-4 h-4 text-brandBlue" />
          <h3 className="font-bold text-brandNavy text-sm">Recent Audit Operations</h3>
        </div>
        <div className="overflow-x-auto rounded-xl border border-brandBorder">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="bg-brandBg text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold border-b border-brandBorder">
                <th className="py-3.5 px-4">Operator</th>
                <th className="py-3.5 px-4">Action</th>
                <th className="py-3.5 px-4">Details</th>
                <th className="py-3.5 px-4">Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {stats?.recentLogs && stats.recentLogs.length > 0 ? (
                stats.recentLogs.map((log) => (
                  <tr key={log.id} className="border-b border-brandBorder last:border-0 hover:bg-brandBlue/5 transition-all text-brandTextPrimary">
                    <td className="py-3 px-4 font-bold text-brandNavy">{log.userEmail}</td>
                    <td className="py-3 px-4">
                      <span className="inline-flex px-2 py-0.5 bg-brandBg border border-brandBorder text-brandTextPrimary rounded-md font-bold text-[10px] uppercase">
                        {log.action}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-brandTextSecondary font-medium">{log.details}</td>
                    <td className="py-3 px-4 text-[10px] font-bold text-brandTextSecondary">
                      {new Date(log.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={4} className="text-center py-8 text-brandTextSecondary font-medium text-xs">
                    No recent administrative transactions registered.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
      </>
      )}
    </div>
  );
};
