import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  MapPin,
  Bus as BusIcon,
  Clock,
  User as UserIcon,
  Navigation,
  ChevronRight,
  Wifi
} from 'lucide-react';
import { SmartWebSocketClient, ConnectionState } from '../../services/websocketService';
import { ConnectionStatusBadge } from '../../components/ConnectionStatusBadge';

interface ActiveBusSummary {
  busId: string;
  busNumber: string;
  busCode: string;
  routeName: string;
  driverName: string;
  currentStop: string;
  nextStop: string;
  eta: number;
  status: string;
  etaStatus?: string;
  distanceMeters?: number;
  estimatedArrivalTime?: string;
  offRoute?: boolean;
  gpsStale?: boolean;
  routeDeviationMeters?: number;
  delayMinutes?: number;
  trackingSource: string;
  latitude?: number;
  longitude?: number;
  speed?: number;
  heading?: number;
  lastUpdated?: string;
}

interface OtherBusSummary {
  busId: string;
  busNumber: string;
  busCode: string;
  routeName: string;
  status: string;
}

interface DashboardData {
  greeting: string;
  homeStop: string;
  yourBus: ActiveBusSummary | null;
  homeEta: number;
  otherBuses: OtherBusSummary[];
}

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [wsState, setWsState] = useState<ConnectionState>('DISCONNECTED');
  const [wsRetry, setWsRetry] = useState<number>(0);
  const clientRef = useRef<SmartWebSocketClient | null>(null);
  const lastTimestampRef = useRef<number>(0);

  const fetchDashboard = async () => {
    try {
      const res = await axios.get('/api/student/dashboard');
      setData(res.data);
      setError('');
    } catch (err: any) {
      console.error(err);
      setError('Failed to load dashboard information');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard();
    const interval = setInterval(fetchDashboard, 15000); // refresh every 15s

    // SEC-12: Managed WebSocket with exponential backoff & subscription recovery
    const client = new SmartWebSocketClient({
      subscriptions: [{ type: 'SUBSCRIBE_CLIENT' }],
      onStateChange: (state, retry) => {
        setWsState(state);
        setWsRetry(retry);
      },
      onMessage: (msg) => {
        try {
          if (!msg || !msg.type) return;

          if (msg.type === 'BUS_LOCATION_UPDATE' || msg.type === 'TELEMETRY') {
            const packetEpoch = msg.timestamp ? new Date(msg.timestamp).getTime() : Date.now();
            if (lastTimestampRef.current && packetEpoch < lastTimestampRef.current) {
              return; // Drop out-of-order older packets
            }
            lastTimestampRef.current = packetEpoch;

            setData((prev) => {
              if (!prev || !prev.yourBus) return prev;
              const matches = (msg.busId && msg.busId === prev.yourBus.busId) ||
                              (msg.busNumber && msg.busNumber === prev.yourBus.busNumber) ||
                              (msg.busCode && msg.busCode === prev.yourBus.busCode);
              if (!matches) return prev;

              return {
                ...prev,
                yourBus: {
                  ...prev.yourBus,
                  status: 'LIVE',
                  gpsStale: false,
                  latitude: msg.latitude != null ? msg.latitude : prev.yourBus.latitude,
                  longitude: msg.longitude != null ? msg.longitude : prev.yourBus.longitude,
                  speed: msg.speed != null ? msg.speed : prev.yourBus.speed,
                  heading: msg.heading != null ? msg.heading : prev.yourBus.heading,
                  trackingSource: msg.trackingSource || prev.yourBus.trackingSource,
                  lastUpdated: msg.timestamp || new Date().toISOString()
                }
              };
            });
          } else if (msg.type === 'TRIP_STATUS_UPDATE') {
            const status = msg.status;
            setData((prev) => {
              if (!prev || !prev.yourBus) return prev;
              const matches = (msg.busId && msg.busId === prev.yourBus.busId) ||
                              (msg.busNumber && msg.busNumber === prev.yourBus.busNumber) ||
                              (msg.tripId && msg.tripId === prev.yourBus.busId);
              if (!matches && msg.busId) return prev;

              if (status === 'PAUSED') {
                return {
                  ...prev,
                  yourBus: {
                    ...prev.yourBus,
                    status: 'PAUSED'
                  }
                };
              } else if (status === 'IN_PROGRESS') {
                return {
                  ...prev,
                  yourBus: {
                    ...prev.yourBus,
                    status: 'LIVE',
                    gpsStale: false
                  }
                };
              } else if (status === 'COMPLETED' || status === 'CANCELLED') {
                return {
                  ...prev,
                  yourBus: {
                    ...prev.yourBus,
                    status: 'COMPLETED'
                  }
                };
              }
              return prev;
            });
            fetchDashboard();
          } else if (msg.type === 'BUS_STARTED') {
            fetchDashboard();
          } else if (msg.type === 'ETA_UPDATED') {
            setData((prev) => {
              if (!prev || !prev.yourBus || prev.yourBus.busId !== msg.busId) return prev;
              return {
                ...prev,
                yourBus: {
                  ...prev.yourBus,
                  eta: msg.minutesRemaining != null ? msg.minutesRemaining : prev.yourBus.eta,
                  etaStatus: msg.etaStatus || prev.yourBus.etaStatus,
                  distanceMeters: msg.distanceMeters != null ? msg.distanceMeters : prev.yourBus.distanceMeters,
                  estimatedArrivalTime: msg.estimatedArrivalTime || prev.yourBus.estimatedArrivalTime,
                  offRoute: msg.offRoute !== undefined ? msg.offRoute : prev.yourBus.offRoute,
                  gpsStale: msg.gpsStale !== undefined ? msg.gpsStale : prev.yourBus.gpsStale,
                  routeDeviationMeters: msg.routeDeviationMeters != null ? msg.routeDeviationMeters : prev.yourBus.routeDeviationMeters,
                  delayMinutes: msg.delayMinutes != null ? msg.delayMinutes : prev.yourBus.delayMinutes,
                }
              };
            });
          }
        } catch (err) {
          // ignore non-json ws frames
        }
      }
    });

    clientRef.current = client;
    client.connect();

    return () => {
      clearInterval(interval);
      client.disconnect();
      clientRef.current = null;
    };
  }, []);

  const handleTrackBus = (busId: string) => {
    navigate(`/student/live-map?busId=${busId}`);
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3 text-brandTextSecondary">
        <div className="w-8 h-8 border-4 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
        <p className="text-xs font-bold">Loading Student Dashboard...</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Greeting Header */}
      <div>
        <div className="flex items-center gap-2.5">
          <h1 className="text-2xl font-bold tracking-tight text-brandNavy">
            {data?.greeting || 'Hello there! 👋'}
          </h1>
          <ConnectionStatusBadge state={wsState} retryAttempt={wsRetry} />
        </div>
        <p className="text-xs text-brandTextSecondary mt-1 font-medium">
          Ready for your journey? Let's check coordinates.
        </p>
      </div>

      {/* Home Stop & Preference Summary Card */}
      <div className="bg-white border border-brandBorder rounded-2xl p-5 flex flex-col gap-3 shadow-sm relative overflow-hidden group">
        <div className="absolute right-0 top-0 w-32 h-32 bg-brandBlue/5 rounded-full blur-2xl group-hover:bg-brandBlue/10 transition-all duration-500"></div>
        <div className="flex items-center justify-between z-10">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
              <MapPin className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] text-brandBlue font-bold uppercase tracking-wider">
                Home Transit Stop
              </span>
              <h2 className="text-sm font-bold text-brandNavy">
                {data?.homeStop || 'Not Set'}
              </h2>
            </div>
          </div>
          <button 
            onClick={() => navigate('/student/profile')}
            className="text-xs text-brandBlue hover:text-brandBlue/90 font-bold flex items-center gap-0.5 transition-colors"
          >
            Preferences <ChevronRight className="w-4 h-4" />
          </button>
        </div>

        {data?.yourBus && (
          <div className="border-t border-brandBorder/60 pt-2.5 flex items-center justify-between text-xs text-brandTextSecondary z-10">
            <span className="flex items-center gap-1.5 font-medium">
              <BusIcon className="w-3.5 h-3.5 text-brandBlue" /> Selected Bus: <strong className="text-brandNavy">{data.yourBus.busNumber}</strong>
            </span>
            <button
              onClick={() => navigate('/student/schedules')}
              className="text-[11px] text-brandBlue font-bold hover:underline"
            >
              All Schedules →
            </button>
          </div>
        )}
      </div>

      {/* Error Alert */}
      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold">
          {error}
        </div>
      )}

      {/* Active Bus / Telemetry Section */}
      <div>
        <h3 className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider mb-3 px-1">
          Your Primary Bus
        </h3>

        {data?.yourBus ? (
          <div className="bg-white border border-brandBlue/20 rounded-3xl p-5 flex flex-col gap-4 shadow-sm relative overflow-hidden">
            {/* Top Tag Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="bg-brandBlue text-white font-extrabold text-[10px] px-3 py-1 rounded-full uppercase tracking-wider shadow-sm">
                  {data.yourBus.busNumber}
                </span>
                {data.yourBus.offRoute ? (
                  <span className="flex items-center gap-1.5 bg-brandRed/15 border border-brandRed/30 text-brandRed text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase animate-pulse">
                    <span className="w-1.5 h-1.5 bg-brandRed rounded-full"></span>
                    🚨 OFF ROUTE
                  </span>
                ) : data.yourBus.gpsStale ? (
                  <span className="flex items-center gap-1.5 bg-brandAmber/15 border border-brandAmber/30 text-brandAmber text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase">
                    <span className="w-1.5 h-1.5 bg-brandAmber rounded-full"></span>
                    ⚠️ GPS STALE
                  </span>
                ) : data.yourBus.status === 'LIVE' ? (
                  <span className="flex items-center gap-1.5 bg-brandGreen/10 border border-brandGreen/25 text-brandGreen text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase">
                    <span className="w-1.5 h-1.5 bg-brandGreen rounded-full animate-pulse"></span>
                    🟢 LIVE
                  </span>
                ) : data.yourBus.status === 'PAUSED' ? (
                  <span className="flex items-center gap-1.5 bg-brandAmber/15 border border-brandAmber/30 text-brandAmber text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase">
                    <span className="w-1.5 h-1.5 bg-brandAmber rounded-full"></span>
                    ⏸️ PAUSED
                  </span>
                ) : data.yourBus.status === 'COMPLETED' ? (
                  <span className="flex items-center gap-1.5 bg-slate-100 border border-slate-200 text-slate-600 text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase">
                    Trip Completed
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5 bg-brandAmber/10 border border-brandAmber/20 text-brandAmber text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase">
                    Trip not started
                  </span>
                )}
              </div>
              <div className="flex items-center gap-1 text-[10px] font-bold text-brandTextSecondary bg-brandBg border border-brandBorder px-2 py-0.5 rounded-lg">
                {data.yourBus.trackingSource === 'GPS_DEVICE' ? (
                  <>
                    <Wifi className="w-3 h-3 text-brandGreen shrink-0" /> Live GPS
                  </>
                ) : (
                  <>
                    <Navigation className="w-3 h-3 text-brandBlue shrink-0" /> Phone GPS
                  </>
                )}
              </div>
            </div>

            {/* Route Detail */}
            <div>
              <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                Route
              </p>
              <h4 className="font-bold text-brandNavy text-sm">
                {data.yourBus.routeName}
              </h4>
            </div>

            {/* Stops progress grid */}
            <div className="grid grid-cols-2 gap-4 border-y border-brandBorder py-3 text-xs">
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                  Current location
                </p>
                <p className="font-bold text-brandNavy">
                  {data.yourBus.currentStop}
                </p>
              </div>
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                  Next Stop
                </p>
                <p className="font-bold text-brandNavy">
                  {data.yourBus.nextStop}
                </p>
              </div>
            </div>

            {/* Driver & ETA information */}
            <div className="flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-brandBg border border-brandBorder rounded-lg text-brandTextSecondary">
                  <UserIcon className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-[9px] text-brandTextSecondary font-bold">Driver</p>
                  <p className="font-bold text-brandNavy">{data.yourBus.driverName}</p>
                </div>
              </div>
              
              {/* ETA Display with Phase 5 status pill */}
              <div className="text-right flex flex-col items-end">
                <div className="flex items-center gap-1.5">
                  <span className="text-[9px] text-brandTextSecondary font-bold">ETA</span>
                  {data.yourBus.etaStatus === 'DELAYED' ? (
                    <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-brandAmber/15 text-brandAmber border border-brandAmber/30">
                      DELAYED {data.yourBus.delayMinutes ? `(+${data.yourBus.delayMinutes}m)` : ''}
                    </span>
                  ) : data.yourBus.offRoute ? (
                    <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-brandRed/15 text-brandRed border border-brandRed/30">
                      OFF ROUTE
                    </span>
                  ) : data.yourBus.gpsStale ? (
                    <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-slate-200 text-slate-700">
                      GPS STALE
                    </span>
                  ) : (
                    <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-brandGreen/15 text-brandGreen border border-brandGreen/30">
                      ON TIME
                    </span>
                  )}
                </div>
                <p className="text-base font-black text-brandBlue">
                  {data.yourBus.eta >= 0 ? `${data.yourBus.eta} min` : 'Calculating...'}
                </p>
                {data.yourBus.distanceMeters != null && (
                  <p className="text-[10px] text-brandTextSecondary font-medium">
                    {(data.yourBus.distanceMeters / 1000).toFixed(1)} km away
                  </p>
                )}
              </div>
            </div>

            {/* Deviation / Delay Warning Notification if applicable */}
            {data.yourBus.offRoute && (
              <div className="p-3 bg-brandRed/10 border border-brandRed/25 rounded-2xl flex items-center gap-2.5 text-xs text-brandRed font-semibold">
                <span className="w-2 h-2 rounded-full bg-brandRed animate-ping"></span>
                <span>Bus is currently deviating from its assigned route path ({Math.round(data.yourBus.routeDeviationMeters || 0)}m off route).</span>
              </div>
            )}

            {/* Home Stop Arrival Overlay */}
            {data.homeEta >= 0 && (
              <div className="bg-brandBlue/5 border border-brandBlue/10 rounded-2xl p-4 flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <Clock className="w-5 h-5 text-brandBlue shrink-0" />
                  <div>
                    <p className="text-brandNavy font-bold">Reaches home stop in</p>
                    <p className="text-[10px] text-brandTextSecondary font-semibold">Stop: {data.homeStop}</p>
                  </div>
                </div>
                <span className="text-lg font-black text-brandNavy">
                  {data.homeEta}m
                </span>
              </div>
            )}

            {/* Action Track Bus */}
            <button
              onClick={() => handleTrackBus(data.yourBus!.busId)}
              className="w-full bg-brandBlue hover:bg-brandBlue/90 text-white font-black py-3.5 px-4 rounded-2xl shadow-lg shadow-brandBlue/25 flex items-center justify-center gap-2.5 transition-all uppercase tracking-wider text-xs"
            >
              <Navigation className="w-4 h-4" /> TRACK MY BUS
            </button>
          </div>
        ) : (
          <div className="bg-white border border-brandBorder rounded-3xl p-6 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-2 shadow-sm">
            <BusIcon className="w-10 h-10 text-brandTextSecondary/50 mb-1" />
            <p className="font-bold text-sm text-brandNavy">No live trip currently running</p>
            <p className="text-xs text-brandTextSecondary max-w-xs font-semibold">
              When drivers start an active schedule, your primary bus tracking status and live ETA details will appear here.
            </p>
            <button
              onClick={() => navigate('/student/search')}
              className="mt-2 text-xs bg-brandBg hover:bg-brandBg/85 border border-brandBorder text-brandTextPrimary font-bold px-4 py-2 rounded-xl transition-colors shadow-sm"
            >
              Search schedules
            </button>
          </div>
        )}
      </div>

      {/* Other Buses Section */}
      <div>
        <h3 className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider mb-3 px-1">
          Other Running Buses
        </h3>

        {data?.otherBuses && data.otherBuses.length > 0 ? (
          <div className="flex flex-col gap-3">
            {data.otherBuses.map((bus) => (
              <div
                key={bus.busId}
                onClick={() => navigate(`/student/bus/${bus.busId}`)}
                className="bg-white hover:bg-brandBlue/5 border border-brandBorder hover:border-brandBlue/35 rounded-2xl p-4 flex items-center justify-between cursor-pointer transition-all shadow-sm group"
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextSecondary group-hover:text-brandBlue group-hover:bg-brandBlue/5 transition-all">
                    <BusIcon className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold text-brandNavy text-sm">
                        {bus.busNumber}
                      </span>
                      <span className="w-1.5 h-1.5 bg-brandGreen rounded-full"></span>
                    </div>
                    <p className="text-[10px] text-brandTextSecondary font-bold">
                      {bus.routeName}
                    </p>
                  </div>
                </div>
                <ChevronRight className="w-5 h-5 text-brandTextSecondary/60 group-hover:text-brandTextPrimary transition-all" />
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-brandTextSecondary font-semibold italic text-center py-2">
            No other active buses running right now.
          </p>
        )}
      </div>
    </div>
  );
};
