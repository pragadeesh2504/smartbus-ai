import React, { useState, useEffect, useRef, useMemo } from 'react';
import axios from 'axios';
import {
  Bus as BusIcon,
  Maximize2,
  Crosshair,
  X,
  Radio
} from 'lucide-react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { SmartWebSocketClient, ConnectionState } from '../../services/websocketService';
import { GoogleSmartBusMap, ActiveBusMarker, MapStop } from '../../components/map/GoogleSmartBusMap';

export interface ActiveBusData {
  tripId: string;
  busId: string;
  busCode: string;
  busNumber: string;
  routeId?: string;
  routeName: string;
  driverId?: string;
  driverName: string;
  tripStatus: string;
  latitude: number | null;
  longitude: number | null;
  speed: number;
  heading: number;
  accuracy: number;
  trackingSource: string;
  lastUpdatedTimestamp: number;
  lastUpdatedText: string;
  etaMinutes?: number | null;
  etaStatus?: string;
  distanceMeters?: number | null;
  nextStopName?: string | null;
  nextStopSequence?: number | null;
  offRoute?: boolean;
  routeDeviationMeters?: number | null;
  delayMinutes?: number;
  gpsStale?: boolean;
  gpsStatus?: 'LIVE' | 'GPS_STALE' | 'UNAVAILABLE' | string;
  startLatitude?: number | null;
  startLongitude?: number | null;
  endLatitude?: number | null;
  endLongitude?: number | null;
  polyline?: string | null;
  stops?: any[];
}

const createStartIcon = () => L.divIcon({
  html: `<div class="relative flex items-center justify-center" style="width: 32px; height: 32px;">
    <div class="w-8 h-8 rounded-full bg-emerald-600 text-white font-black text-xs flex items-center justify-center border-2 border-white shadow-xl ring-2 ring-emerald-400/40">
      A
    </div>
    <div class="absolute -bottom-4 px-1 rounded bg-emerald-700 text-white font-black text-[7px] shadow uppercase tracking-tight whitespace-nowrap">FROM</div>
  </div>`,
  className: 'route-marker-start',
  iconSize: [32, 32],
  iconAnchor: [16, 16]
});

const createEndIcon = () => L.divIcon({
  html: `<div class="relative flex items-center justify-center" style="width: 32px; height: 32px;">
    <div class="w-8 h-8 rounded-full bg-rose-600 text-white font-black text-xs flex items-center justify-center border-2 border-white shadow-xl ring-2 ring-rose-400/40">
      B
    </div>
    <div class="absolute -bottom-4 px-1 rounded bg-rose-700 text-white font-black text-[7px] shadow uppercase tracking-tight whitespace-nowrap">TO</div>
  </div>`,
  className: 'route-marker-end',
  iconSize: [32, 32],
  iconAnchor: [16, 16]
});

const createNumberedStopIcon = (seq: number, status: 'passed' | 'next' | 'upcoming') => {
  let bg = 'bg-slate-600 text-white';
  let badge = `${seq}`;
  let extraClass = '';

  if (status === 'passed') {
    bg = 'bg-emerald-500 text-white';
    badge = '✓';
  } else if (status === 'next') {
    bg = 'bg-brandBlue text-white ring-4 ring-brandBlue/30';
    extraClass = 'animate-pulse scale-110';
  }

  return L.divIcon({
    html: `<div class="relative flex items-center justify-center ${extraClass}" style="width: 28px; height: 28px;">
      <div class="w-7 h-7 rounded-full ${bg} font-black text-[10px] flex items-center justify-center border-2 border-white shadow-md">
        ${badge}
      </div>
    </div>`,
    className: 'route-marker-stop',
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  });
};

const MapCameraController: React.FC<{
  selectedBus: ActiveBusData | null;
  fitFleetTrigger: number;
  buses: ActiveBusData[];
  followSelected: boolean;
}> = ({ selectedBus, fitFleetTrigger, buses, followSelected }) => {
  const map = useMap();

  useEffect(() => {
    if (fitFleetTrigger === 0) return;
    const locatedBuses = buses.filter(b => b.latitude != null && b.longitude != null);
    if (locatedBuses.length === 0) {
      map.flyTo([12.9715, 80.2210], 12, { duration: 1.2 });
      return;
    }
    const bounds = L.latLngBounds(locatedBuses.map(b => [b.latitude!, b.longitude!]));
    map.fitBounds(bounds, { padding: [60, 60], maxZoom: 16, animate: true, duration: 1.2 });
  }, [fitFleetTrigger, map, buses]);

  useEffect(() => {
    if (!followSelected || !selectedBus || selectedBus.latitude == null || selectedBus.longitude == null) return;
    map.panTo([selectedBus.latitude, selectedBus.longitude], { animate: true, duration: 0.8 });
  }, [selectedBus?.latitude, selectedBus?.longitude, followSelected, map]);

  return null;
};

const createFleetBusIcon = (bus: ActiveBusData, isSelected: boolean) => {
  const isPaused = bus.tripStatus === 'PAUSED';
  const isStale = bus.gpsStale;
  const isOffRoute = bus.offRoute;

  let ringColor = 'border-brandBlue bg-brandBlue';
  let badgeColor = 'bg-brandBlue';
  let badgeText = 'LIVE';

  if (isOffRoute) {
    ringColor = 'border-brandRed bg-brandRed';
    badgeColor = 'bg-brandRed';
    badgeText = 'OFF ROUTE';
  } else if (isStale) {
    ringColor = 'border-amber-500 bg-amber-500';
    badgeColor = 'bg-amber-500';
    badgeText = 'STALE';
  } else if (isPaused) {
    ringColor = 'border-amber-400 bg-amber-400';
    badgeColor = 'bg-amber-400';
    badgeText = 'PAUSED';
  } else {
    ringColor = 'border-emerald-500 bg-emerald-500';
    badgeColor = 'bg-emerald-500';
    badgeText = 'LIVE';
  }

  const headingDeg = (bus.heading != null && !isNaN(bus.heading)) ? Math.round(bus.heading) : 0;
  const showHeadingArrow = bus.heading != null && !isNaN(bus.heading) && bus.heading >= 0 && bus.speed > 1;

  const arrowSvg = '<div style="transform: rotate(' + headingDeg + 'deg);" class="transition-transform duration-300"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="text-brandNavy"><polygon points="12 2 19 21 12 17 5 21 12 2"></polygon></svg></div>';
  const busSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="text-brandNavy"><path d="M8 6v6"></path><path d="M15 6v6"></path><path d="M2 12h19.6"></path><path d="M18 18h3s.5-1.7.8-2.8c.1-.4.2-.8.2-1.2 0-.4-.1-.8-.2-1.2l-1.4-5C20.1 6.8 19.1 6 18 6H4C2.9 6 1.9 6.8 1.6 7.8L.2 12.8c-.1.4-.2.8-.2 1.2 0 .4.1.8.2 1.2C.5 16.3 1 18 1 18h3"></path><circle cx="7" cy="18" r="2"></circle><path d="M9 18h5"></path><circle cx="16" cy="18" r="2"></circle></svg>';

  const html = '<div class="relative flex items-center justify-center transition-transform duration-300" style="width: 44px; height: 44px;">' +
    (!isStale && !isPaused ? '<div class="absolute inset-0 rounded-full ' + badgeColor + ' opacity-25 animate-ping"></div>' : '') +
    '<div class="relative w-10 h-10 rounded-full bg-white border-2 ' + ringColor + ' shadow-xl flex items-center justify-center ' + (isSelected ? 'ring-4 ring-brandBlue/40 scale-110' : '') + ' transition-all">' +
      '<div class="flex flex-col items-center justify-center">' +
        (showHeadingArrow ? arrowSvg : busSvg) +
        '<span class="text-[8px] font-black text-brandNavy leading-none tracking-tight mt-0.5">' + bus.busNumber.slice(-3) + '</span>' +
      '</div>' +
    '</div>' +
    '<div class="absolute -top-1 -right-1 px-1 py-0.2 rounded-full ' + badgeColor + ' text-white font-black text-[7px] tracking-tight uppercase shadow">' +
      badgeText +
    '</div>' +
  '</div>';

  return L.divIcon({
    html,
    className: 'fleet-marker-container',
    iconSize: [44, 44],
    iconAnchor: [22, 22]
  });
};

export const AdminLiveFleetMap: React.FC = () => {
  const [buses, setBuses] = useState<Record<string, ActiveBusData>>({});
  const [selectedBusId, setSelectedBusId] = useState<string | null>(null);
  const [followSelected, setFollowSelected] = useState<boolean>(true);
  const [fitFleetTrigger, setFitFleetTrigger] = useState<number>(1);
  const clientRef = useRef<SmartWebSocketClient | null>(null);

  const fetchActiveFleet = async () => {
    try {
      const res = await axios.get('/api/admin/buses/active-fleet');
      if (res.data.success && Array.isArray(res.data.data)) {
        const busMap: Record<string, ActiveBusData> = {};
        const now = Date.now();

        res.data.data.forEach((dto: any) => {
          const hasLocation = dto.latitude != null && dto.longitude != null;
          const updatedEpoch = dto.lastUpdated ? new Date(dto.lastUpdated).getTime() : now;
          const isGpsStale = dto.gpsStale || (hasLocation && now - updatedEpoch > 60000);
          const gpsStatus = dto.gpsStatus || (!hasLocation ? 'UNAVAILABLE' : isGpsStale ? 'GPS_STALE' : 'LIVE');

          busMap[dto.busId] = {
            tripId: dto.tripId,
            busId: dto.busId,
            busCode: dto.busCode,
            busNumber: dto.busNumber,
            routeId: dto.routeId,
            routeName: dto.routeName,
            driverId: dto.driverId,
            driverName: dto.driverName,
            tripStatus: dto.tripStatus || 'IN_PROGRESS',
            latitude: hasLocation ? dto.latitude : null,
            longitude: hasLocation ? dto.longitude : null,
            speed: dto.speed || 0,
            heading: dto.heading || 0,
            accuracy: dto.accuracy || 10,
            trackingSource: dto.trackingSource || 'AUTHORITATIVE_GPS',
            lastUpdatedTimestamp: updatedEpoch,
            lastUpdatedText: hasLocation ? new Date(updatedEpoch).toLocaleTimeString() : 'Awaiting GPS',
            etaMinutes: dto.etaMinutes,
            etaStatus: dto.etaStatus,
            distanceMeters: dto.distanceMeters,
            nextStopName: dto.nextStopName,
            nextStopSequence: dto.nextStopSequence,
            offRoute: dto.offRoute,
            routeDeviationMeters: dto.routeDeviationMeters,
            delayMinutes: dto.delayMinutes,
            gpsStale: isGpsStale,
            gpsStatus: gpsStatus,
            startLatitude: dto.startLatitude,
            startLongitude: dto.startLongitude,
            endLatitude: dto.endLatitude,
            endLongitude: dto.endLongitude,
            polyline: dto.polyline,
            stops: dto.stops
          };
        });

        setBuses(busMap);
      }
    } catch (e) {
      console.warn('Failed to load initial active fleet:', e);
    }
  };

  useEffect(() => {
    fetchActiveFleet();

    const client = new SmartWebSocketClient({
      subscriptions: [{ type: 'SUBSCRIBE_CLIENT' }],
      onMessage: (data) => {
        if (!data || !data.type) return;

        if (data.type === 'BUS_LOCATION_UPDATE' || data.type === 'TELEMETRY') {
          const busKey = data.busId || data.busCode || data.busNumber;
          if (!busKey || data.latitude == null || data.longitude == null) return;

          const packetEpoch = data.timestamp ? new Date(data.timestamp).getTime() : Date.now();

          setBuses(prev => {
            const existing = prev[busKey] || Object.values(prev).find(b => b.busCode === data.busCode || b.busNumber === data.busNumber);

            if (existing && existing.lastUpdatedTimestamp && packetEpoch < existing.lastUpdatedTimestamp) {
              return prev;
            }

            const targetKey = existing ? existing.busId : (data.busId || busKey);

            return {
              ...prev,
              [targetKey]: {
                ...(existing || {
                  tripId: data.tripId || '',
                  busId: data.busId || targetKey,
                  busCode: data.busCode || '',
                  busNumber: data.busNumber || '',
                  routeName: data.routeName || 'Assigned Route',
                  driverName: data.driverName || 'Driver',
                  tripStatus: 'IN_PROGRESS'
                }),
                busId: data.busId || (existing?.busId ?? targetKey),
                busCode: data.busCode || (existing?.busCode ?? ''),
                busNumber: data.busNumber || (existing?.busNumber ?? ''),
                latitude: data.latitude,
                longitude: data.longitude,
                speed: data.speed != null ? data.speed : (existing?.speed ?? 0),
                heading: data.heading != null ? data.heading : (existing?.heading ?? 0),
                accuracy: data.accuracy != null ? data.accuracy : (existing?.accuracy ?? 10),
                trackingSource: data.trackingSource || 'AUTHORITATIVE_GPS',
                lastUpdatedTimestamp: packetEpoch,
                lastUpdatedText: new Date(packetEpoch).toLocaleTimeString(),
                gpsStale: false,
                startLatitude: existing?.startLatitude,
                startLongitude: existing?.startLongitude,
                endLatitude: existing?.endLatitude,
                endLongitude: existing?.endLongitude,
                polyline: existing?.polyline,
                stops: existing?.stops
              }
            };
          });
        }
        else if (data.type === 'TRIP_STATUS_UPDATE') {
          const status = data.status;
          const busKey = data.busId || data.busCode || data.busNumber;

          if (status === 'COMPLETED' || status === 'CANCELLED') {
            setBuses(prev => {
              const copy = { ...prev };
              const foundKey = Object.keys(copy).find(
                k => copy[k].busId === data.busId || copy[k].busNumber === data.busNumber || copy[k].tripId === data.tripId
              );
              if (foundKey) delete copy[foundKey];
              return copy;
            });
            setSelectedBusId(curr => (curr === data.busId ? null : curr));
          } else if (status === 'PAUSED' || status === 'IN_PROGRESS') {
            setBuses(prev => {
              const existingKey = Object.keys(prev).find(
                k => prev[k].busId === data.busId || prev[k].busNumber === data.busNumber || prev[k].tripId === data.tripId
              ) || busKey;

              const existing = prev[existingKey];
              if (!existing && data.latitude != null && data.longitude != null) {
                return {
                  ...prev,
                  [data.busId || busKey]: {
                    tripId: data.tripId || '',
                    busId: data.busId || busKey,
                    busCode: data.busCode || '',
                    busNumber: data.busNumber || '',
                    routeName: data.routeName || 'Assigned Route',
                    driverName: data.driverName || 'Driver',
                    tripStatus: status,
                    latitude: data.latitude,
                    longitude: data.longitude,
                    speed: 0,
                    heading: 0,
                    accuracy: 10,
                    trackingSource: 'AUTHORITATIVE_GPS',
                    lastUpdatedTimestamp: Date.now(),
                    lastUpdatedText: new Date().toLocaleTimeString(),
                    gpsStale: false
                  }
                };
              }
              if (existing) {
                return {
                  ...prev,
                  [existingKey]: {
                    ...existing,
                    tripStatus: status
                  }
                };
              }
              return prev;
            });
          }
        }
        else if (data.type === 'ETA_UPDATED') {
          setBuses(prev => {
            const foundKey = Object.keys(prev).find(
              k => prev[k].busId === data.busId || prev[k].busNumber === data.busNumber || prev[k].tripId === data.tripId
            );
            if (!foundKey) return prev;

            return {
              ...prev,
              [foundKey]: {
                ...prev[foundKey],
                etaMinutes: data.minutesRemaining,
                etaStatus: data.etaStatus,
                distanceMeters: data.distanceMeters,
                nextStopName: data.nextStopName || prev[foundKey].nextStopName,
                nextStopSequence: data.nextStopSequence || prev[foundKey].nextStopSequence,
                offRoute: data.offRoute !== undefined ? data.offRoute : prev[foundKey].offRoute,
                routeDeviationMeters: data.routeDeviationMeters != null ? data.routeDeviationMeters : prev[foundKey].routeDeviationMeters,
                delayMinutes: data.delayMinutes != null ? data.delayMinutes : prev[foundKey].delayMinutes,
                gpsStale: data.gpsStale !== undefined ? data.gpsStale : prev[foundKey].gpsStale
              }
            };
          });
        }
        else if (data.type === 'BUS_OFF_ROUTE') {
          setBuses(prev => {
            const foundKey = Object.keys(prev).find(k => prev[k].busId === data.busId || prev[k].busNumber === data.busNumber);
            if (!foundKey) return prev;
            return { ...prev, [foundKey]: { ...prev[foundKey], offRoute: true, routeDeviationMeters: data.deviationMeters || 0 } };
          });
        }
        else if (data.type === 'BUS_BACK_ON_ROUTE') {
          setBuses(prev => {
            const foundKey = Object.keys(prev).find(k => prev[k].busId === data.busId || prev[k].busNumber === data.busNumber);
            if (!foundKey) return prev;
            return { ...prev, [foundKey]: { ...prev[foundKey], offRoute: false } };
          });
        }
        else if (data.type === 'GPS_STALE') {
          setBuses(prev => {
            const foundKey = Object.keys(prev).find(k => prev[k].busId === data.busId || prev[k].busNumber === data.busNumber);
            if (!foundKey) return prev;
            return { ...prev, [foundKey]: { ...prev[foundKey], gpsStale: true } };
          });
        }
      }
    });

    clientRef.current = client;
    client.connect();

    const staleInterval = setInterval(() => {
      const now = Date.now();
      setBuses(prev => {
        let changed = false;
        const updated = { ...prev };
        Object.keys(updated).forEach(key => {
          const b = updated[key];
          if (!b.gpsStale && b.lastUpdatedTimestamp && (now - b.lastUpdatedTimestamp > 60000)) {
            updated[key] = { ...b, gpsStale: true };
            changed = true;
          }
        });
        return changed ? updated : prev;
      });
    }, 5000);

    return () => {
      clearInterval(staleInterval);
      client.disconnect();
      clientRef.current = null;
    };
  }, []);

  const busList = useMemo(() => Object.values(buses), [buses]);

  const selectedBus = useMemo(() => {
    if (!selectedBusId) return null;
    return busList.find(b => b.busId === selectedBusId || b.busCode === selectedBusId) || null;
  }, [busList, selectedBusId]);

  const liveCount = busList.filter(b => b.tripStatus !== 'PAUSED' && !b.gpsStale && !b.offRoute && b.latitude != null && b.gpsStatus !== 'UNAVAILABLE').length;
  const pausedCount = busList.filter(b => b.tripStatus === 'PAUSED').length;
  const staleCount = busList.filter(b => b.gpsStale && b.gpsStatus !== 'UNAVAILABLE').length;
  const offRouteCount = busList.filter(b => b.offRoute).length;
  const unavailableCount = busList.filter(b => b.latitude == null || b.gpsStatus === 'UNAVAILABLE').length;

  // Auto-select single active bus for focused testing
  useEffect(() => {
    if (!selectedBusId && busList.length === 1) {
      setSelectedBusId(busList[0].busId);
    }
  }, [busList, selectedBusId]);

  // Active route geometry for selected or lone active bus
  const activeRouteGeometry = useMemo(() => {
    const targetBus = selectedBus || (busList.length === 1 ? busList[0] : null);
    if (!targetBus) return null;

    let points: [number, number][] = [];
    if (targetBus.polyline) {
      try {
        const parsed = JSON.parse(targetBus.polyline);
        if (Array.isArray(parsed) && parsed.length > 1) {
          points = parsed;
        }
      } catch (e) {}
    }

    const stops = targetBus.stops || [];
    const startCoord: [number, number] | null = (targetBus.startLatitude != null && targetBus.startLongitude != null)
      ? [targetBus.startLatitude, targetBus.startLongitude]
      : (stops.length > 0 && stops[0].stop?.latitude != null ? [stops[0].stop.latitude, stops[0].stop.longitude] : null);

    const endCoord: [number, number] | null = (targetBus.endLatitude != null && targetBus.endLongitude != null)
      ? [targetBus.endLatitude, targetBus.endLongitude]
      : (stops.length > 0 && stops[stops.length - 1].stop?.latitude != null
          ? [stops[stops.length - 1].stop.latitude, stops[stops.length - 1].stop.longitude]
          : null);

    if (points.length === 0) {
      if (startCoord) points.push(startCoord);
      stops.forEach((s: any) => {
        if (s.stop?.latitude != null && s.stop?.longitude != null) {
          points.push([s.stop.latitude, s.stop.longitude]);
        }
      });
      if (endCoord) points.push(endCoord);
    }

    return {
      points,
      startCoord,
      endCoord,
      stops,
      nextStopSequence: targetBus.nextStopSequence || 1,
      offRoute: targetBus.offRoute,
      routeName: targetBus.routeName
    };
  }, [selectedBus, busList]);

  const activeBusMarkers: ActiveBusMarker[] = useMemo(() => {
    return busList
      .filter(b => b.latitude != null && b.longitude != null && b.gpsStatus !== 'UNAVAILABLE')
      .map(b => ({
        busId: b.busId,
        busNumber: b.busNumber,
        latitude: b.latitude,
        longitude: b.longitude,
        heading: b.heading,
        speed: b.speed,
        gpsStatus: b.gpsStale ? 'GPS_STALE' : 'LIVE',
        routeName: b.routeName,
        nextStopName: b.nextStopName || undefined,
        etaMinutes: b.etaMinutes || undefined
      }));
  }, [busList]);

  const selectedBusStops: MapStop[] = useMemo(() => {
    if (!activeRouteGeometry?.stops) return [];
    return activeRouteGeometry.stops.map((s: any, idx: number) => {
      const seq = s.sequenceNumber || (idx + 1);
      const lat = s.stop?.latitude ?? s.latitude;
      const lng = s.stop?.longitude ?? s.longitude;
      const name = s.stop?.stopName || s.stopName || `Stop ${seq}`;
      const isPassed = seq < activeRouteGeometry.nextStopSequence;
      const isNext = seq === activeRouteGeometry.nextStopSequence;
      return {
        id: s.stop?.id || `stop-${seq}`,
        stopName: name,
        latitude: lat,
        longitude: lng,
        sequenceNumber: seq,
        status: isPassed ? 'PASSED' : isNext ? 'NEXT' : 'UPCOMING'
      };
    });
  }, [activeRouteGeometry]);

  const startPointObj = useMemo(() => {
    if (activeRouteGeometry?.startCoord) {
      return {
        name: activeRouteGeometry.routeName ? `${activeRouteGeometry.routeName} (Start)` : 'Route Start',
        latitude: activeRouteGeometry.startCoord[0],
        longitude: activeRouteGeometry.startCoord[1]
      };
    }
    return null;
  }, [activeRouteGeometry]);

  const endPointObj = useMemo(() => {
    if (activeRouteGeometry?.endCoord) {
      return {
        name: activeRouteGeometry.routeName ? `${activeRouteGeometry.routeName} (Destination)` : 'Route Destination',
        latitude: activeRouteGeometry.endCoord[0],
        longitude: activeRouteGeometry.endCoord[1]
      };
    }
    return null;
  }, [activeRouteGeometry]);

  return (
    <div className="bg-white border border-brandBorder rounded-3xl overflow-hidden shadow-sm flex flex-col h-[520px] relative">
      <div className="p-4 bg-brandBg/80 backdrop-blur border-b border-brandBorder flex flex-wrap items-center justify-between gap-3 z-10">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-brandBlue/10 rounded-2xl text-brandBlue">
            <BusIcon className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-bold text-brandNavy text-sm">Command Center Fleet Monitor</h3>
              <span className="flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-black bg-brandGreen/15 text-brandGreen border border-brandGreen/25">
                <span className="w-1.5 h-1.5 rounded-full bg-brandGreen animate-pulse"></span>
                {busList.length} Active Bus{busList.length !== 1 ? 'es' : ''}
              </span>
            </div>
            <p className="text-[11px] text-brandTextSecondary font-medium">
              Authoritative driver phone GPS & onboard telemetry • Zero-latency movement
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div className="hidden sm:flex items-center gap-1.5 bg-white border border-brandBorder rounded-xl p-1 text-[10px] font-bold">
            <span className="px-2 py-0.5 rounded-lg bg-emerald-50 text-emerald-700">🟢 {liveCount} Live</span>
            {unavailableCount > 0 && <span className="px-2 py-0.5 rounded-lg bg-slate-100 text-slate-700">📍 {unavailableCount} Unavail</span>}
            {pausedCount > 0 && <span className="px-2 py-0.5 rounded-lg bg-amber-50 text-amber-700">🟡 {pausedCount} Paused</span>}
            {staleCount > 0 && <span className="px-2 py-0.5 rounded-lg bg-orange-50 text-orange-700">⚠️ {staleCount} Stale</span>}
            {offRouteCount > 0 && <span className="px-2 py-0.5 rounded-lg bg-rose-50 text-rose-700">🚨 {offRouteCount} Dev</span>}
          </div>

          <button
            onClick={() => setFitFleetTrigger(prev => prev + 1)}
            title="Fit map view to all active fleet vehicles"
            className="flex items-center gap-1.5 px-3 py-2 bg-white border border-brandBorder hover:bg-brandBg text-brandNavy rounded-xl text-xs font-bold transition-all shadow-sm"
          >
            <Maximize2 className="w-3.5 h-3.5 text-brandBlue" />
            <span className="hidden md:inline">Fit Fleet</span>
          </button>

          <button
            onClick={() => setFollowSelected(prev => !prev)}
            title="Keep camera centered on selected bus"
            className={'flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold transition-all border shadow-sm ' +
              (followSelected
                ? 'bg-brandBlue text-white border-brandBlue'
                : 'bg-white text-brandTextSecondary border-brandBorder hover:bg-brandBg')
            }
          >
            <Crosshair className="w-3.5 h-3.5" />
            <span className="hidden md:inline">Follow Bus</span>
          </button>
        </div>
      </div>

      <div className="flex-1 relative z-0">
        <GoogleSmartBusMap
          activeBuses={activeBusMarkers}
          onBusClick={(id) => setSelectedBusId(id)}
          routePolyline={activeRouteGeometry?.points || []}
          startPoint={startPointObj}
          endPoint={endPointObj}
          stops={selectedBusStops}
          busLocation={
            selectedBus && selectedBus.latitude != null && selectedBus.longitude != null && selectedBus.gpsStatus !== 'UNAVAILABLE'
              ? {
                  latitude: selectedBus.latitude,
                  longitude: selectedBus.longitude,
                  heading: selectedBus.heading,
                  speed: selectedBus.speed,
                  gpsStatus: selectedBus.gpsStale ? 'GPS_STALE' : 'LIVE',
                  busNumber: selectedBus.busNumber
                }
              : null
          }
          followDriver={followSelected}
          showRecenterButton={true}
          isOffRoute={activeRouteGeometry?.offRoute}
          height="100%"
        />

        <div className="absolute top-4 right-4 bg-white/95 border border-brandBorder rounded-2xl p-3.5 z-[1000] w-64 shadow-xl backdrop-blur max-h-[220px] overflow-y-auto">
          <div className="flex items-center justify-between mb-2">
            <h4 className="text-[10px] font-extrabold text-brandNavy uppercase tracking-wider flex items-center gap-1.5">
              <Radio className="w-3 h-3 text-brandBlue animate-pulse" /> Active Transits ({busList.length})
            </h4>
          </div>

          <div className="space-y-1.5">
            {busList.length > 0 ? (
              busList.map((bus) => {
                const isSelected = bus.busId === selectedBusId;
                const isUnavailable = bus.latitude == null || bus.gpsStatus === 'UNAVAILABLE';
                return (
                  <div
                    key={bus.busId}
                    onClick={() => setSelectedBusId(bus.busId)}
                    className={'p-2 rounded-xl text-xs cursor-pointer border transition-all ' +
                      (isSelected
                        ? 'bg-brandBlue/10 border-brandBlue font-bold'
                        : 'bg-brandBg/60 hover:bg-brandBg border-transparent font-medium')
                    }
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-brandNavy font-extrabold flex items-center gap-1.5">
                        <span className={'w-2 h-2 rounded-full ' + (
                          isUnavailable ? 'bg-slate-400' :
                          bus.offRoute ? 'bg-brandRed animate-ping' :
                          bus.gpsStale ? 'bg-amber-500' :
                          bus.tripStatus === 'PAUSED' ? 'bg-amber-400' :
                          'bg-emerald-500 animate-pulse'
                        )}></span>
                        {bus.busNumber}
                      </span>

                      {isUnavailable ? (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded bg-amber-50 text-amber-800 border border-amber-300">🟡 STARTED / 📍 Waiting for GPS</span>
                      ) : bus.offRoute ? (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded bg-brandRed/15 text-brandRed">DEV</span>
                      ) : bus.gpsStale ? (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">STALE</span>
                      ) : bus.tripStatus === 'PAUSED' ? (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">PAUSED</span>
                      ) : bus.etaMinutes != null ? (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800">{bus.etaMinutes}m</span>
                      ) : (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded bg-blue-100 text-blue-800">LIVE</span>
                      )}
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-brandTextSecondary mt-1">
                      <span className="truncate max-w-[130px]">{bus.nextStopName ? '→ ' + bus.nextStopName : bus.routeName}</span>
                      <span>{isUnavailable ? 'No GPS' : `${bus.speed.toFixed(0)} km/h`}</span>
                    </div>
                  </div>
                );
              })
            ) : (
              <p className="text-xs text-brandTextSecondary italic p-2 text-center">No buses currently active.</p>
            )}
          </div>
        </div>

        {selectedBus && (
          <div className="absolute bottom-4 left-4 bg-white/95 border border-brandBorder rounded-2xl p-4 z-[1000] w-80 shadow-2xl backdrop-blur text-brandTextPrimary animate-in fade-in slide-in-from-bottom-2">
            <div className="flex items-center justify-between border-b border-brandBorder pb-2 mb-3">
              <div className="flex items-center gap-2">
                <div className="p-2 bg-brandBlue/10 rounded-xl text-brandBlue">
                  <BusIcon className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="font-extrabold text-sm text-brandNavy">{selectedBus.busNumber}</h4>
                  <p className="text-[10px] text-brandTextSecondary font-bold">{selectedBus.routeName}</p>
                </div>
              </div>
              <button
                onClick={() => setSelectedBusId(null)}
                className="p-1 hover:bg-brandBg rounded-lg text-brandTextSecondary"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex items-center justify-between mb-3 text-xs">
              <span className="text-[11px] text-brandTextSecondary font-semibold">Vehicle State</span>
              {(selectedBus.latitude == null || selectedBus.gpsStatus === 'UNAVAILABLE') ? (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-slate-100 text-slate-700 border border-slate-300">
                  📍 LOCATION UNAVAILABLE
                </span>
              ) : selectedBus.offRoute ? (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-brandRed/15 text-brandRed border border-brandRed/25">
                  🚨 OFF ROUTE ({Math.round(selectedBus.routeDeviationMeters || 0)}m)
                </span>
              ) : selectedBus.gpsStale ? (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-amber-100 text-amber-800 border border-amber-300">
                  ⚠️ GPS STALE — Last known
                </span>
              ) : selectedBus.tripStatus === 'PAUSED' ? (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-amber-100 text-amber-800 border border-amber-300">
                  🟡 TRIP PAUSED
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-emerald-100 text-emerald-800 border border-emerald-300 flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                  🟢 LIVE ON ROUTE
                </span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs mb-3">
              <div className="p-2 bg-brandBg rounded-xl">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase">Driver</p>
                <p className="font-bold text-brandNavy truncate">{selectedBus.driverName}</p>
              </div>
              <div className="p-2 bg-brandBg rounded-xl">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase">Velocity</p>
                <p className="font-bold text-brandNavy">{selectedBus.speed.toFixed(1)} km/h</p>
              </div>
              <div className="p-2 bg-brandBg rounded-xl">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase">Heading / Acc</p>
                <p className="font-bold text-brandNavy">{Math.round(selectedBus.heading)}° (±{Math.round(selectedBus.accuracy)}m)</p>
              </div>
              <div className="p-2 bg-brandBg rounded-xl">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase">Next Stop</p>
                <p className="font-bold text-brandBlue truncate">{selectedBus.nextStopName || 'En route'}</p>
              </div>
            </div>

            <div className="flex items-center justify-between text-[10px] text-brandTextSecondary pt-2 border-t border-brandBorder">
              <span>Source: <b className="text-brandNavy">{selectedBus.trackingSource}</b></span>
              <span>Updated: <b>{selectedBus.lastUpdatedText}</b></span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
