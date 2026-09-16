import React, { useState, useEffect, useRef, useMemo } from 'react';
import axios from 'axios';
import {
  Play,
  Pause,
  RotateCcw,
  Square,
  FastForward,
  AlertTriangle,
  Compass,
  Gauge,
  Navigation,
  CheckCircle,
  Radio,
  WifiOff,
  Flame
} from 'lucide-react';
import { MapService } from '../../services/MapService';

interface StopPoint {
  stopId: string;
  stopName: string;
  latitude: number;
  longitude: number;
  sequence: number;
}

interface RouteSimulatorPanelProps {
  tripId: string;
  busNumber: string;
  routeName: string;
  routeId: string;
  tripStatus: string;
  onLocationSent?: (lat: number, lng: number, speed: number, heading: number) => void;
  onStatusChange?: (newStatus: string) => void;
}

export const RouteSimulatorPanel: React.FC<RouteSimulatorPanelProps> = ({
  tripId,
  busNumber,
  routeName,
  routeId,
  tripStatus,
  onLocationSent,
  onStatusChange
}) => {
  const [isRunning, setIsRunning] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [speedMultiplier, setSpeedMultiplier] = useState<number>(10);
  const [polyline, setPolyline] = useState<[number, number][]>([]);
  const [stops, setStops] = useState<StopPoint[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [currentSpeed, setCurrentSpeed] = useState<number>(0);
  const [currentHeading, setCurrentHeading] = useState<number>(0);
  const [distanceKm, setDistanceKm] = useState<number>(0);
  const [error, setError] = useState<string>('');
  const [loadingRoute, setLoadingRoute] = useState<boolean>(true);
  const [isStaleTestActive, setIsStaleTestActive] = useState<boolean>(false);
  const [isDeviated, setIsDeviated] = useState<boolean>(false);

  const timerRef = useRef<number | null>(null);
  const lastTimestampRef = useRef<number>(0);
  const currentIndexRef = useRef<number>(0);
  const isRunningRef = useRef<boolean>(false);
  const isPausedRef = useRef<boolean>(false);
  const isStaleTestRef = useRef<boolean>(false);
  const speedMultRef = useRef<number>(speedMultiplier);

  useEffect(() => {
    currentIndexRef.current = currentIndex;
  }, [currentIndex]);

  useEffect(() => {
    isRunningRef.current = isRunning;
  }, [isRunning]);

  useEffect(() => {
    isPausedRef.current = isPaused;
  }, [isPaused]);

  useEffect(() => {
    isStaleTestRef.current = isStaleTestActive;
  }, [isStaleTestActive]);

  useEffect(() => {
    speedMultRef.current = speedMultiplier;
  }, [speedMultiplier]);

  // Load route geometry and stops
  useEffect(() => {
    let isMounted = true;
    const loadRouteGeometry = async () => {
      setLoadingRoute(true);
      setError('');
      try {
        let routeData: any = null;
        try {
          const res = await axios.get(`/api/routes/${routeId}`);
          routeData = res.data;
        } catch (e) {
          const adminRes = await axios.get(`/api/admin/routes/${routeId}`);
          routeData = adminRes.data?.data || adminRes.data;
        }

        if (!routeData) {
          throw new Error('Route geometry unavailable for this route.');
        }

        // Ordered stops
        const rawStops: any[] = routeData.stops || [];
        const loadedStops: StopPoint[] = rawStops.map((rs: any, idx: number) => ({
          stopId: rs.stop?.id || rs.stopId || `stop-${idx}`,
          stopName: rs.stop?.stopName || rs.stopName || `Stop ${idx + 1}`,
          latitude: rs.stop?.latitude ?? rs.latitude,
          longitude: rs.stop?.longitude ?? rs.longitude,
          sequence: rs.sequenceNumber || rs.sequence || idx + 1
        })).filter(s => s.latitude != null && s.longitude != null);

        loadedStops.sort((a, b) => a.sequence - b.sequence);
        if (isMounted) setStops(loadedStops);

        // 1. Check if saved polyline exists in DB
        let points: [number, number][] = [];
        if (routeData.polyline) {
          try {
            const parsed = JSON.parse(routeData.polyline);
            if (Array.isArray(parsed) && parsed.length > 1) {
              points = parsed;
            }
          } catch (ignored) {}
        }

        // 2. If not saved, compute road-following geometry via MapService
        if (points.length === 0 && loadedStops.length > 0) {
          const fromLat = routeData.startLatitude ?? loadedStops[0].latitude;
          const fromLng = routeData.startLongitude ?? loadedStops[0].longitude;
          const toLat = routeData.endLatitude ?? loadedStops[loadedStops.length - 1].latitude;
          const toLng = routeData.endLongitude ?? loadedStops[loadedStops.length - 1].longitude;

          const stopsCoords = loadedStops.map(s => ({ lat: s.latitude, lng: s.longitude }));
          const routeResult = await MapService.calculateRoute(
            { lat: fromLat, lng: fromLng },
            { lat: toLat, lng: toLng },
            stopsCoords
          );

          if (routeResult && routeResult.polyline && routeResult.polyline.length > 1) {
            points = routeResult.polyline;
          }
        }

        if (points.length < 2) {
          if (isMounted) {
            setError('Route geometry unavailable for this route.');
            setLoadingRoute(false);
          }
          return;
        }

        if (isMounted) {
          setPolyline(points);
          setCurrentIndex(0);
          currentIndexRef.current = 0;
          setLoadingRoute(false);
        }
      } catch (err: any) {
        if (isMounted) {
          setError(err.message || 'Route geometry unavailable for this route.');
          setLoadingRoute(false);
        }
      }
    };

    if (routeId) {
      loadRouteGeometry();
    }

    return () => {
      isMounted = false;
    };
  }, [routeId]);

  // Synchronize paused state from prop
  useEffect(() => {
    if (tripStatus === 'PAUSED' && !isPaused) {
      setIsPaused(true);
    } else if (tripStatus === 'IN_PROGRESS' && isPaused) {
      setIsPaused(false);
    }
  }, [tripStatus]);

  // Bearing calculation
  const calculateBearing = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const toDeg = (rad: number) => (rad * 180) / Math.PI;
    const φ1 = toRad(lat1);
    const φ2 = toRad(lat2);
    const Δλ = toRad(lon2 - lon1);

    const y = Math.sin(Δλ) * Math.cos(φ2);
    const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
    const θ = Math.atan2(y, x);
    return Math.round((toDeg(θ) + 360) % 360);
  };

  // Haversine distance
  const haversineDistKm = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
    const R = 6371;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  };

  // Map each stop to nearest polyline index
  const stopIndices = useMemo(() => {
    if (polyline.length === 0 || stops.length === 0) return [];
    return stops.map(stop => {
      let bestIdx = 0;
      let minD = Infinity;
      polyline.forEach((p, idx) => {
        const d = haversineDistKm(stop.latitude, stop.longitude, p[0], p[1]);
        if (d < minD) {
          minD = d;
          bestIdx = idx;
        }
      });
      return { stop, polylineIndex: bestIdx };
    });
  }, [polyline, stops]);

  // Current leg & Next stop computation
  const currentLegInfo = useMemo(() => {
    if (polyline.length === 0) return { leg: 'Initializing...', nextStopName: 'Calculating' };
    const curIdx = currentIndex;

    let passedCount = 0;
    let nextStopName = stops.length > 0 ? stops[0].stopName : 'Destination';

    for (let i = 0; i < stopIndices.length; i++) {
      if (curIdx >= stopIndices[i].polylineIndex) {
        passedCount = i + 1;
      } else {
        nextStopName = stopIndices[i].stop.stopName;
        break;
      }
    }

    if (passedCount === 0) {
      return {
        leg: `FROM → ${stops.length > 0 ? stops[0].stopName : 'Destination'}`,
        nextStopName: stops.length > 0 ? stops[0].stopName : 'Destination'
      };
    } else if (passedCount >= stops.length) {
      return {
        leg: `${stops[stops.length - 1]?.stopName || 'Last Stop'} → Destination`,
        nextStopName: 'Destination'
      };
    } else {
      return {
        leg: `${stops[passedCount - 1].stopName} → ${stops[passedCount].stopName}`,
        nextStopName: stops[passedCount].stopName
      };
    }
  }, [currentIndex, polyline.length, stopIndices, stops]);

  const progressPercentage = useMemo(() => {
    if (polyline.length <= 1) return 0;
    return Math.min(100, Math.round((currentIndex / (polyline.length - 1)) * 100));
  }, [currentIndex, polyline.length]);

  // Telemetry tick executor
  const executeTick = async () => {
    if (!isRunningRef.current || isPausedRef.current) return;
    if (isStaleTestRef.current) {
      // Pause GPS Updates active: do not send location packet
      return;
    }

    const idx = currentIndexRef.current;
    if (idx >= polyline.length) {
      // Reached destination
      setIsRunning(false);
      isRunningRef.current = false;
      return;
    }

    const currentPt = polyline[idx];
    const nextPt = idx < polyline.length - 1 ? polyline[idx + 1] : currentPt;

    // Calculate heading towards next waypoint
    const heading = (currentPt[0] === nextPt[0] && currentPt[1] === nextPt[1])
      ? currentHeading
      : calculateBearing(currentPt[0], currentPt[1], nextPt[0], nextPt[1]);

    // Simulated speed between 25 and 45 km/h
    const baseSpeed = idx === 0 || idx >= polyline.length - 1 ? 0 : 35 + (idx % 7);
    const speed = baseSpeed;

    setCurrentSpeed(speed);
    setCurrentHeading(heading);

    // Apply deviation offset if test hook active
    let sendLat = currentPt[0];
    let sendLng = currentPt[1];
    if (isDeviated) {
      // Shift coordinate by ~0.003 (~300 meters)
      sendLat += 0.003;
      sendLng += 0.003;
    }

    // Monotonically increasing timestamp
    const now = Date.now();
    const packetTimestamp = Math.max(now, lastTimestampRef.current + 100);
    lastTimestampRef.current = packetTimestamp;

    // Post to existing authoritative driver GPS endpoint
    try {
      await axios.post('/api/driver/location', {
        latitude: sendLat,
        longitude: sendLng,
        speed: speed,
        heading: heading,
        accuracy: 8.5,
        altitude: 15.0,
        timestamp: new Date(packetTimestamp).toISOString(),
        trackingSource: 'AUTHORITATIVE_GPS'
      });

      if (onLocationSent) {
        onLocationSent(sendLat, sendLng, speed, heading);
      }
    } catch (err) {
      console.warn('Simulator location broadcast warning:', err);
    }

    // Advance index based on speed multiplier
    const stepAdvance = Math.max(1, Math.floor(speedMultRef.current / 2));
    const nextIndex = Math.min(polyline.length - 1, idx + stepAdvance);

    currentIndexRef.current = nextIndex;
    setCurrentIndex(nextIndex);

    // Stop if finished
    if (nextIndex >= polyline.length - 1) {
      setIsRunning(false);
      isRunningRef.current = false;
    }
  };

  // Run simulation interval loop
  useEffect(() => {
    if (!isRunning || isPaused) {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      return;
    }

    const intervalMs = Math.max(200, Math.floor(1000 / (speedMultiplier >= 10 ? 4 : 2)));
    timerRef.current = window.setInterval(executeTick, intervalMs);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [isRunning, isPaused, speedMultiplier, polyline.length, isDeviated]);

  // Controls
  const handleStart = () => {
    if (polyline.length === 0) return;
    setIsRunning(true);
    setIsPaused(false);
  };

  const handlePause = () => {
    setIsPaused(true);
  };

  const handleResume = () => {
    setIsPaused(false);
  };

  const handleReset = () => {
    setIsRunning(false);
    setIsPaused(false);
    setCurrentIndex(0);
    currentIndexRef.current = 0;
    setCurrentSpeed(0);
  };

  return (
    <div className="bg-white border border-brandBorder rounded-3xl p-5 shadow-sm">
      <div className="flex items-center justify-between border-b border-brandBorder pb-3 mb-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-brandBlue/10 rounded-2xl text-brandBlue">
            <Navigation className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-extrabold text-brandNavy text-sm">Laptop End-to-End Route Simulator</h3>
              <span className="px-2 py-0.5 rounded-full text-[9px] font-black bg-brandBlue/10 text-brandBlue border border-brandBlue/20">
                LOCAL TESTING ONLY
              </span>
            </div>
            <p className="text-[11px] text-brandTextSecondary font-medium">
              Simulates authoritative driver phone GPS along saved road geometry
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <span className={`px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider flex items-center gap-1.5 ${
            isDeviated ? 'bg-brandRed/15 text-brandRed border border-brandRed/20' :
            isStaleTestActive ? 'bg-amber-100 text-amber-800 border border-amber-300' :
            isPaused ? 'bg-amber-100 text-amber-800 border border-amber-300' :
            isRunning ? 'bg-emerald-100 text-emerald-800 border border-emerald-300' :
            'bg-slate-100 text-slate-700 border border-slate-200'
          }`}>
            <span className={`w-1.5 h-1.5 rounded-full ${
              isDeviated ? 'bg-brandRed animate-ping' :
              isStaleTestActive ? 'bg-amber-500' :
              isPaused ? 'bg-amber-500' :
              isRunning ? 'bg-emerald-500 animate-pulse' :
              'bg-slate-400'
            }`} />
            {isDeviated ? 'OFF ROUTE DEV' :
             isStaleTestActive ? 'GPS STALE TEST' :
             isPaused ? 'PAUSED' :
             isRunning ? 'SIMULATING' : 'READY'}
          </span>
        </div>
      </div>

      {loadingRoute ? (
        <div className="py-6 text-center text-xs text-brandTextSecondary font-medium animate-pulse">
          Loading road polyline and stop waypoints...
        </div>
      ) : error ? (
        <div className="p-3 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl text-xs font-semibold flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0 text-amber-600" />
          <span>{error}</span>
        </div>
      ) : (
        <div className="space-y-4">
          {/* Status Metrics Bar */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
            <div className="p-2.5 bg-brandBg rounded-2xl">
              <p className="text-[9px] text-brandTextSecondary font-extrabold uppercase">Vehicle</p>
              <p className="font-extrabold text-xs text-brandNavy truncate">{busNumber} • {routeName}</p>
            </div>
            <div className="p-2.5 bg-brandBg rounded-2xl">
              <p className="text-[9px] text-brandTextSecondary font-extrabold uppercase">Sim Speed / Hdg</p>
              <p className="font-extrabold text-xs text-brandNavy">{currentSpeed.toFixed(0)} km/h • {currentHeading}°</p>
            </div>
            <div className="p-2.5 bg-brandBg rounded-2xl">
              <p className="text-[9px] text-brandTextSecondary font-extrabold uppercase">Next Stop</p>
              <p className="font-extrabold text-xs text-brandBlue truncate">{currentLegInfo.nextStopName}</p>
            </div>
            <div className="p-2.5 bg-brandBg rounded-2xl">
              <p className="text-[9px] text-brandTextSecondary font-extrabold uppercase">Route Progress</p>
              <p className="font-extrabold text-xs text-emerald-600">{progressPercentage}% ({currentIndex}/{polyline.length})</p>
            </div>
          </div>

          {/* Current Leg Info */}
          <div className="px-3 py-2 bg-brandBg/60 border border-brandBorder/60 rounded-xl flex items-center justify-between text-xs">
            <span className="text-[11px] text-brandTextSecondary font-bold">Active Leg:</span>
            <span className="font-black text-brandNavy">{currentLegInfo.leg}</span>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
            <div
              className={`h-full transition-all duration-300 ${
                isDeviated ? 'bg-brandRed' :
                isPaused ? 'bg-amber-400' :
                'bg-brandBlue'
              }`}
              style={{ width: `${progressPercentage}%` }}
            />
          </div>

          {/* Main Controls */}
          <div className="flex flex-wrap items-center justify-between gap-2.5 pt-1">
            <div className="flex items-center gap-2">
              {!isRunning ? (
                <button
                  onClick={handleStart}
                  className="px-4 py-2 bg-brandBlue hover:bg-brandBlueDark text-white rounded-2xl text-xs font-black flex items-center gap-1.5 shadow-sm transition-all"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  Start Simulation
                </button>
              ) : isPaused ? (
                <button
                  onClick={handleResume}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-2xl text-xs font-black flex items-center gap-1.5 shadow-sm transition-all"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  Resume
                </button>
              ) : (
                <button
                  onClick={handlePause}
                  className="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-white rounded-2xl text-xs font-black flex items-center gap-1.5 shadow-sm transition-all"
                >
                  <Pause className="w-3.5 h-3.5 fill-current" />
                  Pause
                </button>
              )}

              <button
                onClick={handleReset}
                className="px-3 py-2 bg-white border border-brandBorder hover:bg-brandBg text-brandNavy rounded-2xl text-xs font-bold flex items-center gap-1.5 shadow-sm transition-all"
                title="Reset simulation to beginning"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Reset
              </button>
            </div>

            {/* Speed Multiplier */}
            <div className="flex items-center gap-1 bg-brandBg border border-brandBorder rounded-2xl p-1 text-[11px] font-bold">
              <span className="text-[9px] text-brandTextSecondary uppercase font-extrabold px-1.5">Speed:</span>
              {[1, 5, 10, 25].map(multiplier => (
                <button
                  key={multiplier}
                  onClick={() => setSpeedMultiplier(multiplier)}
                  className={`px-2 py-0.5 rounded-xl transition-all ${
                    speedMultiplier === multiplier
                      ? 'bg-brandBlue text-white font-black shadow-sm'
                      : 'text-brandTextSecondary hover:text-brandNavy'
                  }`}
                >
                  {multiplier}x
                </button>
              ))}
            </div>
          </div>

          {/* Test Hooks */}
          <div className="pt-2 border-t border-brandBorder">
            <p className="text-[10px] text-brandTextSecondary font-extrabold uppercase tracking-wider mb-2">
              Developer Test Hooks
            </p>
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => setIsStaleTestActive(prev => !prev)}
                className={`p-2.5 rounded-2xl border text-[11px] font-bold flex items-center justify-center gap-1.5 transition-all ${
                  isStaleTestActive
                    ? 'bg-amber-500 text-white border-amber-600 shadow-sm'
                    : 'bg-white border-brandBorder text-brandNavy hover:bg-brandBg'
                }`}
                title="Freezes outgoing GPS updates to test STALE detection"
              >
                <WifiOff className="w-3.5 h-3.5" />
                {isStaleTestActive ? 'Resume GPS Ticks' : 'Pause GPS Updates'}
              </button>

              <button
                onClick={() => setIsDeviated(prev => !prev)}
                className={`p-2.5 rounded-2xl border text-[11px] font-bold flex items-center justify-center gap-1.5 transition-all ${
                  isDeviated
                    ? 'bg-brandRed text-white border-brandRed shadow-sm'
                    : 'bg-white border-brandBorder text-brandNavy hover:bg-brandBg'
                }`}
                title="Offsets coordinates off-route to test deviation alerting"
              >
                <AlertTriangle className="w-3.5 h-3.5" />
                {isDeviated ? 'Return to Route' : 'Simulate Deviation'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
