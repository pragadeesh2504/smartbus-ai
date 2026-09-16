import React, { useEffect, useState, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Navigation,
  Radio,
  Clock,
  Gauge,
  MapPin,
  Pause,
  Play,
  Square,
  ShieldAlert,
  Wrench,
  AlertTriangle,
  ArrowRight,
  Shield,
  Bus,
  Calendar,
  ChevronRight,
  Compass,
  Bell,
  CheckCircle2,
  CornerUpRight,
  Crosshair
} from 'lucide-react';
import { SmartWebSocketClient } from '../../services/websocketService';
import { RouteSimulatorPanel } from '../../components/simulation/RouteSimulatorPanel';
import { GoogleSmartBusMap, MapStop } from '../../components/map/GoogleSmartBusMap';
import { MapService } from '../../services/MapService';
import { ErrorBoundary } from '../../components/common/ErrorBoundary';

const TripScreenContent: React.FC = () => {
  const navigate = useNavigate();
  const [trip, setTrip] = useState<any>(null);
  const [assignment, setAssignment] = useState<any>(null);
  const [routeProgress, setRouteProgress] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [startTripLoading, setStartTripLoading] = useState(false);
  const [driverCoords, setDriverCoords] = useState<{ lat: number; lng: number; heading: number } | null>(null);

  // Navigation application UX state
  const [followDriver, setFollowDriver] = useState(true);
  const [arrivalAcknowledgedStopId, setArrivalAcknowledgedStopId] = useState<string | null>(null);
  const [recoveryPolyline, setRecoveryPolyline] = useState<[number, number][]>([]);
  const [gpsStatus, setGpsStatus] = useState<'LIVE' | 'GPS_STALE' | 'UNAVAILABLE'>('UNAVAILABLE');

  // Live telemetry counters
  const [elapsedMins, setElapsedMins] = useState(0);
  const [elapsedSecs, setElapsedSecs] = useState(0);
  const [distance, setDistance] = useState(0.0);
  const [speed, setSpeed] = useState(0.0);
  const [accuracy, setAccuracy] = useState(8.0);
  const [trackingSource, setTrackingSource] = useState('GPS_DEVICE');
  const [lastGpsTimestamp, setLastGpsTimestamp] = useState<string | null>(null);

  // Breakdown Modal state
  const [breakdownOpen, setBreakdownOpen] = useState(false);
  const [issueType, setIssueType] = useState('ENGINE');
  const [description, setDescription] = useState('');
  const [breakdownLoading, setBreakdownLoading] = useState(false);

  // Pause Modal state
  const [pauseOpen, setPauseOpen] = useState(false);
  const [pauseReason, setPauseReason] = useState('Traffic');
  const [pauseLoading, setPauseLoading] = useState(false);



  const watchIdRef = useRef<number | null>(null);
  const timerRef = useRef<number | null>(null);
  const pollRef = useRef<number | null>(null);
  const clientRef = useRef<SmartWebSocketClient | null>(null);
  const lastTimestampRef = useRef<number>(0);

  const isPaused = trip?.status === 'PAUSED';

  const allStops = useMemo(() => {
    if (!routeProgress) return [];
    return [...(routeProgress.visitedStops || []), ...(routeProgress.remainingStops || [])];
  }, [routeProgress]);

  const mapStops: MapStop[] = useMemo(() => {
    if (!routeProgress) return [];
    const list: MapStop[] = [];
    (routeProgress.visitedStops || []).forEach((s: any) => {
      const lat = Number(s?.latitude);
      const lng = Number(s?.longitude);
      if (!isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0) {
        list.push({
          id: s.stopId || String(s.sequence || Math.random()),
          stopName: s.stopName || 'Stop',
          latitude: lat,
          longitude: lng,
          sequenceNumber: s.sequence,
          status: 'PASSED',
          arrivalTime: s.arrivalTime
        });
      }
    });
    (routeProgress.remainingStops || []).forEach((s: any, idx: number) => {
      const lat = Number(s?.latitude);
      const lng = Number(s?.longitude);
      if (!isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0) {
        list.push({
          id: s.stopId || String(s.sequence || Math.random()),
          stopName: s.stopName || 'Stop',
          latitude: lat,
          longitude: lng,
          sequenceNumber: s.sequence,
          status: idx === 0 ? 'NEXT' : 'UPCOMING',
          arrivalTime: s.arrivalTime
        });
      }
    });
    return list;
  }, [routeProgress]);

  const activePolylineCoords: [number, number][] = useMemo(() => {
    if (routeProgress?.polyline) {
      try {
        const parsed = JSON.parse(routeProgress.polyline);
        if (Array.isArray(parsed) && parsed.length > 1) {
          return parsed;
        }
      } catch (e) {}
    }
    const validStops = allStops.filter((s: any) => {
      const lat = Number(s?.latitude);
      const lng = Number(s?.longitude);
      return !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0;
    });
    if (validStops.length > 1) {
      return validStops.map((s: any) => [Number(s.latitude), Number(s.longitude)] as [number, number]);
    }
    return [];
  }, [routeProgress, allStops]);

  const startTerminalPoint = useMemo(() => {
    const sLat = Number(routeProgress?.startLatitude);
    const sLng = Number(routeProgress?.startLongitude);
    if (!isNaN(sLat) && !isNaN(sLng) && sLat !== 0 && sLng !== 0) {
      return {
        name: routeProgress?.routeName ? `${routeProgress.routeName} (Start)` : 'Route Start',
        latitude: sLat,
        longitude: sLng
      };
    }
    const validStops = allStops.filter((s: any) => {
      const lat = Number(s?.latitude);
      const lng = Number(s?.longitude);
      return !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0;
    });
    if (validStops.length > 0) {
      return { name: validStops[0].stopName || 'Start', latitude: Number(validStops[0].latitude), longitude: Number(validStops[0].longitude) };
    }
    return null;
  }, [routeProgress, allStops]);

  const endTerminalPoint = useMemo(() => {
    const eLat = Number(routeProgress?.endLatitude);
    const eLng = Number(routeProgress?.endLongitude);
    if (!isNaN(eLat) && !isNaN(eLng) && eLat !== 0 && eLng !== 0) {
      return {
        name: routeProgress?.routeName ? `${routeProgress.routeName} (Destination)` : 'Route Destination',
        latitude: eLat,
        longitude: eLng
      };
    }
    const validStops = allStops.filter((s: any) => {
      const lat = Number(s?.latitude);
      const lng = Number(s?.longitude);
      return !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0;
    });
    if (validStops.length > 0) {
      const last = validStops[validStops.length - 1];
      return { name: last.stopName || 'Destination', latitude: Number(last.latitude), longitude: Number(last.longitude) };
    }
    return null;
  }, [routeProgress, allStops]);

  // IndexedDB setup
  const openDb = () => {
    return new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open("SmartBusOfflineDb", 1);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains("offline_locations")) {
          db.createObjectStore("offline_locations", { keyPath: "id", autoIncrement: true });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  };

  const queueLocationOffline = async (loc: any) => {
    try {
      const db = await openDb();
      const tx = db.transaction("offline_locations", "readwrite");
      const store = tx.objectStore("offline_locations");
      store.add(loc);
    } catch (e) {
      console.error('IndexedDB queue failed', e);
    }
  };

  const getQueuedLocations = async (): Promise<any[]> => {
    try {
      const db = await openDb();
      return new Promise((resolve) => {
        const tx = db.transaction("offline_locations", "readonly");
        const store = tx.objectStore("offline_locations");
        const req = store.getAll();
        req.onsuccess = () => resolve(req.result);
      });
    } catch (e) {
      return [];
    }
  };

  const clearQueuedLocations = async () => {
    try {
      const db = await openDb();
      const tx = db.transaction("offline_locations", "readwrite");
      const store = tx.objectStore("offline_locations");
      store.clear();
    } catch (e) {
      console.error(e);
    }
  };

  // Sync Offline Queue when network is restored
  const syncOfflineQueue = async () => {
    if (!navigator.onLine) return;
    const queued = await getQueuedLocations();
    if (queued.length === 0) return;

    try {
      await axios.post('/api/driver/location/batch', queued);
      await clearQueuedLocations();
      console.log('Synchronized cached offline location packets.');
    } catch (e) {
      console.error('Failed to sync offline location packets', e);
    }
  };

  const fetchTripDetails = async () => {
    try {
      // Find current active trip directly
      let activeTripData: any = null;
      try {
        const activeRes = await axios.get('/api/driver/trips/active');
        if (activeRes.status === 200 && activeRes.data && activeRes.data.tripId) {
          activeTripData = activeRes.data;
        }
      } catch (err) {
        // Fallback to dashboard activeTripId
      }

      let tripId = activeTripData ? activeTripData.tripId : null;
      if (!tripId) {
        try {
          const dashRes = await axios.get('/api/driver/dashboard');
          tripId = dashRes.data?.activeTripId;
        } catch (dashErr) {
          console.warn('Dashboard trip lookup skipped', dashErr);
        }
      }

      if (!tripId) {
        // PRE-TRIP PREVIEW: load today's assignment details
        try {
          const assignRes = await axios.get('/api/driver/assignments/today');
          if (assignRes.data && assignRes.data.length > 0) {
            const currentAssignment = assignRes.data[0];
            const detailRes = await axios.get(`/api/driver/assignments/${currentAssignment.assignmentId}`);
            setAssignment(detailRes.data);
          } else {
            const dashRes = await axios.get('/api/driver/dashboard');
            if (dashRes.data && dashRes.data.busNumber) {
              setAssignment({
                busNumber: dashRes.data.busNumber,
                busCode: dashRes.data.busCode,
                routeName: dashRes.data.routeName,
                departureTime: dashRes.data.departureTime,
                scheduleId: dashRes.data.scheduleId,
                stops: []
              });
            } else {
              setAssignment(null);
            }
          }
        } catch (err) {
          console.warn('Failed to load assignment for pre-trip preview', err);
          setAssignment(null);
        }
        setTrip(null);
        setRouteProgress(null);
        setDriverCoords(null);
        setGpsStatus('UNAVAILABLE');
        return;
      }

      // If active trip was retrieved
      if (!activeTripData) {
        const tripsRes = await axios.get('/api/driver/trips');
        activeTripData = tripsRes.data.find((t: any) => t.tripId === tripId);
      }

      if (activeTripData) {
        setTrip(activeTripData);
        setDistance(activeTripData.distance || 0.0);
        setAssignment(null);
      }

      // Fetch stop progress geofences and current telemetry
      const routeRes = await axios.get('/api/driver/route');
      setRouteProgress(routeRes.data);

      if (routeRes.data) {
        const d = routeRes.data;
        if (d.currentLatitude != null && d.currentLongitude != null) {
          setDriverCoords({
            lat: d.currentLatitude,
            lng: d.currentLongitude,
            heading: d.heading || 0
          });
          setGpsStatus(d.gpsStatus || 'LIVE');
          if (d.speed != null) setSpeed(d.speed);
          if (d.accuracy != null) setAccuracy(d.accuracy);
          if (d.trackingSource) setTrackingSource(d.trackingSource);
          if (d.lastGpsTimestamp || d.lastTimestamp) setLastGpsTimestamp(d.lastGpsTimestamp || d.lastTimestamp);
        } else {
          setDriverCoords(null);
          setGpsStatus('UNAVAILABLE');
          setLastGpsTimestamp(null);
        }
      }
      setError(null);
    } catch (e: any) {
      console.error('Failed to fetch trip details:', e);
      setError(e.response?.data?.message || 'Unable to load trip details');
    } finally {
      setLoading(false);
    }
  };

  const handleStartTrip = async (scheduleId: string) => {
    if (!scheduleId) return;
    setStartTripLoading(true);
    try {
      await axios.post(`/api/driver/trips/${scheduleId}/start`);
      await fetchTripDetails();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to start trip');
    } finally {
      setStartTripLoading(false);
    }
  };

  useEffect(() => {
    fetchTripDetails();

    // Connect WebSocket for trip status updates and route configuration changes
    const client = new SmartWebSocketClient({
      subscriptions: [{ type: 'SUBSCRIBE_CLIENT' }],
      onMessage: (data) => {
        if (!data || !data.type) return;
        if (data.type === 'ROUTE_UPDATED') {
          console.log('Driver portal received ROUTE_UPDATED event:', data);
          // Reload assigned route / preview when Admin updates route or stops
          fetchTripDetails();
        }
        if (data.type === 'TRIP_STATUS_UPDATE') {
          if (trip && (data.tripId === trip.tripId || data.busNumber === trip.busNumber)) {
            if (data.status === 'PAUSED') {
              setTrip((prev: any) => prev ? { ...prev, status: 'PAUSED' } : null);
            } else if (data.status === 'IN_PROGRESS') {
              setTrip((prev: any) => prev ? { ...prev, status: 'IN_PROGRESS' } : null);
            } else if (data.status === 'COMPLETED') {
              fetchTripDetails();
            }
          }
        }
        if ((data.type === 'BUS_LOCATION_UPDATE' || data.type === 'TELEMETRY') &&
            trip && (data.tripId === trip.tripId || data.busNumber === trip.busNumber || data.busId === trip.busId)) {
          if (data.latitude != null && data.longitude != null) {
            setDriverCoords({
              lat: data.latitude,
              lng: data.longitude,
              heading: data.heading || 0
            });
            setGpsStatus('LIVE');
            if (data.speed != null) setSpeed(data.speed);
            if (data.accuracy != null) setAccuracy(data.accuracy);
            if (data.trackingSource) setTrackingSource(data.trackingSource);
          }
        }
        if (data.type === 'GPS_STALE' &&
            trip && (data.tripId === trip.tripId || data.busNumber === trip.busNumber || data.busId === trip.busId)) {
          setGpsStatus('GPS_STALE');
        }
      }
    });
    clientRef.current = client;
    client.connect();

    // Start timer counter
    timerRef.current = window.setInterval(() => {
      setElapsedSecs((prev) => {
        if (prev === 59) {
          setElapsedMins((m) => m + 1);
          return 0;
        }
        return prev + 1;
      });
    }, 1000);

    // Network status listener
    window.addEventListener('online', syncOfflineQueue);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (pollRef.current) clearInterval(pollRef.current);
      if (watchIdRef.current) navigator.geolocation.clearWatch(watchIdRef.current);
      if (clientRef.current) clientRef.current.disconnect();
      window.removeEventListener('online', syncOfflineQueue);
    };
  }, []);

  // Set up high accuracy GPS streaming watcher
  useEffect(() => {
    if (loading || !trip) return;

    // Set polling trigger for route geofence validation
    pollRef.current = window.setInterval(async () => {
      try {
        const routeRes = await axios.get('/api/driver/route');
        setRouteProgress(routeRes.data);
      } catch (err) {
        console.warn('Geofence poll failed', err);
      }
    }, 10000);

    if (navigator.geolocation) {
      watchIdRef.current = navigator.geolocation.watchPosition(
        async (position) => {
          const { latitude, longitude, speed: geoSpeed, accuracy: geoAccuracy } = position.coords;
          
          setSpeed(geoSpeed ? geoSpeed * 3.6 : 0.0); // Convert m/s to km/h
          setAccuracy(geoAccuracy || 8.0);
          setTrackingSource('MOBILE_PORTAL');
          setDriverCoords({
            lat: latitude,
            lng: longitude,
            heading: position.coords.heading || 0
          });

          const headingVal = (position.coords.heading != null && !isNaN(position.coords.heading) && position.coords.heading >= 0) ? position.coords.heading : 0.0;

          const packet = {
            latitude,
            longitude,
            speed: geoSpeed ? geoSpeed * 3.6 : 0.0,
            heading: headingVal,
            accuracy: geoAccuracy || 8.0,
            timestamp: new Date().toISOString()
          };

          if (navigator.onLine) {
            try {
              // Flush telemetry packet directly to route coordinates receiver
              const res = await axios.post('/api/driver/location', packet);
              setLastGpsTimestamp(packet.timestamp);
              setGpsStatus('LIVE');
              if (res.data.success && res.data.data) {
                // Update live accumulated distance
                setDistance(res.data.data.distance || 0.0);
              }
              // Try syncing offline backlog
              syncOfflineQueue();
            } catch (err) {
              console.warn('Network streaming failed, caching locally...', err);
              queueLocationOffline(packet);
            }
          } else {
            console.log('Mobile portal offline. Caching coordinates packet.');
            queueLocationOffline(packet);
          }
        },
        (error) => {
          console.warn('Geolocation sensor failure', error);
        },
        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 0
        }
      );
    }

  }, [loading, trip]);

  // Dynamic off-route navigation recovery calculation
  useEffect(() => {
    if (routeProgress?.offRoute && driverCoords && routeProgress?.nextStop) {
      MapService.calculateRecoveryRoute(
        { lat: driverCoords.lat, lng: driverCoords.lng },
        { lat: routeProgress.nextStop.latitude, lng: routeProgress.nextStop.longitude }
      ).then((pts) => {
        if (pts && pts.length > 0) setRecoveryPolyline(pts);
      }).catch((err) => {
        console.warn('Emergency recovery route failed', err);
      });
    } else {
      setRecoveryPolyline([]);
    }
  }, [routeProgress?.offRoute, routeProgress?.nextStop?.latitude, routeProgress?.nextStop?.longitude, driverCoords?.lat, driverCoords?.lng]);

  const handlePause = async () => {
    setPauseLoading(true);
    try {
      await axios.post('/api/driver/trip/pause', null, {
        params: { reason: pauseReason }
      });
      setTrip((prev: any) => ({ ...prev, status: 'PAUSED' }));
      setPauseOpen(false);
    } catch (e) {
      alert('Failed to pause trip.');
    } finally {
      setPauseLoading(false);
    }
  };

  const handleResume = async () => {
    try {
      await axios.post('/api/driver/trip/resume');
      setTrip((prev: any) => ({ ...prev, status: 'IN_PROGRESS' }));
    } catch (e) {
      alert('Failed to resume trip.');
    }
  };

  const handleEndTrip = async () => {
    if (!window.confirm('Do you want to finalize this duty assignment and close the trip?')) return;
    try {
      await axios.post('/api/driver/trip/end');
      alert('Trip successfully completed and archived.');
      navigate('/driver/dashboard');
    } catch (e) {
      alert('Failed to end trip.');
    }
  };

  const triggerSos = async () => {
    if (!window.confirm('WARNING: Are you sure you want to trigger emergency SOS?')) return;
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

  if (error) {
    return (
      <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center flex flex-col items-center justify-center gap-4 shadow-sm my-4">
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 rounded-full text-brandRed">
          <AlertTriangle className="w-8 h-8" />
        </div>
        <div>
          <h3 className="font-bold text-base text-brandNavy">Unable to Load Trip</h3>
          <p className="text-xs text-brandTextSecondary max-w-xs mx-auto mt-1 font-semibold">
            {error}
          </p>
        </div>
        <button
          onClick={() => {
            setLoading(true);
            setError(null);
            fetchTripDetails();
          }}
          className="text-xs bg-brandBlue hover:bg-brandBlue/90 text-white font-bold px-6 py-2.5 rounded-xl shadow-sm transition-all"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!trip) {
    return (
      <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-4 shadow-sm my-4">
        <div className="p-4 bg-brandBg border border-brandBorder rounded-full text-brandBlue">
          <Bus className="w-8 h-8" />
        </div>
        <div>
          <h2 className="font-extrabold text-base text-brandNavy uppercase tracking-wider">NO ACTIVE TRIP</h2>
          <p className="text-xs text-brandTextSecondary max-w-sm mx-auto mt-1.5 font-semibold">
            No active trip is currently running. Please go to your Dashboard to start an assigned trip.
          </p>
        </div>
        <button
          onClick={() => navigate('/driver/dashboard')}
          className="text-xs bg-brandBlue hover:bg-brandBlue/90 text-white font-extrabold px-6 py-3 rounded-xl shadow-md transition-all uppercase tracking-wider"
        >
          Back to Dashboard
        </button>
      </div>
    );
  }



  return (
    <div className="space-y-6">
      {/* active trip header */}
      <div className="bg-white border border-brandBorder rounded-3xl p-6 shadow-sm relative overflow-hidden">
        <div className="absolute top-0 right-0 w-24 h-24 bg-brandBlue/5 rounded-full blur-2xl"></div>
        <div className="flex items-center gap-3 mb-4">
          <div className="p-2 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
            <Bus className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-brandNavy uppercase">{trip?.busNumber}</h2>
            <p className="text-[10px] text-brandTextSecondary font-bold">{trip?.routeName}</p>
          </div>
        </div>

        {/* Current status display */}
        <div className="flex items-center justify-between p-3.5 bg-brandBg border border-brandBorder rounded-2xl">
          <div className="flex items-center gap-2 text-xs font-bold">
            <span className="w-2.5 h-2.5 bg-brandTeal rounded-full animate-ping"></span>
            <span className="text-brandTextPrimary uppercase tracking-wider">LIVE TELEMETRY STREAMING</span>
          </div>
          <span className={`px-2.5 py-1 rounded-full font-bold text-[10px] uppercase shadow-sm ${
            isPaused ? 'bg-brandAmber/15 text-brandAmber border border-brandAmber/25' : 'bg-brandGreen/15 text-brandGreen border border-brandGreen/25'
          }`}>
            {trip?.status}
          </span>
        </div>

        {/* Route Deviation Critical Warning Banner */}
        {routeProgress?.offRoute && (
          <div className="mt-4 p-4 bg-brandRed/15 border-2 border-brandRed rounded-2xl flex items-center gap-3 text-brandRed font-bold text-xs shadow-sm animate-pulse">
            <AlertTriangle className="w-6 h-6 shrink-0 text-brandRed" />
            <div>
              <p className="uppercase tracking-wider text-[11px] font-black">⚠ ROUTE DEVIATION DETECTED</p>
              <p className="text-[10px] font-semibold text-brandRed/90 mt-0.5">
                Bus is {Math.round(routeProgress.deviationMeters || 0)}m off the assigned route path. Please return to route immediately.
              </p>
            </div>
          </div>
        )}

        {/* Geofencing progress milestone card */}
        {routeProgress && (
          <div className="mt-4 pt-4 border-t border-brandBorder space-y-3 font-semibold text-xs">
            {routeProgress.currentStop && (
              <div className="flex items-center gap-3">
                <div className="w-6 h-6 rounded-full bg-brandGreen/10 border border-brandGreen/20 flex items-center justify-center text-brandGreen text-[10px] font-extrabold shadow-sm">
                  ✓
                </div>
                <div>
                  <p className="text-[9px] text-brandTextSecondary uppercase font-bold">Current Location</p>
                  <p className="font-bold text-brandNavy">{routeProgress.currentStop.stopName}</p>
                </div>
              </div>
            )}
            {routeProgress.nextStop && (
              <div className="flex items-center justify-between pt-2.5 border-t border-brandBorder">
                <div className="flex items-center gap-3">
                  <div className="w-6 h-6 rounded-full bg-brandBlue/10 border border-brandBlue/20 flex items-center justify-center text-brandBlue text-[10px] font-extrabold shadow-sm">
                    →
                  </div>
                  <div>
                    <p className="text-[9px] text-brandTextSecondary uppercase font-bold">Next Stop Milestone</p>
                    <p className="font-bold text-brandNavy">{routeProgress.nextStop.stopName}</p>
                    {routeProgress.nextStopDistanceMeters != null && (
                      <p className="text-[9px] text-brandTextSecondary font-medium">
                        {(routeProgress.nextStopDistanceMeters / 1000).toFixed(1)} km away
                      </p>
                    )}
                  </div>
                </div>

                {/* Next Stop ETA & Delay status */}
                <div className="text-right">
                  <span className="text-[9px] text-brandTextSecondary uppercase font-bold">Est. Arrival</span>
                  <p className="text-sm font-black text-brandBlue">
                    {routeProgress.nextStopEtaMinutes != null ? `${routeProgress.nextStopEtaMinutes} min` : 'Calculating...'}
                  </p>
                  {routeProgress.delayMinutes != null && routeProgress.delayMinutes > 0 && (
                    <span className="inline-block text-[8px] font-extrabold px-1.5 py-0.5 rounded-full bg-brandAmber/15 text-brandAmber border border-brandAmber/25">
                      +{routeProgress.delayMinutes}m DELAY
                    </span>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Local-only Testing Route Simulator */}
      {trip && trip.tripId && (
        <ErrorBoundary
          componentName="Route Simulator Panel"
          fallbackTitle="Simulator Unavailable"
          fallbackMessage="The simulation panel encountered an issue. Live device GPS remains active."
        >
          <RouteSimulatorPanel
            tripId={trip.tripId}
            busNumber={trip.busNumber || 'Assigned Bus'}
            routeName={trip.routeName || 'Assigned Route'}
            routeId={trip.routeId || ''}
            tripStatus={trip.status || 'IN_PROGRESS'}
            onLocationSent={(lat, lng, spd, hdg) => {
              setDriverCoords({ lat, lng, heading: hdg });
              setSpeed(spd);
            }}
            onStatusChange={(newStatus) => {
              setTrip((prev: any) => prev ? { ...prev, status: newStatus } : null);
            }}
          />
        </ErrorBoundary>
      )}

      {/* Google-Maps-Style Next Stop Turn Guidance Banner */}
      <div className="bg-slate-950 text-white rounded-3xl p-5 shadow-2xl border-2 border-emerald-500/40 relative overflow-hidden flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-2xl bg-emerald-500 text-slate-950 flex flex-col items-center justify-center font-black shadow-lg shrink-0">
            <CornerUpRight className="w-7 h-7 text-slate-950" />
            <span className="text-[9px] uppercase tracking-wider font-black">NAV</span>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-400 font-extrabold text-[10px] tracking-wider uppercase border border-emerald-500/30">
                {routeProgress?.nextStopDistanceMeters != null
                  ? routeProgress.nextStopDistanceMeters < 1000
                    ? `${Math.round(routeProgress.nextStopDistanceMeters)} m`
                    : `${(routeProgress.nextStopDistanceMeters / 1000).toFixed(1)} km`
                  : 'Approaching'}
              </span>
              {routeProgress?.nextStopEtaMinutes != null && (
                <span className="text-xs text-slate-300 font-bold">
                  • ETA ~{routeProgress.nextStopEtaMinutes} min
                </span>
              )}
            </div>
            <h2 className="text-xl md:text-2xl font-black text-white tracking-tight mt-1">
              {routeProgress?.nextStop?.stopName || 'Destination Terminal'}
            </h2>
            <p className="text-xs text-slate-400 font-medium">
              Stop #{routeProgress?.nextStop?.sequence || 1} • {routeProgress?.offRoute ? '⚠️ Follow Amber Line to Recover Route' : 'Authorized Road Navigation'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 self-end md:self-auto">
          <button
            onClick={() => setFollowDriver(prev => !prev)}
            className={`px-3.5 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 border shadow-sm ${
              followDriver
                ? 'bg-blue-600 text-white border-blue-500 ring-2 ring-blue-400/30'
                : 'bg-slate-800 text-slate-300 border-slate-700 hover:bg-slate-700'
            }`}
          >
            <Crosshair className="w-4 h-4" />
            <span>{followDriver ? 'Tracking Vehicle' : 'Follow Camera'}</span>
          </button>
        </div>
      </div>

      {/* Stop Arrival Prominent Card (Within 150m) */}
      {routeProgress?.nextStop &&
        routeProgress.nextStopDistanceMeters != null &&
        routeProgress.nextStopDistanceMeters <= 150 &&
        arrivalAcknowledgedStopId !== routeProgress.nextStop.stopId && (
          <div className="p-4 bg-emerald-50 border-2 border-emerald-500 rounded-3xl flex items-center justify-between text-emerald-950 shadow-xl animate-pulse">
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-emerald-600 text-white rounded-2xl shadow">
                <Bell className="w-6 h-6 animate-spin" />
              </div>
              <div>
                <p className="text-[10px] font-black uppercase tracking-wider text-emerald-700">ARRIVING AT DESIGNATED STOP</p>
                <h3 className="text-base font-black text-emerald-950">{routeProgress.nextStop.stopName}</h3>
                <p className="text-xs text-emerald-800 font-semibold">
                  Within {Math.round(routeProgress.nextStopDistanceMeters)} meters — prepare for passenger boarding
                </p>
              </div>
            </div>
            <button
              onClick={() => setArrivalAcknowledgedStopId(routeProgress.nextStop.stopId)}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl shadow transition-all shrink-0 ml-2"
            >
              Acknowledge
            </button>
          </div>
      )}

      {/* Live Navigation Map Frame */}
      <div className="bg-white border border-brandBorder rounded-3xl overflow-hidden shadow-sm">
        <div className="p-3 bg-brandBg border-b border-brandBorder flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-bold text-brandNavy">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
            <span className="uppercase tracking-wider">LIVE TURN-BY-TURN FLEET NAVIGATION</span>
          </div>
          {routeProgress?.offRoute ? (
            <span className="text-[9px] font-extrabold px-2.5 py-1 rounded-full bg-brandRed text-white flex items-center gap-1">
              <AlertTriangle className="w-3 h-3" />
              OFF ROUTE ({Math.round(routeProgress.deviationMeters || 0)}m)
            </span>
          ) : (
            <span className="text-[9px] font-extrabold px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-300">
              ON AUTHORIZED ROUTE
            </span>
          )}
        </div>

        {/* Active Trip but GPS Unavailable / Stale Notification Banner */}
        {(!driverCoords || gpsStatus === 'UNAVAILABLE') ? (
          <div className="p-3.5 bg-slate-900 text-white border-2 border-slate-700 rounded-2xl flex items-center justify-between text-xs font-bold shadow-lg">
            <div className="flex items-center gap-2.5">
              <MapPin className="w-5 h-5 shrink-0 text-amber-400 animate-pulse" />
              <div>
                <p className="font-extrabold text-white">🟢 TRIP STARTED / 📍 WAITING FOR DRIVER GPS</p>
                <p className="text-[10px] text-slate-300 font-medium mt-0.5">
                  Vehicle marker will appear as soon as GPS telemetry or simulator stream is received.
                </p>
              </div>
            </div>
            <span className="text-[9px] px-2 py-1 rounded-full bg-slate-800 text-amber-400 font-black uppercase tracking-wider border border-slate-600">
              Awaiting GPS
            </span>
          </div>
        ) : gpsStatus === 'GPS_STALE' ? (
          <div className="p-3 bg-amber-500/20 border border-amber-500 rounded-2xl flex items-center justify-between text-amber-900 text-xs font-bold">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-600" />
              <span>⚠️ GPS STALE — Displaying last known vehicle coordinates</span>
            </div>
            <span className="text-[9px] px-2 py-0.5 rounded-full bg-amber-500 text-slate-950 font-black">STALE</span>
          </div>
        ) : null}

        <div className="h-80 sm:h-96 w-full relative z-0">
          <ErrorBoundary
            fallbackTitle="Map Display Unavailable"
            fallbackMessage="An error occurred while rendering the navigation map. Your telemetry and route instructions remain active."
          >
            <GoogleSmartBusMap
              routePolyline={activePolylineCoords}
              recoveryPolyline={recoveryPolyline}
              isOffRoute={routeProgress?.offRoute}
              stops={mapStops}
              startPoint={startTerminalPoint}
              endPoint={endTerminalPoint}
              busLocation={driverCoords && gpsStatus !== 'UNAVAILABLE' ? {
                latitude: driverCoords.lat,
                longitude: driverCoords.lng,
                heading: driverCoords.heading,
                speed,
                gpsStatus: gpsStatus,
                busNumber: trip?.busNumber
              } : null}
              followDriver={followDriver}
              showRecenterButton={true}
              height="100%"
            />
          </ErrorBoundary>
        </div>
      </div>

      {/* Temporary Development GPS Diagnostics Panel */}
      <div className="bg-slate-900 border border-slate-700 rounded-2xl p-4 text-slate-200 text-xs font-mono shadow-md">
        <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2 font-sans">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 text-emerald-400 animate-pulse" />
            <span className="font-bold text-slate-100 text-xs uppercase tracking-wider">Telemetry Diagnostic Stream</span>
          </div>
          <span className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase ${
            gpsStatus === 'LIVE' ? 'bg-emerald-500 text-slate-950' : gpsStatus === 'GPS_STALE' ? 'bg-amber-400 text-slate-950' : 'bg-slate-700 text-slate-300'
          }`}>
            {gpsStatus}
          </span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 text-[11px]">
          <div><span className="text-slate-400">Map Center Source:</span> <span className="text-emerald-400 font-bold">{driverCoords ? 'LIVE DRIVER LOCATION' : 'ROUTE EXTENTS'}</span></div>
          <div><span className="text-slate-400">Driver GPS Source:</span> <span className="text-blue-400 font-bold">{trackingSource}</span></div>
          <div><span className="text-slate-400">Bus Marker Source:</span> <span className="text-emerald-300 font-bold">{driverCoords && gpsStatus !== 'UNAVAILABLE' ? 'ACCEPTED DRIVER GPS' : 'NONE (PRE-GPS)'}</span></div>
          <div><span className="text-slate-400">GPS Status:</span> <span className="font-bold">{gpsStatus}</span></div>
          <div><span className="text-slate-400">Last GPS Timestamp:</span> <span className="text-amber-300">{lastGpsTimestamp ? new Date(lastGpsTimestamp).toLocaleTimeString() : 'Awaiting First GPS'}</span></div>
          <div><span className="text-slate-400">Lat/Lng:</span> {driverCoords && !isNaN(Number(driverCoords.lat)) && !isNaN(Number(driverCoords.lng)) ? `${Number(driverCoords.lat).toFixed(5)}, ${Number(driverCoords.lng).toFixed(5)}` : 'null'}</div>
          <div><span className="text-slate-400">Speed/Hdg:</span> {Math.round(Number(speed) || 0)} km/h • {Math.round(Number(driverCoords?.heading) || 0)}°</div>
          <div><span className="text-slate-400">Next Stop:</span> {routeProgress?.nextStop?.stopName || 'None'}</div>
        </div>
      </div>

      {/* Telemetry Stats Grid */}
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white border border-brandBorder rounded-2xl p-4 flex items-center gap-3 shadow-sm">
          <Clock className="w-5 h-5 text-brandBlue shrink-0" />
          <div>
            <p className="text-[9px] text-brandTextSecondary uppercase font-bold">Elapsed Time</p>
            <p className="text-sm font-extrabold text-brandNavy mt-0.5">
              {(Number(elapsedMins) || 0).toString().padStart(2, '0')}:{(Number(elapsedSecs) || 0).toString().padStart(2, '0')}
            </p>
          </div>
        </div>

        <div className="bg-white border border-brandBorder rounded-2xl p-4 flex items-center gap-3 shadow-sm">
          <Gauge className="w-5 h-5 text-brandBlue shrink-0" />
          <div>
            <p className="text-[9px] text-brandTextSecondary uppercase font-bold">Distance</p>
            <p className="text-sm font-extrabold text-brandNavy mt-0.5">{(Number(distance) || 0.0).toFixed(2)} km</p>
          </div>
        </div>

        <div className="bg-white border border-brandBorder rounded-2xl p-4 flex items-center gap-3 shadow-sm">
          <Navigation className="w-5 h-5 text-brandBlue shrink-0 animate-pulse" />
          <div>
            <p className="text-[9px] text-brandTextSecondary uppercase font-bold">Current Speed</p>
            <p className="text-sm font-extrabold text-brandNavy mt-0.5">{(Number(speed) || 0).toFixed(0)} km/h</p>
          </div>
        </div>

        <div className="bg-white border border-brandBorder rounded-2xl p-4 flex items-center gap-3 shadow-sm">
          <Radio className="w-5 h-5 text-brandBlue shrink-0" />
          <div>
            <p className="text-[9px] text-brandTextSecondary uppercase font-bold">accuracy (gps)</p>
            <p className="text-[10px] font-extrabold text-brandNavy mt-0.5 truncate max-w-[120px]">
              {(Number(accuracy) || 0).toFixed(0)}m ({(trackingSource || 'GPS').substring(0, 6)})
            </p>
          </div>
        </div>
      </div>

      {/* Route Sequence Progress UI */}
      {routeProgress && (
        <div className="bg-white border border-brandBorder rounded-3xl p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-xs font-extrabold uppercase tracking-wider text-brandNavy">Route Sequence Progress</h3>
              <p className="text-[10px] text-brandTextSecondary font-bold mt-0.5">
                Passed: {routeProgress.visitedStops?.length || 0} / {(routeProgress.visitedStops?.length || 0) + (routeProgress.remainingStops?.length || 0)} stops
              </p>
            </div>
            <div className="text-right">
              <span className="text-xs font-black text-brandBlue">
                {Math.round(routeProgress.progressPercentage || 0)}%
              </span>
              <p className="text-[9px] text-brandTextSecondary font-semibold">Completed</p>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden border border-brandBorder/50">
            <div
              className="h-full bg-brandBlue rounded-full transition-all duration-500"
              style={{ width: `${Math.min(100, Math.max(0, routeProgress.progressPercentage || 0))}%` }}
            ></div>
          </div>

          {/* Transition Milestone Summary: Prev Stop -> Next Stop */}
          <div className="p-3 bg-brandBg rounded-2xl border border-brandBorder flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <span className="text-[10px] font-bold text-brandTextSecondary uppercase">Progress:</span>
              <span className="font-extrabold text-brandNavy">
                {routeProgress.currentStop?.stopName || 'Start Point'}
              </span>
              <ArrowRight className="w-3.5 h-3.5 text-brandBlue" />
              <span className="font-extrabold text-brandBlue">
                {routeProgress.nextStop?.stopName || 'Destination'}
              </span>
            </div>
            {routeProgress.nextStopEtaMinutes != null && (
              <span className="text-[10px] font-black text-brandBlue bg-blue-50 px-2 py-0.5 rounded-full border border-blue-200">
                ETA: {routeProgress.nextStopEtaMinutes}m
              </span>
            )}
          </div>

          {/* Sequential Geofencing Stop Checklist */}
          <div className="relative pl-6 space-y-5 pt-2">
            <div className="absolute left-2.5 top-2.5 bottom-2.5 w-px bg-brandBorder"></div>
            {routeProgress.visitedStops?.map((stop: any) => (
              <div key={stop.stopId} className="relative flex items-center justify-between text-xs font-semibold">
                <span className="absolute -left-5 w-4 h-4 rounded-full bg-brandGreen border-2 border-white flex items-center justify-center text-[8px] text-white shadow-sm font-extrabold">✓</span>
                <span className="text-brandTextSecondary/70 line-through font-medium">{stop.stopName}</span>
                <span className="text-[10px] text-brandGreen font-bold">Passed</span>
              </div>
            ))}
            {routeProgress.remainingStops?.map((stop: any, idx: number) => {
              const isNext = idx === 0;
              return (
                <div key={stop.stopId} className="relative flex items-center justify-between text-xs font-semibold">
                  <span className={`absolute -left-[21px] w-4 h-4 rounded-full border-2 border-white shadow-sm ${
                    isNext ? 'bg-brandBlue animate-pulse ring-2 ring-brandBlue/30' : 'bg-brandBg border-brandBorder'
                  }`}></span>
                  <div>
                    <span className={isNext ? 'text-brandNavy font-extrabold' : 'text-brandTextSecondary/80 font-medium'}>
                      {stop.stopName}
                    </span>
                    <span className="text-[10px] text-brandTextSecondary ml-2 font-bold">#{stop.sequence}</span>
                  </div>
                  {isNext ? (
                    <span className="text-[9px] px-2 py-0.5 rounded-full bg-brandBlue text-white font-bold uppercase">
                      Next {routeProgress.nextStopEtaMinutes != null ? `(${routeProgress.nextStopEtaMinutes}m)` : ''}
                    </span>
                  ) : (
                    <span className="text-[10px] text-brandTextSecondary font-medium">Upcoming</span>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Action buttons */}
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-4">
          {isPaused ? (
            <button
              onClick={handleResume}
              className="h-14 bg-brandBlue hover:bg-brandBlue/90 text-white font-extrabold rounded-2xl flex items-center justify-center gap-2 shadow-md transition-all text-xs uppercase tracking-wider"
            >
              <Play className="w-5 h-5 fill-white" />
              Resume Duty
            </button>
          ) : (
            <button
              onClick={() => setPauseOpen(true)}
              className="h-14 bg-white border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-extrabold rounded-2xl flex items-center justify-center gap-2 shadow-sm transition-all text-xs uppercase tracking-wider"
            >
              <Pause className="w-5 h-5" />
              Pause Trip
            </button>
          )}

          <button
            onClick={() => setBreakdownOpen(true)}
            className="h-14 bg-white border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-extrabold rounded-2xl flex items-center justify-center gap-2 shadow-sm transition-all text-xs uppercase tracking-wider"
          >
            <Wrench className="w-5 h-5 text-brandBlue" />
            Report Issue
          </button>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <button
            onClick={triggerSos}
            className="h-14 bg-brandRed hover:bg-brandRed/90 text-white font-extrabold rounded-2xl flex items-center justify-center gap-2 shadow-md transition-all text-xs uppercase tracking-wider"
          >
            <ShieldAlert className="w-5 h-5" />
            SOS Panic
          </button>

          <button
            onClick={handleEndTrip}
            className="h-14 bg-brandGreen hover:bg-brandGreen/90 text-white font-extrabold rounded-2xl shadow-md flex items-center justify-center gap-2 transition-all text-xs uppercase tracking-wider"
          >
            <Square className="w-5 h-5 fill-white" />
            End Trip Duty
          </button>
        </div>
      </div>

      {/* Pause dialog */}
      {pauseOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder rounded-3xl w-full max-w-sm p-6 space-y-6 shadow-2xl text-brandTextPrimary">
            <h3 className="text-base font-bold text-brandNavy">Pause Active Trip</h3>
            <div className="space-y-4">
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider">Reason</label>
                <select
                  value={pauseReason}
                  onChange={(e) => setPauseReason(e.target.value)}
                  className="w-full bg-brandBg border border-brandBorder rounded-xl px-4 py-2.5 text-xs text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold"
                >
                  <option value="Traffic">Traffic Congestion</option>
                  <option value="Break">Scheduled Rest Break</option>
                  <option value="Passenger Issue">Passenger Boarding Delay</option>
                  <option value="Vehicle Issue">Minor Technical Check</option>
                  <option value="Other">Other Delay</option>
                </select>
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  onClick={() => setPauseOpen(false)}
                  className="flex-1 py-2.5 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handlePause}
                  disabled={pauseLoading}
                  className="flex-1 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white font-bold rounded-xl text-xs transition-colors shadow-sm animate-hover"
                >
                  {pauseLoading ? 'Saving...' : 'Confirm Pause'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Breakdown dialog */}
      {breakdownOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder rounded-3xl w-full max-w-sm p-6 space-y-6 shadow-2xl text-brandTextPrimary">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-brandNavy">Report Vehicle Issue</h3>
              <button onClick={() => setBreakdownOpen(false)} className="text-brandTextSecondary hover:text-brandTextPrimary font-bold text-lg">✕</button>
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
                className="w-full py-3 bg-brandBlue hover:bg-brandBlue/90 disabled:bg-brandBlue/55 text-white font-bold rounded-xl transition-all text-xs shadow-md"
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

export const TripScreen: React.FC = () => {
  return (
    <ErrorBoundary
      componentName="Driver Active Trip"
      fallbackTitle="Driver Active Screen Error"
      fallbackMessage="An unexpected error occurred while displaying the active trip. Click below to reload or return to dashboard."
    >
      <TripScreenContent />
    </ErrorBoundary>
  );
};
