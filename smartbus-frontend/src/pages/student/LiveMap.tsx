import React, { useEffect, useState, useRef, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet';
import L from 'leaflet';
import {
  Wifi,
  WifiOff,
  Navigation,
  MapPin,
  Clock,
  Compass,
  AlertTriangle,
  ChevronLeft
} from 'lucide-react';
import { SmartWebSocketClient, ConnectionState } from '../../services/websocketService';
import { ConnectionStatusBadge } from '../../components/ConnectionStatusBadge';
import { GoogleSmartBusMap, MapStop } from '../../components/map/GoogleSmartBusMap';

interface StopProgress {
  stopId: string;
  stopName: string;
  latitude: number;
  longitude: number;
  sequence: number;
}

interface UpcomingStopEta {
  sequence: number;
  stopId: string;
  stopName: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
  etaMinutes: number;
  estimatedArrivalTime: string;
  passed: boolean;
  isNext: boolean;
}

interface RouteProgressInfo {
  passedStops: number;
  totalStops: number;
  nextStopSequence: number;
  nextStopName: string;
  progressPercent: number;
}

interface BusDetails {
  busId: string;
  busNumber: string;
  busCode: string;
  routeId?: string;
  routeName: string;
  driverName: string;
  status: string;
  currentStop: string;
  nextStop: string;
  eta: number;
  etaStatus?: string;
  distanceMeters?: number;
  offRoute?: boolean;
  gpsStale?: boolean;
  gpsStatus?: 'LIVE' | 'GPS_STALE' | 'UNAVAILABLE' | string;
  routeDeviationMeters?: number;
  delayMinutes?: number;
  trackingSource: string;
  gpsDeviceOnline: boolean;
  startLatitude?: number | null;
  startLongitude?: number | null;
  endLatitude?: number | null;
  endLongitude?: number | null;
  polyline?: string | null;
  upcomingStops?: UpcomingStopEta[];
  routeProgress?: RouteProgressInfo;
}

export const LiveMap: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const busId = searchParams.get('busId');

  const [busDetails, setBusDetails] = useState<BusDetails | null>(null);
  const [routeStops, setRouteStops] = useState<StopProgress[]>([]);
  const [busLocation, setBusLocation] = useState<{ lat: number; lng: number; speed: number; heading: number } | null>(null);
  const [gpsStatus, setGpsStatus] = useState<'LIVE' | 'GPS_STALE' | 'UNAVAILABLE' | string>('UNAVAILABLE');
  const [upcomingStops, setUpcomingStops] = useState<UpcomingStopEta[]>([]);
  const [routeProgress, setRouteProgress] = useState<RouteProgressInfo | null>(null);
  const [wsState, setWsState] = useState<ConnectionState>('DISCONNECTED');
  const [wsRetry, setWsRetry] = useState<number>(0);
  const [lastUpdatedStr, setLastUpdatedStr] = useState('');
  const [isOfflineMode, setIsOfflineMode] = useState(false);
  const [isNotActive, setIsNotActive] = useState(false);
  const [scheduleInfo, setScheduleInfo] = useState<any>(null);
  const [activeAlert, setActiveAlert] = useState<string | null>(null);
  const [preferredStopId, setPreferredStopId] = useState<string | null>(null);
  const [preferredStopName, setPreferredStopName] = useState<string | null>(null);

  const clientRef = useRef<SmartWebSocketClient | null>(null);
  const lastTimestampRef = useRef<number>(0);

  // Custom Leaflet Icons using SVGs
  const getBusIcon = (heading: number, isStale: boolean = false) => L.divIcon({
    html: `<div class="relative flex items-center justify-center" style="width: 44px; height: 44px;">
      ${!isStale ? '<div class="absolute inset-0 rounded-full bg-brandTeal opacity-30 animate-ping"></div>' : ''}
      <div style="transform: rotate(${heading}deg)" class="${isStale ? 'bg-amber-500' : 'bg-brandTeal'} text-white p-2 rounded-full shadow-2xl border-2 border-white flex items-center justify-center w-10 h-10 transition-transform duration-500">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-bus"><rect width="16" height="16" x="4" y="4" rx="2"/><path d="M4 9h16"/><path d="M12 9v11"/><path d="M8 20v2"/><path d="M16 20v2"/><path d="M6 14h2"/><path d="M16 14h2"/></svg>
      </div>
    </div>`,
    className: 'custom-bus-icon',
    iconSize: [44, 44],
    iconAnchor: [22, 22],
  });

  const getStartIcon = () => L.divIcon({
    html: `<div class="relative flex items-center justify-center" style="width: 32px; height: 32px;">
      <div class="w-8 h-8 rounded-full bg-emerald-600 text-white font-black text-xs flex items-center justify-center border-2 border-white shadow-xl ring-2 ring-emerald-400/40">
        A
      </div>
      <div class="absolute -bottom-4 px-1 rounded bg-emerald-700 text-white font-black text-[7px] shadow uppercase tracking-tight whitespace-nowrap">FROM</div>
    </div>`,
    className: 'custom-start-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });

  const getEndIcon = () => L.divIcon({
    html: `<div class="relative flex items-center justify-center" style="width: 32px; height: 32px;">
      <div class="w-8 h-8 rounded-full bg-rose-600 text-white font-black text-xs flex items-center justify-center border-2 border-white shadow-xl ring-2 ring-rose-400/40">
        B
      </div>
      <div class="absolute -bottom-4 px-1 rounded bg-rose-700 text-white font-black text-[7px] shadow uppercase tracking-tight whitespace-nowrap">TO</div>
    </div>`,
    className: 'custom-end-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });

  const getStopIcon = (type: 'home' | 'next' | 'passed' | 'normal', seq?: number) => {
    if (type === 'home') {
      return L.divIcon({
        html: `<div class="relative flex items-center justify-center" style="width: 36px; height: 36px;">
          <div class="bg-amber-500 text-white p-1.5 rounded-full shadow-xl border-2 border-white flex items-center justify-center w-9 h-9 scale-110 ring-2 ring-amber-400/50">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-star"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          </div>
          <div class="absolute -bottom-3 px-1 rounded bg-amber-600 text-white font-black text-[7px] shadow uppercase tracking-tight whitespace-nowrap">HOME</div>
        </div>`,
        className: 'custom-home-stop-icon',
        iconSize: [36, 36],
        iconAnchor: [18, 18],
      });
    }
    if (type === 'passed') {
      return L.divIcon({
        html: `<div class="bg-emerald-500 text-white p-1 rounded-full shadow-md border border-white flex items-center justify-center w-6 h-6 transition-all font-black text-[10px]">✓</div>`,
        className: 'custom-passed-stop-icon',
        iconSize: [24, 24],
        iconAnchor: [12, 12],
      });
    }
    if (type === 'next') {
      return L.divIcon({
        html: `<div class="bg-brandBlue scale-110 shadow-md shadow-brandBlue/35 text-white p-1.5 rounded-full border border-white flex items-center justify-center w-7 h-7 transition-all animate-pulse font-black text-[10px]">${seq || '→'}</div>`,
        className: 'custom-next-stop-icon',
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });
    }
    return L.divIcon({
      html: `<div class="bg-slate-500 text-white p-1 rounded-full shadow-md border border-white flex items-center justify-center w-6 h-6 transition-all font-black text-[9px]">${seq || '•'}</div>`,
      className: 'custom-stop-icon',
      iconSize: [24, 24],
      iconAnchor: [12, 12],
    });
  };

  // Load base information
  const loadRouteDetails = async (busUUID: string) => {
    try {
      // 1. Fetch Student Profile for Home Stop preference
      try {
        const profRes = await axios.get('/api/student/profile');
        if (profRes.data) {
          setPreferredStopId(profRes.data.preferredStopId || null);
          setPreferredStopName(profRes.data.preferredStopName || null);
        }
      } catch (e) {
        console.warn('Could not load student profile', e);
      }

      // 2. Fetch Bus Details to get Route Name
      const busRes = await axios.get(`/api/student/buses/${busUUID}`);
      const busData: BusDetails = busRes.data;
      setBusDetails(busData);

      // 3. Fetch stops along route (works for both active and scheduled buses)
      try {
        const stopsRes = await axios.get(`/api/student/buses/${busUUID}/stops`);
        setRouteStops(stopsRes.data || []);
      } catch (e) {
        console.warn('Failed to load stops for bus', e);
      }

      if (busData.status === 'NOT_ACTIVE' || busData.status === 'SCHEDULED') {
        setIsNotActive(true);
        setScheduleInfo(busData);
        setBusLocation(null);
        setGpsStatus('UNAVAILABLE');
        return;
      }

      setIsNotActive(false);

      // 4. Fetch active telemetry coordinates once if trip is running
      try {
        const telemetryRes = await axios.get(`/api/student/live/${busUUID}`);
        const telData = telemetryRes.data;
        if (telData.latitude != null && telData.longitude != null && telData.gpsStatus !== 'UNAVAILABLE') {
          setBusLocation({
            lat: telData.latitude,
            lng: telData.longitude,
            speed: telData.speed || 0.0,
            heading: telData.heading || 0.0
          });
          setGpsStatus(telData.gpsStatus || 'LIVE');
        } else {
          setBusLocation(null);
          setGpsStatus('UNAVAILABLE');
        }

        if (telData.upcomingStops && Array.isArray(telData.upcomingStops)) {
          setUpcomingStops(telData.upcomingStops);
        }
        if (telData.routeProgress) {
          setRouteProgress(telData.routeProgress);
        }
        if (telData.lastUpdate) {
          setLastUpdatedStr(new Date(telData.lastUpdate).toLocaleTimeString());
        }

        setBusDetails(prev => prev ? {
          ...prev,
          nextStop: telData.nextStop || prev.nextStop,
          currentStop: telData.currentStop || prev.currentStop,
          eta: telData.eta != null ? telData.eta : prev.eta,
          distanceMeters: telData.distanceMeters != null ? telData.distanceMeters : prev.distanceMeters,
          offRoute: telData.offRoute != null ? telData.offRoute : prev.offRoute,
          gpsStale: telData.gpsStale != null ? telData.gpsStale : prev.gpsStale,
          gpsStatus: telData.gpsStatus || prev.gpsStatus,
          upcomingStops: telData.upcomingStops,
          routeProgress: telData.routeProgress
        } : null);
      } catch (e) {
        console.warn('Telemetry offline:', e);
        setBusLocation(null);
        setGpsStatus('UNAVAILABLE');
      }

    } catch (e) {
      console.error('Failed to load route coordinates metadata', e);
      setIsOfflineMode(true);
    }
  };

  useEffect(() => {
    if (!busId) return;

    loadRouteDetails(busId);

    // SEC-12: Managed WebSocket with exponential backoff & subscription recovery
    const client = new SmartWebSocketClient({
      subscriptions: [{ action: 'SUBSCRIBE', busId }],
      onStateChange: (state, retry) => {
        setWsState(state);
        setWsRetry(retry);
        if (state === 'CONNECTED') {
          setIsOfflineMode(false);
        }
      },
      onMessage: (message) => {
        try {
          if (!message) return;

          // Route configuration change broadcast
          if (message.type === 'ROUTE_UPDATED') {
            console.log('Student live map received ROUTE_UPDATED event:', message);
            loadRouteDetails(busId);
          }

          // Telemetry and location update
          if ((message.type === 'TELEMETRY' || message.type === 'BUS_LOCATION_UPDATE') && message.busId === busId) {
            const packetEpoch = message.timestamp ? new Date(message.timestamp).getTime() : Date.now();
            if (lastTimestampRef.current && packetEpoch < lastTimestampRef.current) {
              return; // Drop out-of-order older packets
            }
            lastTimestampRef.current = packetEpoch;

            if (message.latitude != null && message.longitude != null) {
              setBusLocation({
                lat: message.latitude,
                lng: message.longitude,
                speed: message.speed || 0.0,
                heading: message.heading || 0.0
              });
              setGpsStatus('LIVE');
            }
            setLastUpdatedStr(new Date(packetEpoch).toLocaleTimeString());
            setIsNotActive(false);
            setIsOfflineMode(false);
            setBusDetails(prev => prev ? { ...prev, gpsStale: false, gpsStatus: 'LIVE' } : null);
          } else if (message.type === 'BUS_STARTED' && (message.busId === busId || !message.busId)) {
            // Driver started trip! Transition from pre-trip to live mode
            loadRouteDetails(busId);
          } else if (message.type === 'TRIP_STATUS_UPDATE' && (message.busId === busId || !message.busId)) {
            if (message.status === 'PAUSED') {
              setBusDetails(prev => prev ? { ...prev, status: 'PAUSED' } : null);
            } else if (message.status === 'IN_PROGRESS') {
              setBusDetails(prev => prev ? { ...prev, status: 'IN_PROGRESS' } : null);
              setIsNotActive(false);
            } else if (message.status === 'COMPLETED') {
              setIsNotActive(true);
              setBusDetails(prev => prev ? { ...prev, status: 'COMPLETED' } : null);
              setBusLocation(null);
            }
            loadRouteDetails(busId);
          } else if (message.type === 'ROUTE_PROGRESS' && message.busId === busId) {
            // Update stop list sequencing
            setBusDetails((prev) => prev ? {
              ...prev,
              currentStop: message.currentStop,
              nextStop: message.nextStop,
              eta: message.eta
            } : null);
          } else if (message.type === 'ETA_UPDATED' && message.busId === busId) {
            if (message.gpsStatus) {
              setGpsStatus(message.gpsStatus);
            }
            if (message.upcomingStops && Array.isArray(message.upcomingStops)) {
              setUpcomingStops(message.upcomingStops);
            }
            if (message.routeProgress) {
              setRouteProgress(message.routeProgress);
            }
            setBusDetails((prev) => prev ? {
              ...prev,
              eta: message.minutesRemaining != null ? message.minutesRemaining : prev.eta,
              etaStatus: message.etaStatus || prev.etaStatus,
              distanceMeters: message.distanceMeters != null ? message.distanceMeters : prev.distanceMeters,
              offRoute: message.offRoute !== undefined ? message.offRoute : prev.offRoute,
              gpsStale: message.gpsStale !== undefined ? message.gpsStale : prev.gpsStale,
              gpsStatus: message.gpsStatus || prev.gpsStatus,
              routeDeviationMeters: message.routeDeviationMeters != null ? message.routeDeviationMeters : prev.routeDeviationMeters,
              delayMinutes: message.delayMinutes != null ? message.delayMinutes : prev.delayMinutes,
              nextStop: message.nextStopName || prev.nextStop,
              currentStop: message.currentStopName || prev.currentStop,
              upcomingStops: message.upcomingStops || prev.upcomingStops,
              routeProgress: message.routeProgress || prev.routeProgress
            } : null);
          } else if (message.type === 'BUS_OFF_ROUTE' && message.busId === busId) {
            setActiveAlert(`Bus has deviated from route (${Math.round(message.deviationMeters || 0)}m off-route)`);
            setBusDetails((prev) => prev ? { ...prev, offRoute: true } : null);
          } else if (message.type === 'BUS_BACK_ON_ROUTE' && message.busId === busId) {
            setActiveAlert(null);
            setBusDetails((prev) => prev ? { ...prev, offRoute: false } : null);
          } else if (message.type === 'GPS_STALE' && message.busId === busId) {
            setActiveAlert(`GPS signal stale (last update ${message.secondsOffline}s ago)`);
            setGpsStatus('GPS_STALE');
            setBusDetails((prev) => prev ? { ...prev, gpsStale: true, gpsStatus: 'GPS_STALE' } : null);
          }
        } catch (err) {
          console.warn('Malformed websocket stream frame', err);
        }
      }
    });

    clientRef.current = client;
    client.connect();

    return () => {
      client.disconnect();
      clientRef.current = null;
    };
  }, [busId]);

  if (!busId) {
    return (
      <div className="bg-white border border-brandBorder rounded-3xl p-6 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-3 shadow-sm">
        <MapPin className="w-10 h-10 text-brandTextSecondary/50 mb-1" />
        <p className="font-bold text-sm text-brandNavy">No Bus Selected for Live Tracking</p>
        <button
          onClick={() => navigate('/student/search')}
          className="mt-2 text-xs bg-brandBg border border-brandBorder text-brandTextPrimary font-bold px-4 py-2 rounded-xl transition-colors shadow-sm"
        >
          Browse schedules
        </button>
      </div>
    );
  }

  // Determine initial center
  const homeStopObj = routeStops.find(s => 
    (preferredStopId && s.stopId === preferredStopId) || 
    (preferredStopName && s.stopName === preferredStopName)
  );

  // Compute road-following polyline coordinates
  const polylineCoords: [number, number][] = useMemo(() => {
    if (busDetails?.polyline) {
      try {
        const parsed = JSON.parse(busDetails.polyline);
        if (Array.isArray(parsed) && parsed.length > 1) {
          return parsed;
        }
      } catch (e) {}
    }
    const pts: [number, number][] = [];
    if (busDetails?.startLatitude != null && busDetails?.startLongitude != null) {
      pts.push([busDetails.startLatitude, busDetails.startLongitude]);
    }
    routeStops.forEach(s => pts.push([s.latitude, s.longitude]));
    if (busDetails?.endLatitude != null && busDetails?.endLongitude != null) {
      pts.push([busDetails.endLatitude, busDetails.endLongitude]);
    }
    return pts;
  }, [busDetails, routeStops]);

  const startCoord: [number, number] | null = useMemo(() => {
    if (busDetails?.startLatitude != null && busDetails?.startLongitude != null) {
      return [busDetails.startLatitude, busDetails.startLongitude];
    }
    if (routeStops.length > 0) {
      return [routeStops[0].latitude, routeStops[0].longitude];
    }
    return null;
  }, [busDetails, routeStops]);

  const endCoord: [number, number] | null = useMemo(() => {
    if (busDetails?.endLatitude != null && busDetails?.endLongitude != null) {
      return [busDetails.endLatitude, busDetails.endLongitude];
    }
    if (routeStops.length > 0) {
      return [routeStops[routeStops.length - 1].latitude, routeStops[routeStops.length - 1].longitude];
    }
    return null;
  }, [busDetails, routeStops]);

  const startPointObj = useMemo(() => {
    if (startCoord) {
      return {
        name: busDetails?.routeName ? `${busDetails.routeName} (Start)` : 'Starting Point',
        latitude: startCoord[0],
        longitude: startCoord[1]
      };
    }
    return null;
  }, [startCoord, busDetails?.routeName]);

  const endPointObj = useMemo(() => {
    if (endCoord) {
      return {
        name: busDetails?.routeName ? `${busDetails.routeName} (Destination)` : 'Destination',
        latitude: endCoord[0],
        longitude: endCoord[1]
      };
    }
    return null;
  }, [endCoord, busDetails?.routeName]);

  const mapStops: MapStop[] = useMemo(() => {
    return routeStops.map((stop) => {
      const isHome = (preferredStopId && stop.stopId === preferredStopId) ||
                     (preferredStopName && stop.stopName === preferredStopName);
      let status: 'PASSED' | 'NEXT' | 'UPCOMING' | 'DEFAULT' = 'UPCOMING';
      if (routeProgress) {
        if (stop.sequence < routeProgress.nextStopSequence) status = 'PASSED';
        else if (stop.sequence === routeProgress.nextStopSequence) status = 'NEXT';
      } else if (upcomingStops.length > 0) {
        const uStop = upcomingStops.find(u => u.stopId === stop.stopId || u.sequence === stop.sequence);
        if (uStop) {
          if (uStop.passed) status = 'PASSED';
          else if (uStop.isNext) status = 'NEXT';
        }
      } else if (busDetails?.nextStop) {
        if (busDetails.nextStop === stop.stopName) status = 'NEXT';
      }

      return {
        id: stop.stopId,
        stopName: stop.stopName,
        latitude: stop.latitude,
        longitude: stop.longitude,
        sequenceNumber: stop.sequence,
        status,
        isHomeStop: Boolean(isHome)
      };
    });
  }, [routeStops, preferredStopId, preferredStopName, routeProgress, upcomingStops, busDetails?.nextStop]);

  const defaultCenter: [number, number] = busLocation
    ? [busLocation.lat, busLocation.lng]
    : homeStopObj
    ? [homeStopObj.latitude, homeStopObj.longitude]
    : routeStops.length > 0
    ? [routeStops[0].latitude, routeStops[0].longitude]
    : [12.971598, 77.594562];

  return (
    <div className="flex flex-col gap-6">
      {/* Top Header Controls bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="p-2 bg-white border border-brandBorder rounded-xl text-brandTextSecondary hover:text-brandTextPrimary transition-all shadow-sm"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-bold text-brandNavy">Real-time Live Tracker</h2>
            <p className="text-[10px] text-brandTextSecondary font-bold mt-0.5 uppercase tracking-wider">WebSocket stream connection</p>
          </div>
        </div>

        {/* Network sync status indicator */}
        <ConnectionStatusBadge state={wsState} retryAttempt={wsRetry} />
      </div>

      {isOfflineMode && routeStops.length === 0 ? (
        <div className="bg-white border border-brandBorder rounded-3xl p-6 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-3 shadow-sm">
          <AlertTriangle className="w-10 h-10 text-brandAmber mb-1" />
          <p className="font-bold text-sm text-brandNavy font-bold">Telemetry offline</p>
          <p className="text-xs text-brandTextSecondary max-w-xs font-semibold">
            We are having trouble accessing the coordinates server. Real-time updates will resume as soon as connection is restored.
          </p>
        </div>
      ) : (
        <>
          {/* Pre-Trip Status Banner */}
          {isNotActive && (
            <div className="p-3.5 bg-brandBlue/10 border border-brandBlue/20 rounded-2xl flex items-center justify-between text-brandBlue text-xs font-bold shadow-sm">
              <div className="flex items-center gap-2.5">
                <Clock className="w-4 h-4 shrink-0 text-brandBlue" />
                <span>🚌 BUS NOT STARTED YET — Showing planned route preview & your home stop</span>
              </div>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-brandBlue text-white font-black uppercase tracking-wider">
                Pre-Trip
              </span>
            </div>
          )}

          {/* Active Trip but GPS Unavailable Banner */}
          {!isNotActive && (!busLocation || gpsStatus === 'UNAVAILABLE') && (
            <div className="p-3.5 bg-slate-900 border-2 border-slate-700 text-white rounded-2xl flex items-center justify-between text-xs font-bold shadow-lg">
              <div className="flex items-center gap-2.5">
                <MapPin className="w-5 h-5 shrink-0 text-amber-400 animate-pulse" />
                <div>
                  <p className="font-extrabold text-white">🟢 TRIP STARTED / 📍 WAITING FOR DRIVER GPS</p>
                  <p className="text-[10px] text-slate-300 font-medium mt-0.5">
                    Waiting for driver phone GPS telemetry. Route start coordinates are never shown as fake bus location.
                  </p>
                </div>
              </div>
              <span className="text-[9px] px-2 py-1 rounded-full bg-slate-800 text-amber-400 border border-slate-600 font-black uppercase tracking-wider">
                Awaiting GPS
              </span>
            </div>
          )}

          {/* Active Alert Banner (Deviation / Stale) */}
          {activeAlert && (
            <div className="p-4 bg-brandAmber/15 border border-brandAmber/30 rounded-2xl flex items-center gap-3 text-brandAmber text-xs font-bold shadow-sm">
              <AlertTriangle className="w-5 h-5 shrink-0" />
              <span>{activeAlert}</span>
            </div>
          )}

          {/* Map Display Frame */}
          <div className="bg-white border border-brandBorder rounded-3xl overflow-hidden h-[48vh] sm:h-[54vh] shadow-sm relative z-0">
            <GoogleSmartBusMap
              routePolyline={polylineCoords}
              startPoint={startPointObj}
              endPoint={endPointObj}
              stops={mapStops}
              busLocation={
                busLocation && gpsStatus !== 'UNAVAILABLE'
                  ? {
                      latitude: busLocation.lat,
                      longitude: busLocation.lng,
                      heading: busLocation.heading,
                      speed: busLocation.speed,
                      gpsStatus: (gpsStatus === 'GPS_STALE' || busDetails?.gpsStale) ? 'GPS_STALE' : 'LIVE',
                      busNumber: busDetails?.busNumber
                    }
                  : null
              }
              homeStopId={preferredStopId}
              isOffRoute={busDetails?.offRoute}
              followDriver={false}
              showRecenterButton={true}
              height="100%"
            />
          </div>

          {/* Location Analytics Status Card */}
          {busDetails && (
            <div className="bg-white border border-brandBorder rounded-3xl p-5 flex flex-col gap-4 shadow-sm text-brandTextPrimary">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="font-extrabold text-brandNavy text-base">
                    {busDetails.busNumber}
                  </h3>
                  <p className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider mt-0.5">
                    {busDetails.routeName}
                  </p>
                </div>
                <div className="text-right flex flex-col items-end">
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px] text-brandTextSecondary font-bold">Status</span>
                    {isNotActive || busDetails.status === 'SCHEDULED' || busDetails.status === 'COMPLETED' ? (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-300">
                        {busDetails.status === 'COMPLETED' ? 'TRIP COMPLETED' : 'TRIP NOT STARTED'}
                      </span>
                    ) : busDetails.status === 'PAUSED' ? (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                        🟡 TRIP PAUSED
                      </span>
                    ) : busDetails.offRoute ? (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-brandRed/15 text-brandRed border border-brandRed/30">
                        🚨 OFF ROUTE
                      </span>
                    ) : (!busLocation || gpsStatus === 'UNAVAILABLE') ? (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-300">
                        📍 LOCATION UNAVAILABLE
                      </span>
                    ) : (busDetails.gpsStale || gpsStatus === 'GPS_STALE') ? (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                        ⚠️ GPS STALE — Last known
                      </span>
                    ) : busDetails.etaStatus === 'DELAYED' ? (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-brandAmber/15 text-brandAmber border border-brandAmber/30">
                        DELAYED {busDetails.delayMinutes ? `(+${busDetails.delayMinutes}m)` : ''}
                      </span>
                    ) : (
                      <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-brandGreen/15 text-brandGreen border border-brandGreen/30">
                        🟢 LIVE
                      </span>
                    )}
                  </div>
                  <p className="text-xl font-black text-brandBlue mt-1">
                    {isNotActive || busDetails.status === 'SCHEDULED'
                      ? 'Scheduled'
                      : (!busLocation || gpsStatus === 'UNAVAILABLE')
                      ? 'Awaiting GPS'
                      : busDetails.eta >= 0
                      ? `${busDetails.eta} min`
                      : 'Calculating...'}
                  </p>
                  {busDetails.distanceMeters != null && !isNotActive && busLocation && gpsStatus !== 'UNAVAILABLE' && (
                    <p className="text-[10px] text-brandTextSecondary font-semibold">
                      {(busDetails.distanceMeters / 1000).toFixed(1)} km remaining
                    </p>
                  )}
                </div>
              </div>

              {/* Sequential Route Progress Bar */}
              {routeProgress && (
                <div className="space-y-1.5 pt-2 border-t border-brandBorder">
                  <div className="flex items-center justify-between text-[10px] font-extrabold text-brandTextSecondary">
                    <span>Route Progress: {routeProgress.passedStops} of {routeProgress.totalStops} Stops Passed</span>
                    <span className="text-brandBlue font-black">{routeProgress.progressPercent}%</span>
                  </div>
                  <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden border border-brandBorder/50">
                    <div
                      className="h-full bg-brandBlue rounded-full transition-all duration-500"
                      style={{ width: `${Math.min(100, Math.max(0, routeProgress.progressPercent))}%` }}
                    ></div>
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4 border-t border-brandBorder pt-4 text-xs">
                <div className="flex items-center gap-2">
                  <div className="p-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextSecondary">
                    <MapPin className="w-4 h-4" />
                  </div>
                  <div>
                    <p className="text-[9px] text-brandTextSecondary font-bold">
                      {isNotActive ? 'Departure Depot' : 'Next Stop'}
                    </p>
                    <p className="font-bold text-brandNavy">{busDetails.nextStop || 'None'}</p>
                  </div>
                </div>
                
                <div className="flex items-center gap-2">
                  <div className="p-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextSecondary">
                    <Compass className="w-4 h-4" />
                  </div>
                  <div>
                    <p className="text-[9px] text-brandTextSecondary font-bold">Your Stop</p>
                    <p className="font-bold text-amber-600">
                      {preferredStopName || 'Not configured'}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Upcoming Stops & Dynamic ETAs Card */}
          {upcomingStops.length > 0 && !isNotActive && (
            <div className="bg-white border border-brandBorder rounded-3xl p-5 flex flex-col gap-4 shadow-sm text-brandTextPrimary">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Clock className="w-4 h-4 text-brandBlue" />
                  <h4 className="font-extrabold text-sm text-brandNavy uppercase tracking-wider">Upcoming Stops & Arrival Times</h4>
                </div>
                <span className="text-[10px] font-bold text-brandTextSecondary px-2 py-0.5 rounded-full bg-brandBg border border-brandBorder">
                  {upcomingStops.length} upcoming
                </span>
              </div>

              <div className="divide-y divide-brandBorder/60 max-h-64 overflow-y-auto pr-1">
                {upcomingStops.map((stop) => {
                  const isHome = (preferredStopId && stop.stopId === preferredStopId) ||
                                 (preferredStopName && stop.stopName === preferredStopName);
                  return (
                    <div key={stop.stopId} className="py-2.5 flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2.5">
                        <span className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-black border ${
                          stop.isNext ? 'bg-brandBlue text-white border-brandBlue ring-2 ring-brandBlue/30 animate-pulse' :
                          isHome ? 'bg-amber-500 text-white border-amber-400' :
                          'bg-slate-100 text-slate-600 border-slate-300'
                        }`}>
                          {stop.isNext ? '→' : stop.sequence}
                        </span>
                        <div>
                          <div className="flex items-center gap-1.5">
                            <span className={`font-bold ${stop.isNext ? 'text-brandBlue' : 'text-brandNavy'}`}>
                              {stop.stopName}
                            </span>
                            {isHome && (
                              <span className="text-[8px] font-black px-1.5 py-0.2 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                                ⭐ Your Stop
                              </span>
                            )}
                            {stop.isNext && (
                              <span className="text-[8px] font-black px-1.5 py-0.2 rounded-full bg-blue-100 text-blue-800 uppercase">
                                Next
                              </span>
                            )}
                          </div>
                          <p className="text-[10px] text-brandTextSecondary font-medium">
                            {(stop.distanceMeters / 1000).toFixed(1)} km away
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className="font-black text-brandBlue text-sm">
                          {stop.etaMinutes <= 0 ? 'Arriving' : `${stop.etaMinutes} min`}
                        </p>
                        {stop.estimatedArrivalTime && (
                          <p className="text-[10px] text-brandTextSecondary font-bold">
                            ~{stop.estimatedArrivalTime}
                          </p>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};
