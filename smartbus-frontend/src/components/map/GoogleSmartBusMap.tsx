import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { MapService } from '../../services/MapService';
import { Compass, Navigation, AlertTriangle, RefreshCw, Radio, MapPin } from 'lucide-react';
import { MapContainer, TileLayer, Marker, Polyline, Popup, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';

declare const google: any;

export interface MapStop {
  id: string;
  stopName: string;
  latitude: number;
  longitude: number;
  sequenceNumber?: number;
  status?: 'PASSED' | 'NEXT' | 'UPCOMING' | 'DEFAULT';
  isHomeStop?: boolean | null;
  arrivalTime?: string | null;
  departureTime?: string | null;
}

export interface MapBusLocation {
  latitude: number | null;
  longitude: number | null;
  heading?: number;
  speed?: number;
  gpsStatus?: 'LIVE' | 'GPS_STALE' | 'UNAVAILABLE';
  busNumber?: string;
  routeProgress?: string;
}

export interface ActiveBusMarker {
  busId: string;
  busNumber: string;
  latitude: number | null;
  longitude: number | null;
  heading?: number;
  speed?: number;
  gpsStatus?: string;
  routeName?: string;
  nextStopName?: string;
  etaMinutes?: number;
}

export interface GoogleSmartBusMapProps {
  routePolyline?: [number, number][];
  startPoint?: { name: string; latitude: number; longitude: number } | null;
  endPoint?: { name: string; latitude: number; longitude: number } | null;
  stops?: MapStop[];
  busLocation?: MapBusLocation | null;
  activeBuses?: ActiveBusMarker[];
  onBusClick?: (busId: string) => void;
  onStopClick?: (stop: MapStop) => void;
  homeStopId?: string | null;
  followDriver?: boolean;
  showRecenterButton?: boolean;
  className?: string;
  height?: string;
  isOffRoute?: boolean;
  recoveryPolyline?: [number, number][];
}

export const isValidCoord = (lat: any, lng: any): boolean => {
  return typeof lat === 'number' && typeof lng === 'number' &&
         isFinite(lat) && isFinite(lng) &&
         !isNaN(lat) && !isNaN(lng) &&
         Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
};

// Leaflet fallback markers
const createLeafletBusIcon = (heading: number, gpsStatus?: string) => {
  const isStale = gpsStatus === 'GPS_STALE';
  const pulseColor = isStale ? 'bg-amber-500' : 'bg-brandBlue';
  const badgeColor = isStale ? 'bg-amber-600' : 'bg-brandBlue';
  return L.divIcon({
    html: `<div class="relative flex items-center justify-center" style="width: 50px; height: 50px;">
      <div class="absolute inset-0 rounded-full ${pulseColor} opacity-30 animate-ping"></div>
      <div style="transform: rotate(${heading || 0}deg)" class="${badgeColor} text-white p-2 rounded-full shadow-2xl border-2 border-white flex items-center justify-center w-11 h-11 transition-transform duration-300">
        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><rect width="16" height="16" x="4" y="4" rx="2"/><path d="M4 9h16"/><path d="M12 9v11"/><path d="M8 20v2"/><path d="M16 20v2"/><path d="M6 14h2"/><path d="M16 14h2"/></svg>
      </div>
      <div class="absolute -top-1 px-1.5 py-0.2 bg-emerald-500 text-[9px] font-black text-white rounded-full uppercase tracking-widest shadow">LIVE</div>
    </div>`,
    className: 'leaflet-smartbus-marker',
    iconSize: [50, 50],
    iconAnchor: [25, 25]
  });
};

const createLeafletStopIcon = (status?: string, seq?: number, isHome?: boolean) => {
  if (isHome) {
    return L.divIcon({
      html: `<div class="relative flex flex-col items-center">
        <div class="bg-amber-500 text-white rounded-full shadow-lg border-2 border-white flex items-center justify-center w-8 h-8 text-sm ring-4 ring-amber-400/30">★</div>
        <div class="bg-amber-600 text-white text-[9px] font-black px-1.5 py-0.5 rounded shadow mt-0.5 whitespace-nowrap">Your Stop</div>
      </div>`,
      className: 'leaflet-home-stop-marker',
      iconSize: [40, 50],
      iconAnchor: [20, 25]
    });
  }
  let bg = 'bg-slate-600 text-white';
  let label = `${seq || ''}`;
  if (status === 'PASSED') {
    bg = 'bg-emerald-600 text-white';
    label = '✓';
  } else if (status === 'NEXT') {
    bg = 'bg-amber-500 text-slate-950 font-black ring-4 ring-amber-400/40 animate-pulse';
    label = seq ? `${seq}` : '→';
  }
  return L.divIcon({
    html: `<div class="${bg} rounded-full shadow border-2 border-white flex items-center justify-center w-7 h-7 text-xs font-bold transition-all">${label}</div>`,
    className: 'leaflet-stop-marker',
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  });
};

const createTerminalIcon = (type: 'START' | 'END') => {
  const isStart = type === 'START';
  const bg = isStart ? 'bg-emerald-600' : 'bg-red-600';
  const letter = isStart ? 'A' : 'B';
  return L.divIcon({
    html: `<div class="${bg} text-white rounded-full shadow-md border-2 border-white flex items-center justify-center w-7 h-7 text-xs font-black">${letter}</div>`,
    className: 'leaflet-terminal-marker',
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  });
};

function LeafletMapController({ center, follow, onPan }: { center: [number, number] | null; follow: boolean; onPan?: () => void }) {
  const map = useMap();
  useEffect(() => {
    if (center && follow) {
      map.panTo(center, { animate: true, duration: 1 });
    }
  }, [center, follow, map]);

  useMapEvents({
    dragstart() {
      if (onPan) onPan();
    }
  });
  return null;
}

export const GoogleSmartBusMap: React.FC<GoogleSmartBusMapProps> = ({
  routePolyline = [],
  startPoint,
  endPoint,
  stops = [],
  busLocation,
  activeBuses = [],
  onBusClick,
  onStopClick,
  homeStopId,
  followDriver = false,
  showRecenterButton = true,
  className = '',
  height = '100%',
  isOffRoute = false,
  recoveryPolyline = []
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapInstanceRef = useRef<any>(null);
  const isGoogleAvailable = MapService.isGoogleMapsEnabled();

  const [useFallback, setUseFallback] = useState(!isGoogleAvailable);
  const [following, setFollowing] = useState(followDriver);
  const [currentCoords, setCurrentCoords] = useState<[number, number] | null>(null);

  // Animated bus coordinates interpolation state
  const prevBusCoordsRef = useRef<{ lat: number; lng: number } | null>(null);
  const animFrameRef = useRef<number | null>(null);

  // Sync follow state when prop updates
  useEffect(() => {
    setFollowing(followDriver);
  }, [followDriver]);

  // Update current bus coordinates with smooth interpolation
  useEffect(() => {
    if (busLocation && busLocation.latitude != null && busLocation.longitude != null) {
      const targetLat = busLocation.latitude;
      const targetLng = busLocation.longitude;

      if (!prevBusCoordsRef.current) {
        prevBusCoordsRef.current = { lat: targetLat, lng: targetLng };
        setCurrentCoords([targetLat, targetLng]);
      } else {
        // Smoothly interpolate towards target
        const startLat = prevBusCoordsRef.current.lat;
        const startLng = prevBusCoordsRef.current.lng;
        const durationMs = 1000;
        const startTime = performance.now();

        const animate = (now: number) => {
          const elapsed = now - startTime;
          const progress = Math.min(1, elapsed / durationMs);
          const curLat = startLat + (targetLat - startLat) * progress;
          const curLng = startLng + (targetLng - startLng) * progress;
          setCurrentCoords([curLat, curLng]);

          if (progress < 1) {
            animFrameRef.current = requestAnimationFrame(animate);
          } else {
            prevBusCoordsRef.current = { lat: targetLat, lng: targetLng };
          }
        };

        if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = requestAnimationFrame(animate);
      }
    } else {
      setCurrentCoords(null);
      prevBusCoordsRef.current = null;
    }

    return () => {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current);
    };
  }, [busLocation?.latitude, busLocation?.longitude]);

  // Determine initial center safely
  const defaultCenter: [number, number] = useMemo(() => {
    if (currentCoords && isValidCoord(currentCoords[0], currentCoords[1])) return currentCoords;
    if (startPoint && isValidCoord(startPoint.latitude, startPoint.longitude)) return [startPoint.latitude, startPoint.longitude];
    const validStop = stops.find(s => isValidCoord(s.latitude, s.longitude));
    if (validStop) return [validStop.latitude, validStop.longitude];
    return [12.971598, 80.2210];
  }, [currentCoords, startPoint, stops]);

  const handleRecenter = () => {
    setFollowing(true);
    if (mapInstanceRef.current && currentCoords && isValidCoord(currentCoords[0], currentCoords[1])) {
      mapInstanceRef.current.panTo({ lat: currentCoords[0], lng: currentCoords[1] });
    }
  };

  const handleUserPan = () => {
    if (following) {
      setFollowing(false);
    }
  };

  // Google Maps vector dark styling
  const googleMapDarkStyles = [
    { elementType: 'geometry', stylers: [{ color: '#0B1F3A' }] },
    { elementType: 'labels.text.stroke', stylers: [{ color: '#0B1F3A' }] },
    { elementType: 'labels.text.fill', stylers: [{ color: '#94A3B8' }] },
    { featureType: 'administrative.locality', elementType: 'labels.text.fill', stylers: [{ color: '#E2E8F0' }] },
    { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#1E293B' }] },
    { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#334155' }] },
    { featureType: 'road.highway', elementType: 'geometry', stylers: [{ color: '#2563EB' }] },
    { featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{ color: '#1D4ED8' }] },
    { featureType: 'transit', elementType: 'geometry', stylers: [{ color: '#1E293B' }] },
    { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#030712' }] }
  ];

  // Try initializing Google Maps
  useEffect(() => {
    let isMounted = true;
    if (!isGoogleAvailable || useFallback) return;

    MapService.loadGoogleMapsScript()
      .then(() => {
        if (!isMounted || !containerRef.current || typeof google === 'undefined') return;

        const map = new google.maps.Map(containerRef.current, {
          center: { lat: defaultCenter[0], lng: defaultCenter[1] },
          zoom: 14,
          styles: googleMapDarkStyles,
          disableDefaultUI: true,
          zoomControl: true,
          fullscreenControl: false
        });

        map.addListener('dragstart', () => {
          handleUserPan();
        });

        mapInstanceRef.current = map;
      })
      .catch(() => {
        if (isMounted) setUseFallback(true);
      });

    return () => {
      isMounted = false;
    };
  }, [isGoogleAvailable, useFallback]);

  // Filter valid polyline positions
  const validRoutePolyline = useMemo(() => {
    return routePolyline.filter(p => Array.isArray(p) && isValidCoord(p[0], p[1]));
  }, [routePolyline]);

  const validRecoveryPolyline = useMemo(() => {
    return recoveryPolyline.filter(p => Array.isArray(p) && isValidCoord(p[0], p[1]));
  }, [recoveryPolyline]);

  return (
    <div className={`relative w-full overflow-hidden ${className}`} style={{ height }}>
      {useFallback ? (
        <MapContainer
          center={defaultCenter}
          zoom={14}
          style={{ height: '100%', width: '100%', backgroundColor: '#0B1F3A' }}
          zoomControl={true}
        >
          <TileLayer
            attribution="&copy; CartoDB Dark"
            url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
          />

          <LeafletMapController
            center={currentCoords && isValidCoord(currentCoords[0], currentCoords[1]) ? currentCoords : null}
            follow={following}
            onPan={handleUserPan}
          />

          {/* Planned Route Polyline */}
          {validRoutePolyline.length > 0 && (
            <Polyline
              positions={validRoutePolyline}
              pathOptions={{
                color: isOffRoute ? '#94A3B8' : '#2563EB',
                weight: 6,
                opacity: isOffRoute ? 0.4 : 0.85,
                lineCap: 'round',
                lineJoin: 'round'
              }}
            />
          )}

          {/* Off-Route Recovery Polyline */}
          {isOffRoute && validRecoveryPolyline.length > 0 && (
            <Polyline
              positions={validRecoveryPolyline}
              pathOptions={{
                color: '#F59E0B',
                weight: 6,
                opacity: 0.95,
                dashArray: '8, 8',
                lineCap: 'round'
              }}
            />
          )}

          {/* Start Marker A */}
          {startPoint && isValidCoord(startPoint.latitude, startPoint.longitude) && (
            <Marker
              position={[startPoint.latitude, startPoint.longitude]}
              icon={createTerminalIcon('START')}
            >
              <Popup className="premium-popup">
                <div className="font-bold text-xs">Start: {startPoint.name}</div>
              </Popup>
            </Marker>
          )}

          {/* End Marker B */}
          {endPoint && isValidCoord(endPoint.latitude, endPoint.longitude) && (
            <Marker
              position={[endPoint.latitude, endPoint.longitude]}
              icon={createTerminalIcon('END')}
            >
              <Popup className="premium-popup">
                <div className="font-bold text-xs">End: {endPoint.name}</div>
              </Popup>
            </Marker>
          )}

          {/* Ordered Route Stops */}
          {stops.map((stop) => {
            if (!isValidCoord(stop.latitude, stop.longitude)) return null;
            const isHome = Boolean(stop.isHomeStop || (homeStopId && stop.id === homeStopId));
            return (
              <Marker
                key={stop.id || `${stop.latitude}-${stop.longitude}`}
                position={[stop.latitude, stop.longitude]}
                icon={createLeafletStopIcon(stop.status, stop.sequenceNumber, isHome)}
                eventHandlers={{
                  click: () => onStopClick && onStopClick(stop)
                }}
              >
                <Popup className="premium-popup">
                  <div className="font-bold text-xs">{stop.stopName}</div>
                  <div className="text-[10px] text-slate-400">Sequence #{stop.sequenceNumber}</div>
                  {isHome && <div className="text-amber-400 font-extrabold text-[10px]">★ Your Preferred Stop</div>}
                </Popup>
              </Marker>
            );
          })}

          {/* Current Tracked Bus Marker */}
          {currentCoords && isValidCoord(currentCoords[0], currentCoords[1]) && (
            <Marker
              position={currentCoords}
              icon={createLeafletBusIcon(busLocation?.heading || 0, busLocation?.gpsStatus)}
              zIndexOffset={2500}
            >
              <Popup className="premium-popup">
                <div className="font-extrabold text-sm text-brandBlue">{busLocation?.busNumber || 'Tracked Bus'}</div>
                <div className="text-xs text-slate-300">Status: {busLocation?.gpsStatus || 'LIVE'}</div>
                {busLocation?.speed != null && (
                  <div className="text-xs text-slate-400">Speed: {Math.round(busLocation.speed)} km/h</div>
                )}
              </Popup>
            </Marker>
          )}

          {/* Fleet Multi-Bus Markers */}
          {activeBuses.map((bus) => {
            if (!isValidCoord(bus.latitude, bus.longitude)) return null;
            return (
              <Marker
                key={bus.busId}
                position={[bus.latitude!, bus.longitude!]}
                icon={createLeafletBusIcon(bus.heading || 0, bus.gpsStatus)}
                zIndexOffset={2000}
                eventHandlers={{
                  click: () => onBusClick && onBusClick(bus.busId)
                }}
              >
                <Popup className="premium-popup">
                  <div className="font-bold text-sm text-brandBlue">{bus.busNumber}</div>
                  <div className="text-xs text-slate-300">Route: {bus.routeName || 'Active'}</div>
                  <div className="text-xs text-slate-400">Next: {bus.nextStopName || 'En route'}</div>
                  {bus.etaMinutes != null && (
                    <div className="text-xs text-emerald-400 font-bold">ETA: {bus.etaMinutes}m</div>
                  )}
                </Popup>
              </Marker>
            );
          })}
        </MapContainer>
      ) : (
        <div ref={containerRef} className="w-full h-full bg-[#0B1F3A]" />
      )}

      {/* Recenter Button Floating Control */}
      {showRecenterButton && !following && currentCoords && (
        <button
          onClick={handleRecenter}
          className="absolute bottom-6 right-6 z-[1000] bg-brandBlue hover:bg-brandBlueDark text-white px-4 py-2.5 rounded-full shadow-2xl flex items-center gap-2 text-xs font-bold transition-transform hover:scale-105 backdrop-blur border border-white/20"
        >
          <Navigation className="w-4 h-4" />
          Recenter
        </button>
      )}

      {/* Off-Route Alert Floating Banner */}
      {isOffRoute && (
        <div className="absolute top-4 left-1/2 -translate-x-1/2 z-[1000] bg-amber-500/95 text-slate-950 font-black px-4 py-2 rounded-full shadow-2xl flex items-center gap-2 text-xs animate-bounce border border-amber-300">
          <AlertTriangle className="w-4 h-4 text-slate-950" />
          <span>⚠️ OFF ROUTE — Recalculating route to next required stop...</span>
        </div>
      )}
    </div>
  );
};
