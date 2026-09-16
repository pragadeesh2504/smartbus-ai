import React, { useEffect, useRef, useState } from 'react';
import { MapService, LocationSearchResult } from '../../services/MapService';
import { Loader2, AlertCircle } from 'lucide-react';

declare const google: any;

interface StopType {
  id?: string;
  stopName: string;
  latitude: number;
  longitude: number;
}

interface RouteStopType {
  stop: StopType;
  sequenceNumber: number;
  distanceFromStart: number;
  durationFromStartMins: number;
  expectedArrivalTime: string | null;
  expectedDepartureTime: string | null;
  legDistanceKm?: number;
  legDurationMins?: number;
}

interface GoogleRouteMapProps {
  fromLoc: LocationSearchResult | null;
  toLoc: LocationSearchResult | null;
  workspaceStops: RouteStopType[];
  routePolyline: [number, number][];
  onAddStop?: (lat: number, lng: number) => void;
  onMarkerDragEnd?: (index: number, lat: number, lng: number) => void;
  center?: [number, number];
  zoom?: number;
}

export const GoogleRouteMap: React.FC<GoogleRouteMapProps> = ({
  fromLoc,
  toLoc,
  workspaceStops,
  routePolyline,
  onAddStop,
  onMarkerDragEnd,
  center = [12.971598, 80.2210],
  zoom = 12
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapInstanceRef = useRef<any>(null);
  const startMarkerRef = useRef<any>(null);
  const destMarkerRef = useRef<any>(null);
  const stopMarkersRef = useRef<any[]>([]);
  const polylineRef = useRef<any>(null);
  const infoWindowRef = useRef<any>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Initialize Google Map
  useEffect(() => {
    let isMounted = true;

    const initMap = async () => {
      try {
        setLoading(true);
        setError(null);
        await MapService.loadGoogleMapsScript();

        if (!isMounted || !containerRef.current) return;

        if (typeof google === 'undefined' || !google.maps) {
          throw new Error('Google Maps JavaScript API not loaded');
        }

        const map = new google.maps.Map(containerRef.current, {
          center: { lat: center[0], lng: center[1] },
          zoom: zoom,
          mapTypeId: google.maps.MapTypeId.ROADMAP,
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: true,
          zoomControl: true,
          styles: [
            {
              featureType: 'transit.station.bus',
              elementType: 'labels.icon',
              stylers: [{ visibility: 'on' }, { color: '#2563EB' }]
            }
          ]
        });

        infoWindowRef.current = new google.maps.InfoWindow();

        // Click on map to add stop
        map.addListener('click', (e: any) => {
          if (!e.latLng || !onAddStop) return;
          const lat = e.latLng.lat();
          const lng = e.latLng.lng();
          onAddStop(lat, lng);
        });

        mapInstanceRef.current = map;
        setLoading(false);
      } catch (err: any) {
        console.error('Failed to initialize Google Map:', err);
        if (isMounted) {
          setError(err.message || 'Unable to load Google Map.');
          setLoading(false);
        }
      }
    };

    initMap();

    return () => {
      isMounted = false;
      // Clean up markers
      if (startMarkerRef.current) startMarkerRef.current.setMap(null);
      if (destMarkerRef.current) destMarkerRef.current.setMap(null);
      stopMarkersRef.current.forEach(m => m.setMap(null));
      stopMarkersRef.current = [];
      if (polylineRef.current) polylineRef.current.setMap(null);
      mapInstanceRef.current = null;
    };
  }, []);

  // Update Markers and Polyline when route data changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || typeof google === 'undefined') return;

    const bounds = new google.maps.LatLngBounds();
    let hasPoints = false;

    // Helper for SVG marker icon
    const createMarkerIcon = (letter: string, bgColor: string) => ({
      url: `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`
        <svg xmlns="http://www.w3.org/2000/svg" width="34" height="42" viewBox="0 0 34 42">
          <path d="M17 0 C7.6 0 0 7.6 0 17 C0 29.8 17 42 17 42 C17 42 34 29.8 34 17 C34 7.6 26.4 0 17 0 Z" fill="${bgColor}" stroke="#FFFFFF" stroke-width="2"/>
          <circle cx="17" cy="16" r="11" fill="#FFFFFF"/>
          <text x="17" y="20" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="${bgColor}" text-anchor="middle">${letter}</text>
        </svg>
      `)}`,
      scaledSize: new google.maps.Size(34, 42),
      anchor: new google.maps.Point(17, 42)
    });

    // 1. Start Marker (A)
    if (fromLoc && fromLoc.latitude && fromLoc.longitude) {
      const pos = { lat: fromLoc.latitude, lng: fromLoc.longitude };
      bounds.extend(pos);
      hasPoints = true;

      if (!startMarkerRef.current) {
        startMarkerRef.current = new google.maps.Marker({
          position: pos,
          map: map,
          title: `START: ${fromLoc.name}`,
          icon: createMarkerIcon('A', '#10B981')
        });
        startMarkerRef.current.addListener('click', () => {
          infoWindowRef.current.setContent(`
            <div style="padding: 6px; font-family: sans-serif; font-size: 12px;">
              <strong style="color: #065F46; font-size: 13px;">ORIGIN (FROM)</strong>
              <div style="font-weight: bold; margin-top: 2px;">${fromLoc.name}</div>
              <div style="color: #6B7280; font-size: 11px; margin-top: 2px;">${fromLoc.address}</div>
            </div>
          `);
          infoWindowRef.current.open(map, startMarkerRef.current);
        });
      } else {
        startMarkerRef.current.setPosition(pos);
        startMarkerRef.current.setMap(map);
      }
    } else if (startMarkerRef.current) {
      startMarkerRef.current.setMap(null);
    }

    // 2. Destination Marker (B)
    if (toLoc && toLoc.latitude && toLoc.longitude) {
      const pos = { lat: toLoc.latitude, lng: toLoc.longitude };
      bounds.extend(pos);
      hasPoints = true;

      if (!destMarkerRef.current) {
        destMarkerRef.current = new google.maps.Marker({
          position: pos,
          map: map,
          title: `DESTINATION: ${toLoc.name}`,
          icon: createMarkerIcon('B', '#EF4444')
        });
        destMarkerRef.current.addListener('click', () => {
          infoWindowRef.current.setContent(`
            <div style="padding: 6px; font-family: sans-serif; font-size: 12px;">
              <strong style="color: #991B1B; font-size: 13px;">DESTINATION (TO)</strong>
              <div style="font-weight: bold; margin-top: 2px;">${toLoc.name}</div>
              <div style="color: #6B7280; font-size: 11px; margin-top: 2px;">${toLoc.address}</div>
            </div>
          `);
          infoWindowRef.current.open(map, destMarkerRef.current);
        });
      } else {
        destMarkerRef.current.setPosition(pos);
        destMarkerRef.current.setMap(map);
      }
    } else if (destMarkerRef.current) {
      destMarkerRef.current.setMap(null);
    }

    // 3. Intermediate Stop Markers
    stopMarkersRef.current.forEach(m => m.setMap(null));
    stopMarkersRef.current = [];

    workspaceStops.forEach((ws, idx) => {
      if (!ws.stop || !ws.stop.latitude || !ws.stop.longitude) return;
      const pos = { lat: ws.stop.latitude, lng: ws.stop.longitude };
      bounds.extend(pos);
      hasPoints = true;

      const marker = new google.maps.Marker({
        position: pos,
        map: map,
        title: `Stop #${ws.sequenceNumber}: ${ws.stop.stopName}`,
        draggable: true,
        icon: createMarkerIcon(String(ws.sequenceNumber), '#2563EB')
      });

      marker.addListener('click', () => {
        infoWindowRef.current.setContent(`
          <div style="padding: 6px; font-family: sans-serif; font-size: 12px;">
            <strong style="color: #1E40AF; font-size: 13px;">Stop #${ws.sequenceNumber}</strong>
            <div style="font-weight: bold; margin-top: 2px;">${ws.stop.stopName}</div>
            <div style="color: #4B5563; font-size: 11px; margin-top: 3px;">
              Arrival: <strong>${ws.expectedArrivalTime ? ws.expectedArrivalTime.substring(0, 5) : '--:--'}</strong>
              • Distance: <strong>${ws.distanceFromStart} km</strong>
            </div>
          </div>
        `);
        infoWindowRef.current.open(map, marker);
      });

      marker.addListener('dragend', (e: any) => {
        if (!e.latLng || !onMarkerDragEnd) return;
        onMarkerDragEnd(idx, e.latLng.lat(), e.latLng.lng());
      });

      stopMarkersRef.current.push(marker);
    });

    // 4. Polyline Route Drawing
    if (polylineRef.current) {
      polylineRef.current.setMap(null);
      polylineRef.current = null;
    }

    if (routePolyline && routePolyline.length > 1) {
      const path = routePolyline.map(([lat, lng]) => ({ lat, lng }));
      path.forEach(p => bounds.extend(p));
      hasPoints = true;

      polylineRef.current = new google.maps.Polyline({
        path: path,
        geodesic: true,
        strokeColor: '#2563EB',
        strokeOpacity: 0.85,
        strokeWeight: 5,
        map: map
      });
    }

    // Adjust bounds if points exist
    if (hasPoints && !bounds.isEmpty()) {
      map.fitBounds(bounds, { top: 50, bottom: 50, left: 50, right: 50 });
    }
  }, [fromLoc, toLoc, workspaceStops, routePolyline]);

  if (error) {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center bg-brandBg/60 text-brandTextSecondary p-6 text-center">
        <AlertCircle className="w-8 h-8 text-brandAmber mb-2" />
        <p className="font-bold text-xs text-brandNavy">{error}</p>
        <p className="text-[11px] text-brandTextSecondary mt-1">Please verify your Google Maps Platform API key.</p>
      </div>
    );
  }

  return (
    <div className="relative w-full h-full">
      {loading && (
        <div className="absolute inset-0 bg-white/70 backdrop-blur-xs flex items-center justify-center z-10">
          <div className="flex items-center gap-2 px-4 py-2 bg-white rounded-xl border border-brandBorder shadow-md text-xs font-bold text-brandBlue">
            <Loader2 className="w-4 h-4 animate-spin text-brandBlue" />
            <span>Loading Google Map...</span>
          </div>
        </div>
      )}
      <div ref={containerRef} className="w-full h-full" />
    </div>
  );
};
