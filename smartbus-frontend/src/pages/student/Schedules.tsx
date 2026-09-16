import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Calendar,
  Bus,
  MapPin,
  Clock,
  ChevronRight,
  Navigation,
  Sparkles,
  AlertTriangle,
  Radio
} from 'lucide-react';

interface StopInfo {
  stopId: string;
  stopName: string;
  sequence: number;
  arrivalTime?: string;
  departureTime?: string;
}

interface ScheduleItem {
  scheduleId: string;
  busId: string;
  busNumber: string;
  busCode: string;
  routeId: string;
  routeName: string;
  startPoint: string;
  endPoint: string;
  departureTime: string;
  arrivalTime: string;
  estimatedDurationMins?: number;
  daysOfWeek: string;
  scheduleStatus: string;
  tripStatus: string; // LIVE, PAUSED, SCHEDULED, COMPLETED
  activeTripId?: string;
  driverName?: string;
  stops: StopInfo[];
}

export const Schedules: React.FC = () => {
  const navigate = useNavigate();
  const [schedules, setSchedules] = useState<ScheduleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filterRoute, setFilterRoute] = useState('ALL');

  const fetchSchedules = async () => {
    try {
      const res = await axios.get('/api/student/schedules');
      setSchedules(res.data || []);
      setError('');
    } catch (err: any) {
      console.error('Failed to load student schedules', err);
      setError('Unable to load available schedules right now.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSchedules();
  }, []);

  const uniqueRoutes = Array.from(new Set(schedules.map(s => s.routeName)));

  const filtered = schedules.filter(s => {
    if (filterRoute === 'ALL') return true;
    return s.routeName === filterRoute;
  });

  const getStatusBadge = (status: string) => {
    if (status === 'LIVE') {
      return (
        <span className="flex items-center gap-1 bg-brandGreen/15 border border-brandGreen/30 text-brandGreen text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase">
          <span className="w-1.5 h-1.5 bg-brandGreen rounded-full animate-pulse"></span>
          ?? LIVE
        </span>
      );
    }
    if (status === 'PAUSED') {
      return (
        <span className="flex items-center gap-1 bg-brandAmber/15 border border-brandAmber/30 text-brandAmber text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase">
          <span className="w-1.5 h-1.5 bg-brandAmber rounded-full"></span>
          ?? PAUSED
        </span>
      );
    }
    if (status === 'COMPLETED') {
      return (
        <span className="flex items-center gap-1 bg-slate-100 border border-slate-200 text-slate-600 text-[10px] font-bold px-2.5 py-0.5 rounded-full">
          Trip Completed
        </span>
      );
    }
    return (
      <span className="flex items-center gap-1 bg-brandAmber/10 border border-brandAmber/20 text-brandAmber text-[10px] font-bold px-2.5 py-0.5 rounded-full">
        Trip not started
      </span>
    );
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3 text-brandTextSecondary">
        <div className="w-8 h-8 border-4 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
        <p className="text-xs font-bold">Loading schedules...</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 pb-6">
      {/* Page Title */}
      <div>
        <h1 className="text-xl font-bold tracking-tight text-brandNavy flex items-center gap-2">
          <Calendar className="w-5 h-5 text-brandBlue animate-pulse" /> My Schedules
        </h1>
        <p className="text-xs text-brandTextSecondary mt-1 font-medium">
          Authoritative university bus schedules and route timings.
        </p>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold">
          {error}
        </div>
      )}

      {/* Route Filter Tabs if multiple routes */}
      {uniqueRoutes.length > 1 && (
        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
          <button
            onClick={() => setFilterRoute('ALL')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all whitespace-nowrap ${
              filterRoute === 'ALL'
                ? 'bg-brandBlue text-white shadow-sm'
                : 'bg-white border border-brandBorder text-brandTextSecondary hover:text-brandTextPrimary'
            }`}
          >
            All Routes ({schedules.length})
          </button>
          {uniqueRoutes.map(routeName => (
            <button
              key={routeName}
              onClick={() => setFilterRoute(routeName)}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all whitespace-nowrap ${
                filterRoute === routeName
                  ? 'bg-brandBlue text-white shadow-sm'
                  : 'bg-white border border-brandBorder text-brandTextSecondary hover:text-brandTextPrimary'
              }`}
            >
              {routeName}
            </button>
          ))}
        </div>
      )}

      {/* Schedule Cards List */}
      <div className="space-y-4">
        {filtered.length > 0 ? (
          filtered.map(item => (
            <div
              key={item.scheduleId}
              className="bg-white border border-brandBorder hover:border-brandBlue/30 rounded-3xl p-5 shadow-sm space-y-4 transition-all"
            >
              {/* Card Header: Bus and Status */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="bg-brandBlue/10 border border-brandBlue/20 text-brandBlue text-xs font-extrabold px-3 py-1 rounded-xl flex items-center gap-1.5">
                    <Bus className="w-3.5 h-3.5" /> {item.busNumber}
                  </span>
                  <span className="text-brandTextSecondary text-xs font-semibold">
                    {item.busCode}
                  </span>
                </div>
                {getStatusBadge(item.tripStatus)}
              </div>

              {/* Route & Timings */}
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                  Route
                </p>
                <h3 className="text-sm font-bold text-brandNavy mt-0.5">
                  {item.routeName}
                </h3>
                <div className="flex items-center gap-3 mt-2 text-xs text-brandTextSecondary font-medium">
                  <span className="flex items-center gap-1">
                    <Clock className="w-3.5 h-3.5 text-brandBlue" /> Dep: <strong className="text-brandNavy">{item.departureTime.slice(0, 5)}</strong>
                  </span>
                  {item.arrivalTime && (
                    <span>• Arr: <strong className="text-brandNavy">{item.arrivalTime.slice(0, 5)}</strong></span>
                  )}
                  {item.estimatedDurationMins && (
                    <span>• ~{item.estimatedDurationMins} mins</span>
                  )}
                </div>
              </div>

              {/* Start and End Terminals */}
              <div className="bg-brandBg border border-brandBorder rounded-2xl p-3 flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <div className="w-2 h-2 rounded-full bg-brandGreen"></div>
                  <div>
                    <span className="text-[9px] text-brandTextSecondary font-bold uppercase block">Origin</span>
                    <span className="font-bold text-brandNavy truncate max-w-[120px]">{item.startPoint}</span>
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-brandTextSecondary" />
                <div className="flex items-center gap-2 text-right">
                  <div>
                    <span className="text-[9px] text-brandTextSecondary font-bold uppercase block">Destination</span>
                    <span className="font-bold text-brandNavy truncate max-w-[120px]">{item.endPoint}</span>
                  </div>
                  <div className="w-2 h-2 rounded-full bg-brandRed"></div>
                </div>
              </div>

              {/* Ordered Stops Accordion / Preview */}
              {item.stops && item.stops.length > 0 && (
                <div className="border-t border-brandBorder pt-3">
                  <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-2">
                    Stops Sequence ({item.stops.length})
                  </p>
                  <div className="flex flex-wrap items-center gap-1.5">
                    {item.stops.map((stop, idx) => (
                      <span
                        key={stop.stopId}
                        className="text-[11px] font-bold bg-slate-50 border border-slate-200 text-slate-700 px-2.5 py-0.5 rounded-lg flex items-center gap-1"
                      >
                        <span className="text-[9px] text-brandBlue font-extrabold">{idx + 1}.</span> {stop.stopName}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Driver and Action Footer */}
              <div className="border-t border-brandBorder pt-3 flex items-center justify-between">
                <span className="text-xs text-brandTextSecondary font-medium">
                  Driver: <strong className="text-brandNavy">{item.driverName || 'Assigned Driver'}</strong>
                </span>

                {item.tripStatus === 'LIVE' || item.tripStatus === 'PAUSED' ? (
                  <button
                    onClick={() => navigate(`/student/live-map?busId=${item.busId}`)}
                    className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white font-bold text-xs rounded-xl shadow-md shadow-brandBlue/20 flex items-center gap-1.5 transition-all"
                  >
                    <Navigation className="w-3.5 h-3.5" /> Track Bus
                  </button>
                ) : (
                  <button
                    onClick={() => navigate(`/student/bus/${item.busId}`)}
                    className="px-3 py-1.5 bg-brandBg hover:bg-brandBg/80 border border-brandBorder text-brandTextPrimary font-bold text-xs rounded-xl transition-colors"
                  >
                    View Bus Details
                  </button>
                )}
              </div>
            </div>
          ))
        ) : (
          <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-2">
            <AlertTriangle className="w-8 h-8 text-brandTextSecondary/50" />
            <p className="font-bold text-sm text-brandNavy">No schedules found</p>
            <p className="text-xs text-brandTextSecondary">No active bus schedules are configured for this route currently.</p>
          </div>
        )}
      </div>
    </div>
  );
};
