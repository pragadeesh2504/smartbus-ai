import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Calendar,
  Bus,
  MapPin,
  Clock,
  Play,
  CheckCircle,
  AlertTriangle,
  ChevronRight,
  Shield
} from 'lucide-react';

interface StopInfo {
  stopId: string;
  stopName: string;
  sequence: number;
  arrivalTime?: string;
  departureTime?: string;
}

interface DriverScheduleItem {
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
  daysOfWeek: string;
  scheduleStatus: string;
  tripStatus: string; // UPCOMING, IN_PROGRESS, PAUSED, COMPLETED
  activeTripId?: string;
  stops: StopInfo[];
}

export const DriverSchedules: React.FC = () => {
  const navigate = useNavigate();
  const [schedules, setSchedules] = useState<DriverScheduleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [startingScheduleId, setStartingScheduleId] = useState<string | null>(null);

  const fetchSchedules = async () => {
    try {
      const res = await axios.get('/api/driver/schedules');
      setSchedules(res.data || []);
      setError('');
    } catch (err: any) {
      console.error('Failed to load driver schedules', err);
      setError(err.response?.data?.message || 'Unable to load your assigned schedules.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSchedules();
  }, []);

  const handleStartTrip = async (scheduleId: string) => {
    if (startingScheduleId) return;
    setStartingScheduleId(scheduleId);
    try {
      await axios.post(`/api/driver/trips/${scheduleId}/start`);
      navigate('/driver/active');
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to start trip on this schedule.');
      setStartingScheduleId(null);
    }
  };

  const getStatusBadge = (status: string) => {
    if (status === 'IN_PROGRESS') {
      return (
        <span className="flex items-center gap-1.5 bg-brandGreen/15 border border-brandGreen/30 text-brandGreen text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase">
          <span className="w-1.5 h-1.5 bg-brandGreen rounded-full animate-pulse"></span>
          ?? IN PROGRESS
        </span>
      );
    }
    if (status === 'PAUSED') {
      return (
        <span className="flex items-center gap-1.5 bg-brandAmber/15 border border-brandAmber/30 text-brandAmber text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase">
          <span className="w-1.5 h-1.5 bg-brandAmber rounded-full"></span>
          ?? PAUSED
        </span>
      );
    }
    if (status === 'COMPLETED') {
      return (
        <span className="flex items-center gap-1.5 bg-slate-100 border border-slate-200 text-slate-600 text-[10px] font-bold px-2.5 py-0.5 rounded-full">
          <CheckCircle className="w-3.5 h-3.5" /> COMPLETED
        </span>
      );
    }
    return (
      <span className="flex items-center gap-1.5 bg-brandBlue/10 border border-brandBlue/20 text-brandBlue text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase">
        UPCOMING
      </span>
    );
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3 text-brandTextSecondary">
        <div className="w-8 h-8 border-4 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
        <p className="text-xs font-bold">Loading your duty schedules...</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 pb-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold tracking-tight text-brandNavy flex items-center gap-2">
          <Calendar className="w-5 h-5 text-brandBlue animate-pulse" /> My Schedules
        </h1>
        <p className="text-xs text-brandTextSecondary mt-1 font-medium">
          Official schedules strictly assigned to your driver crew profile.
        </p>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold">
          {error}
        </div>
      )}

      {/* Schedules List */}
      <div className="space-y-4">
        {schedules.length > 0 ? (
          schedules.map((item) => (
            <div
              key={item.scheduleId}
              className="bg-white border border-brandBorder hover:border-brandBlue/30 rounded-3xl p-5 shadow-sm space-y-4 transition-all"
            >
              {/* Header: Bus + Status */}
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
                  Assigned Route
                </p>
                <h3 className="text-sm font-bold text-brandNavy mt-0.5">
                  {item.routeName}
                </h3>
                <div className="flex items-center gap-3 mt-2 text-xs text-brandTextSecondary font-medium">
                  <span className="flex items-center gap-1">
                    <Clock className="w-3.5 h-3.5 text-brandBlue" /> Dep: <strong className="text-brandNavy">{item.departureTime.slice(0, 5)}</strong>
                  </span>
                  {item.arrivalTime && (
                    <span>� Arr: <strong className="text-brandNavy">{item.arrivalTime.slice(0, 5)}</strong></span>
                  )}
                  {item.daysOfWeek && (
                    <span>� Days: {item.daysOfWeek.split(',')[0]}</span>
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

              {/* Stops list */}
              {item.stops && item.stops.length > 0 && (
                <div className="border-t border-brandBorder pt-3">
                  <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-2">
                    En-Route Stops ({item.stops.length})
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

              {/* Action Button */}
              <div className="border-t border-brandBorder pt-3">
                {item.tripStatus === 'IN_PROGRESS' || item.tripStatus === 'PAUSED' ? (
                  <button
                    onClick={() => navigate('/driver/active')}
                    className="w-full h-11 bg-brandGreen hover:bg-brandGreen/90 text-white font-bold rounded-xl text-xs flex items-center justify-center gap-2 shadow-sm transition-all"
                  >
                    <Play className="w-4 h-4 animate-pulse" /> Resume Active Trip
                  </button>
                ) : item.tripStatus === 'COMPLETED' ? (
                  <button
                    disabled
                    className="w-full h-11 bg-slate-100 border border-slate-200 text-slate-400 font-bold rounded-xl text-xs flex items-center justify-center gap-2 cursor-not-allowed"
                  >
                    <CheckCircle className="w-4 h-4" /> Duty Completed
                  </button>
                ) : (
                  <button
                    onClick={() => handleStartTrip(item.scheduleId)}
                    disabled={startingScheduleId === item.scheduleId}
                    className="w-full h-11 bg-brandBlue hover:bg-brandBlue/90 text-white font-bold rounded-xl text-xs flex items-center justify-center gap-2 shadow-sm shadow-brandBlue/20 transition-all"
                  >
                    <Play className="w-4 h-4" />
                    {startingScheduleId === item.scheduleId ? 'Starting Trip...' : 'Start Trip'}
                  </button>
                )}
              </div>
            </div>
          ))
        ) : (
          <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-2">
            <AlertTriangle className="w-8 h-8 text-brandTextSecondary/50" />
            <p className="font-bold text-sm text-brandNavy">No schedules currently assigned</p>
            <p className="text-xs text-brandTextSecondary">Contact college transport administration to assign duty schedules to your profile.</p>
          </div>
        )}
      </div>
    </div>
  );
};
