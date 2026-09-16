import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  User,
  Bus,
  MapPin,
  Clock,
  Radio,
  Navigation,
  Shield,
  Wrench,
  AlertTriangle,
  QrCode,
  Play
} from 'lucide-react';

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<any>(null);
  const [assignments, setAssignments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [sosLoading, setSosLoading] = useState(false);

  // Breakdown Modal State
  const [breakdownOpen, setBreakdownOpen] = useState(false);
  const [issueType, setIssueType] = useState('ENGINE');
  const [description, setDescription] = useState('');
  const [breakdownLoading, setBreakdownLoading] = useState(false);
  const [startTripLoading, setStartTripLoading] = useState(false);
  const [startTripSuccess, setStartTripSuccess] = useState(false);

  const fetchDashboard = async () => {
    try {
      const res = await axios.get('/api/driver/dashboard');
      setData(res.data);
      try {
        const resAssign = await axios.get('/api/driver/assignments/today');
        setAssignments(resAssign.data);
      } catch (err) {
        console.warn('Failed to load assignments list', err);
      }
    } catch (e) {
      console.error('Failed to load driver dashboard', e);
    } finally {
      setLoading(false);
    }
  };

  const handleStartTrip = async (scheduleId: string) => {
    if (!scheduleId || startTripLoading) return;
    setStartTripLoading(true);
    try {
      await axios.post(`/api/driver/trips/${scheduleId}/start`);
      setStartTripSuccess(true);
      setTimeout(() => {
        navigate('/driver/active');
      }, 500);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to start trip on this schedule.');
      setStartTripLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard();
  }, []);

  const triggerSos = async () => {
    if (!window.confirm('Are you sure you want to trigger emergency SOS?')) return;
    setSosLoading(true);
    try {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          async (pos) => {
            await axios.post('/api/driver/emergency', {
              latitude: pos.coords.latitude,
              longitude: pos.coords.longitude
            });
            alert('SOS emergency alert dispatched to transport control room.');
          },
          async () => {
            await axios.post('/api/driver/emergency', { latitude: 12.9715, longitude: 80.2210 });
            alert('SOS emergency alert dispatched (coordinates fallback).');
          }
        );
      } else {
        await axios.post('/api/driver/emergency', { latitude: 12.9715, longitude: 80.2210 });
        alert('SOS emergency alert dispatched.');
      }
    } catch (e) {
      alert('Failed to send SOS.');
    } finally {
      setSosLoading(false);
    }
  };

  const submitBreakdown = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!description.trim()) return;
    setBreakdownLoading(true);
    try {
      await axios.post('/api/driver/breakdown', {
        issueType,
        description,
        photoUrl: ''
      });
      alert('Vehicle breakdown report submitted.');
      setBreakdownOpen(false);
      setDescription('');
    } catch (e) {
      alert('Failed to submit breakdown.');
    } finally {
      setBreakdownLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue"></div>
      </div>
    );
  }

  const hasAssignment = data && data.busNumber;

  return (
    <div className="space-y-6">
      {/* Driver Profile Status Card */}
      <div className="bg-white border border-brandBorder rounded-3xl p-6 relative overflow-hidden shadow-sm">
        <div className="absolute top-0 right-0 w-24 h-24 bg-brandBlue/5 rounded-full blur-2xl"></div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-brandBlue/5 border border-brandBlue/10 rounded-full flex items-center justify-center text-brandBlue shrink-0">
              <User className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-brandNavy tracking-tight">{data?.driverName}</h2>
              <span className="text-xs text-brandTextSecondary font-bold">Approved Driver Crew</span>
            </div>
          </div>
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-brandGreen/10 border border-brandGreen/25 text-brandGreen text-xs font-bold shadow-sm">
            <span className="w-2 h-2 rounded-full bg-brandGreen animate-pulse"></span>
            {data?.driverStatus || 'AVAILABLE'}
          </div>
        </div>
      </div>

      {/* active trip notification banner */}
      {data?.activeTripId && (
        <div className="bg-emerald-500/10 border-2 border-emerald-500/40 rounded-3xl p-5 flex items-center justify-between text-emerald-950 font-bold shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-emerald-500 text-white rounded-2xl shadow-sm">
              <Play className="w-5 h-5 fill-white animate-pulse" />
            </div>
            <div>
              <p className="text-xs font-black uppercase tracking-wider text-emerald-600">🟢 TRIP IN PROGRESS</p>
              <p className="text-sm font-black text-emerald-950">Active Navigation Running</p>
            </div>
          </div>
          <button
            onClick={() => navigate('/driver/active')}
            className="px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-black rounded-2xl text-xs transition-all shadow-md uppercase tracking-wider"
          >
            OPEN ACTIVE TRIP
          </button>
        </div>
      )}

      {/* Start Trip Success Notification */}
      {startTripSuccess && (
        <div className="bg-emerald-600 text-white p-4 rounded-2xl text-xs font-bold flex items-center justify-between shadow-lg animate-fade-in">
          <span>✓ Trip Started! Opening SmartBus Navigation...</span>
        </div>
      )}

      {/* Assigned Trip Card / Start My Trip (P0 Specification) */}
      <div className="bg-white border border-brandBorder rounded-3xl p-6 shadow-sm space-y-6">
        <h3 className="text-xs font-bold uppercase tracking-wider text-brandTextSecondary flex items-center gap-1.5">
          <Bus className="w-4 h-4 text-brandBlue" /> ASSIGNED TRIP
        </h3>

        {hasAssignment ? (
          <div className="space-y-5">
            {/* Bus & Schedule Info */}
            <div className="flex items-start justify-between">
              <div>
                <span className="text-[10px] text-brandTextSecondary font-bold uppercase">Bus Assigned</span>
                <h2 className="text-xl font-black text-brandNavy">{data.busNumber}</h2>
                <span className="text-xs text-brandBlue font-extrabold">{data.busCode}</span>
              </div>
              <div className="text-right">
                <span className="text-[10px] text-brandTextSecondary font-bold uppercase">Scheduled Departure</span>
                <p className="text-lg font-black text-brandNavy flex items-center gap-1 justify-end">
                  <Clock className="w-4 h-4 text-brandBlue" />
                  {data.departureTime ? data.departureTime.slice(0, 5) : 'Scheduled'}
                </p>
              </div>
            </div>

            {/* Route & Stop Details */}
            <div className="bg-brandBg border border-brandBorder rounded-2xl p-4 space-y-2">
              <div className="flex items-center justify-between">
                <p className="text-[10px] text-brandTextSecondary font-bold uppercase">Route</p>
                <div className="text-right text-[11px] font-extrabold text-brandTextSecondary">
                  {data.totalStops != null && data.totalStops > 0 && (
                    <span>{data.totalStops} Stops</span>
                  )}
                  {data.distance != null && data.distance > 0 && (
                    <span> • {data.distance.toFixed(1)} km</span>
                  )}
                </div>
              </div>
              <h4 className="text-sm font-black text-brandNavy">{data.routeName}</h4>
              {data.startPoint && data.endPoint && (
                <p className="text-xs text-brandTextSecondary font-semibold">
                  {data.startPoint} → {data.endPoint}
                </p>
              )}
            </div>

            {/* Hardware Status */}
            <div className="grid grid-cols-2 gap-3 text-xs font-bold border-t border-brandBorder pt-3">
              <div className="flex items-center gap-2">
                <Radio className={`w-4 h-4 ${data.gpsDeviceOnline ? 'text-brandGreen' : 'text-brandTextSecondary'}`} />
                <span className="text-brandTextSecondary">GPS Unit:</span>
                <span className={data.gpsDeviceOnline ? 'text-brandGreen' : 'text-brandTextSecondary'}>
                  {data.gpsDeviceOnline ? 'Online' : 'Offline'}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Navigation className="w-4 h-4 text-brandGreen" />
                <span className="text-brandTextSecondary">Driver GPS:</span>
                <span className="text-brandGreen">Ready</span>
              </div>
            </div>

            {/* Primary Action Button */}
            <div className="pt-2">
              {data.activeTripId ? (
                <button
                  onClick={() => navigate('/driver/active')}
                  className="w-full h-14 bg-emerald-600 hover:bg-emerald-700 text-white font-black rounded-2xl shadow-lg shadow-emerald-600/20 flex items-center justify-center gap-3 transition-all text-base uppercase tracking-wider"
                >
                  <Play className="w-5 h-5 fill-white animate-pulse" />
                  OPEN ACTIVE TRIP
                </button>
              ) : (
                <button
                  onClick={() => handleStartTrip(data.scheduleId)}
                  disabled={startTripLoading || !data.scheduleId}
                  className="w-full h-14 bg-brandGreen hover:bg-brandGreen/90 disabled:bg-brandGreen/50 text-white font-black rounded-2xl shadow-lg shadow-brandGreen/25 flex items-center justify-center gap-3 transition-all text-base uppercase tracking-wider"
                >
                  <Play className="w-5 h-5 fill-white" />
                  {startTripLoading ? 'Starting My Trip...' : '🚍 START MY TRIP'}
                </button>
              )}
            </div>

            {/* Secondary QR Scan trigger */}
            {!data.activeTripId && (
              <button
                onClick={() => navigate('/driver/scan')}
                className="w-full py-2.5 bg-brandBg hover:bg-brandBg/80 border border-brandBorder text-brandTextSecondary hover:text-brandTextPrimary font-bold rounded-xl text-xs flex items-center justify-center gap-2 transition-all"
              >
                <QrCode className="w-4 h-4" /> Verify Bus with QR Scanner
              </button>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-8 text-center space-y-3">
            <div className="p-4 bg-brandBg border border-brandBorder rounded-full text-brandTextSecondary">
              <AlertTriangle className="w-8 h-8" />
            </div>
            <div>
              <p className="text-sm font-bold text-brandNavy">No active schedule assigned</p>
              <p className="text-xs text-brandTextSecondary max-w-[240px] mx-auto mt-1 font-semibold">
                You do not have any operational bus schedule assigned today.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Emergency & Breakdown Controls */}
      {data?.activeTripId && (
        <div className="grid grid-cols-2 gap-4">
          <button
            onClick={() => setBreakdownOpen(true)}
            className="h-16 bg-white border border-brandBorder text-brandTextPrimary font-extrabold rounded-2xl hover:bg-brandBg flex items-center justify-center gap-2 shadow-sm transition-all text-xs uppercase tracking-wider"
          >
            <Wrench className="w-5 h-5 text-brandBlue" />
            Report Issue
          </button>
          <button
            onClick={triggerSos}
            disabled={sosLoading}
            className="h-16 bg-brandRed hover:bg-brandRed/90 disabled:bg-brandRed/50 disabled:text-white/60 text-white font-extrabold rounded-2xl flex items-center justify-center gap-2 shadow-md shadow-brandRed/10 transition-all text-xs uppercase tracking-wider"
          >
            <Shield className="w-5 h-5" />
            {sosLoading ? 'Dispatched...' : 'SOS Panic'}
          </button>
        </div>
      )}

      {/* Breakdown modal */}
      {breakdownOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder rounded-3xl w-full max-w-sm p-6 space-y-6 shadow-2xl text-brandTextPrimary">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-brandNavy">Report Vehicle Issue</h3>
              <button
                onClick={() => setBreakdownOpen(false)}
                className="text-brandTextSecondary hover:text-brandTextPrimary font-bold text-lg"
              >
                ✕
              </button>
            </div>
            <form onSubmit={submitBreakdown} className="space-y-4">
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider">Issue Type</label>
                <select
                  value={issueType}
                  onChange={(e) => setIssueType(e.target.value)}
                  className="w-full bg-brandBg border border-brandBorder rounded-xl px-4 py-2.5 text-xs text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold"
                >
                  <option value="ENGINE">Engine / Power loss</option>
                  <option value="TYRE">Puncture / Tyre issue</option>
                  <option value="ELECTRICAL">Electrical breakdown</option>
                  <option value="FUEL">Fuel shortage</option>
                  <option value="ACCIDENT">Accident</option>
                  <option value="OTHER">Other technical fault</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider">Description</label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={4}
                  placeholder="Provide breakdown details..."
                  className="w-full bg-brandBg border border-brandBorder rounded-xl px-4 py-3 text-xs text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all"
                  required
                ></textarea>
              </div>

              <button
                type="submit"
                disabled={breakdownLoading}
                className="w-full py-3 bg-brandBlue hover:bg-brandBlue/90 disabled:bg-brandBlue/50 text-white font-bold rounded-xl transition-all text-xs shadow-md"
              >
                {breakdownLoading ? 'Submitting...' : 'Submit Report'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
