import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import {
  Plus,
  Trash2,
  Edit2,
  ChevronLeft,
  ChevronRight,
  ArrowRight,
  ChevronUp,
  ChevronDown,
  Navigation,
  CheckCircle,
  MapPin,
  Clock,
  Search,
  X,
  Save,
  AlertTriangle,
  Map as MapIcon,
  MapPin as PinIcon,
  Loader2,
  Crosshair
} from 'lucide-react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapService, LocationSearchResult, RouteResult } from '../../services/MapService';
import { GoogleRouteMap } from '../../components/map/GoogleRouteMap';

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

interface RouteType {
  id: string;
  routeName: string;
  startPoint: string;
  endPoint: string;
  distance: number;
  estimatedDurationMins: number;
  status: string;
  version?: number;
  updatedAt?: string;
  stops?: RouteStopType[];
}

// Markers helper icons
const createStopIcon = (index: number) => {
  return L.divIcon({
    html: `<div class="w-8 h-8 bg-brandBlue border-2 border-white rounded-full flex items-center justify-center text-white font-extrabold text-xs shadow-md hover:scale-110 transition-transform">${index}</div>`,
    className: 'custom-stop-marker-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
};

const createStartIcon = () => {
  return L.divIcon({
    html: `<div class="w-8 h-8 bg-brandGreen border-2 border-white rounded-full flex items-center justify-center text-white font-bold text-xs shadow-md">A</div>`,
    className: 'custom-start-marker-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
};

const createEndIcon = () => {
  return L.divIcon({
    html: `<div class="w-8 h-8 bg-brandRed border-2 border-white rounded-full flex items-center justify-center text-white font-bold text-xs shadow-md">B</div>`,
    className: 'custom-end-marker-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
};

export const RoutesPage: React.FC = () => {
  // Routes search/list
  const [routes, setRoutes] = useState<RouteType[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  // Editor states
  const [isEditing, setIsEditing] = useState(false);
  const [editingRouteId, setEditingRouteId] = useState<string | null>(null);
  
  // Workspace properties
  const [routeName, setRouteName] = useState('');
  const [fromSearch, setFromSearch] = useState('');
  const [fromLoc, setFromLoc] = useState<LocationSearchResult | null>(null);
  const [fromSuggestions, setFromSuggestions] = useState<LocationSearchResult[]>([]);
  const [isFromSearching, setIsFromSearching] = useState(false);

  const [toSearch, setToSearch] = useState('');
  const [toLoc, setToLoc] = useState<LocationSearchResult | null>(null);
  const [toSuggestions, setToSuggestions] = useState<LocationSearchResult[]>([]);
  const [isToSearching, setIsToSearching] = useState(false);

  // Stops editor workspace
  const [workspaceStops, setWorkspaceStops] = useState<RouteStopType[]>([]);
  const [stopSearch, setStopSearch] = useState('');
  const [stopSuggestions, setStopSuggestions] = useState<LocationSearchResult[]>([]);
  const [isStopSearching, setIsStopSearching] = useState(false);
  const [locateLoading, setLocateLoading] = useState<{ [key: string]: boolean }>({});

  // Detailed stop editing modal
  const [editingStopIndex, setEditingStopIndex] = useState<number | null>(null);
  const [editStopName, setEditStopName] = useState('');
  const [editStopArr, setEditStopArr] = useState('08:00');
  const [editStopDep, setEditStopDep] = useState('08:05');
  const [editStopDist, setEditStopDist] = useState(0.0);
  const [editStopDur, setEditStopDur] = useState(5);

  // Map settings
  const [routePolyline, setRoutePolyline] = useState<[number, number][]>([]);
  const [distance, setDistance] = useState(0.0);
  const [duration, setDuration] = useState(30);

  // Alerts
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [saveLoading, setSaveLoading] = useState(false);

  const mapRef = useRef<L.Map | null>(null);

  // Fetch routes page list
  const fetchRoutes = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/admin/routes', {
        params: { page, size, search }
      });
      if (res.data.success) {
        setRoutes(res.data.data.content);
        setTotalElements(res.data.data.totalElements);
        setTotalPages(res.data.data.totalPages);
      }
    } catch (e) {
      setError('Failed to load routes list.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoutes();
  }, [page]);

  // Debounced search for FROM location
  useEffect(() => {
    if (!fromSearch || fromSearch.trim().length < 3 || fromLoc?.name === fromSearch) {
      setFromSuggestions([]);
      return;
    }
    setIsFromSearching(true);
    const delay = setTimeout(async () => {
      const results = await MapService.search(fromSearch);
      setFromSuggestions(results);
      setIsFromSearching(false);
    }, 350);
    return () => clearTimeout(delay);
  }, [fromSearch]);

  // Debounced search for TO location
  useEffect(() => {
    if (!toSearch || toSearch.trim().length < 3 || toLoc?.name === toSearch) {
      setToSuggestions([]);
      return;
    }
    setIsToSearching(true);
    const delay = setTimeout(async () => {
      const results = await MapService.search(toSearch);
      setToSuggestions(results);
      setIsToSearching(false);
    }, 350);
    return () => clearTimeout(delay);
  }, [toSearch]);

  // Debounced search for Stops location
  useEffect(() => {
    if (!stopSearch || stopSearch.trim().length < 3) {
      setStopSuggestions([]);
      return;
    }
    setIsStopSearching(true);
    const delay = setTimeout(async () => {
      const results = await MapService.search(stopSearch);
      setStopSuggestions(results);
      setIsStopSearching(false);
    }, 350);
    return () => clearTimeout(delay);
  }, [stopSearch]);

  // Trigger route directions calculation when From/To/Stops change
  useEffect(() => {
    if (fromLoc && toLoc) {
      calculateActiveRoute();
    } else {
      setRoutePolyline([]);
    }
  }, [fromLoc, toLoc, workspaceStops.length]);

  const formatTimeString = (totalMinutes: number): string => {
    const hours = Math.floor(totalMinutes / 60) % 24;
    const mins = Math.floor(totalMinutes % 60);
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:00`;
  };

  const calculateActiveRoute = async () => {
    if (!fromLoc || !toLoc) return;
    const stopsCoords = workspaceStops.map(s => ({
      lat: s.stop.latitude,
      lng: s.stop.longitude
    }));

    try {
      const result: RouteResult = await MapService.calculateRoute(
        { lat: fromLoc.latitude, lng: fromLoc.longitude },
        { lat: toLoc.latitude, lng: toLoc.longitude },
        stopsCoords
      );
      setRoutePolyline(result.polyline);
      setDistance(result.distanceKm);
      setDuration(result.durationMins);

      // If stops exist and we have leg calculations, update stops with real road leg metrics and cumulative ETAs
      if (workspaceStops.length > 0 && result.legs && result.legs.length > 0) {
        let runningDist = 0;
        let runningMins = 0;
        const baseStartMinute = 8 * 60; // 08:00 AM baseline

        const updatedStops = workspaceStops.map((stop, idx) => {
          const leg = result.legs[idx];
          const legDist = leg ? leg.distanceKm : 0;
          const legDur = leg ? leg.durationMins : 5;

          runningDist = parseFloat((runningDist + legDist).toFixed(2));
          runningMins += legDur;

          const arrTime = formatTimeString(baseStartMinute + runningMins);
          const depTime = formatTimeString(baseStartMinute + runningMins + 2); // 2 min dwell time

          return {
            ...stop,
            distanceFromStart: runningDist,
            durationFromStartMins: runningMins,
            expectedArrivalTime: arrTime,
            expectedDepartureTime: depTime,
            legDistanceKm: legDist,
            legDurationMins: legDur
          };
        });

        setWorkspaceStops(updatedStops);
      }
    } catch (e) {
      console.error('Failed to calculate route directions', e);
    }
  };

  // Open creation panel
  const handleOpenCreate = () => {
    setEditingRouteId(null);
    setRouteName('');
    setFromSearch('');
    setFromLoc(null);
    setToSearch('');
    setToLoc(null);
    setWorkspaceStops([]);
    setRoutePolyline([]);
    setDistance(0.0);
    setDuration(30);
    setIsEditing(true);
    setError('');
  };

  // Open edit panel
  const handleOpenEdit = async (route: RouteType) => {
    try {
      setError('');
      setSaveLoading(true);

      // Fetch route details and stops concurrently with resilient fallbacks
      const routeResPromise = axios.get(`/api/admin/routes/${route.id}`);
      const stopsResPromise = axios.get(`/api/admin/routes/${route.id}/stops`).catch(err => {
        console.warn('Dedicated stops endpoint warning:', err);
        return null;
      });

      const [res, stopsRes] = await Promise.all([routeResPromise, stopsResPromise]);

      const routeData = res.data?.data || res.data;
      if (!routeData) {
        throw new Error('Route data not returned from server');
      }

      // Stops can come from separate stops response OR from routeData.stops
      let stopsData: RouteStopType[] = [];
      if (stopsRes?.data?.success && Array.isArray(stopsRes.data.data)) {
        stopsData = stopsRes.data.data;
      } else if (Array.isArray(stopsRes?.data)) {
        stopsData = stopsRes.data;
      } else if (Array.isArray(routeData.stops)) {
        stopsData = routeData.stops;
      }

      setEditingRouteId(route.id);
      setRouteName(routeData.routeName || '');
      
      const startLat = routeData.startLatitude ?? stopsData[0]?.stop?.latitude ?? 12.9715;
      const startLng = routeData.startLongitude ?? stopsData[0]?.stop?.longitude ?? 80.2210;
      const endLat = routeData.endLatitude ?? (stopsData.length > 0 ? stopsData[stopsData.length - 1]?.stop?.latitude : undefined) ?? 12.9715;
      const endLng = routeData.endLongitude ?? (stopsData.length > 0 ? stopsData[stopsData.length - 1]?.stop?.longitude : undefined) ?? 80.2210;

      const fromItem: LocationSearchResult = {
        name: routeData.startPoint || '',
        address: routeData.startPoint || '',
        latitude: startLat,
        longitude: startLng
      };
      setFromLoc(fromItem);
      setFromSearch(routeData.startPoint || '');

      const toItem: LocationSearchResult = {
        name: routeData.endPoint || '',
        address: routeData.endPoint || '',
        latitude: endLat,
        longitude: endLng
      };
      setToLoc(toItem);
      setToSearch(routeData.endPoint || '');

      if (routeData.polyline) {
        try {
          const parsed = JSON.parse(routeData.polyline);
          if (Array.isArray(parsed) && parsed.length > 0) {
            setRoutePolyline(parsed);
          }
        } catch (ignored) {}
      }

      setWorkspaceStops(stopsData);
      setDistance(routeData.distance ?? 0);
      setDuration(routeData.estimatedDurationMins ?? 30);
      setIsEditing(true);

      if (mapRef.current && stopsData.length > 0 && stopsData[0]?.stop?.latitude) {
        try {
          mapRef.current.setView([stopsData[0].stop.latitude, stopsData[0].stop.longitude], 12);
        } catch (mapErr) {
          console.warn('Deferred map view update:', mapErr);
        }
      }
    } catch (e: any) {
      console.error('Error fetching route details for editing:', e);
      setError(e.response?.data?.message || e.message || 'Failed to fetch details for route editing.');
    } finally {
      setSaveLoading(false);
    }
  };

  // Add Stop
  const handleAddStopFromSearch = async (loc: LocationSearchResult) => {
    try {
      const resolved = await MapService.resolveLocation(loc);
      const nextSeq = workspaceStops.length + 1;
      const newStop: RouteStopType = {
        stop: {
          stopName: resolved.name,
          latitude: resolved.latitude,
          longitude: resolved.longitude
        },
        sequenceNumber: nextSeq,
        distanceFromStart: 0.0,
        durationFromStartMins: 5,
        expectedArrivalTime: '08:00:00',
        expectedDepartureTime: '08:05:00'
      };

      setWorkspaceStops(prev => [...prev, newStop]);
      setStopSearch('');
      setStopSuggestions([]);
      setSuccess(`Stop "${resolved.name}" added to timeline`);
    } catch (err) {
      console.error('Failed to resolve stop coordinates', err);
      setError('Could not resolve stop location coordinates.');
    }
  };

  // Browser Geolocation / Current Location handler
  const handleGetCurrentLocation = async (target: 'from' | 'to' | 'stop' | 'editStop') => {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported by your browser.');
      return;
    }

    setLocateLoading(prev => ({ ...prev, [target]: true }));
    setError('');

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const lat = position.coords.latitude;
          const lng = position.coords.longitude;
          const accuracy = position.coords.accuracy;

          if (accuracy > 1000) {
            console.warn(`Low GPS accuracy: ${accuracy}m`);
          }

          // Reverse geocode via MapService (Google Geocoder or OSM Nominatim fallback)
          const fullAddress = await MapService.reverseGeocode(lat, lng);
          const namePart = fullAddress.split(',')[0]?.trim() || `Location (${lat.toFixed(4)}, ${lng.toFixed(4)})`;

          const locResult: LocationSearchResult = {
            name: namePart,
            address: fullAddress,
            latitude: lat,
            longitude: lng
          };

          if (target === 'from') {
            setFromLoc(locResult);
            setFromSearch(namePart);
            setFromSuggestions([]);
            setSuccess(`Current location set as starting point: ${namePart}`);
          } else if (target === 'to') {
            setToLoc(locResult);
            setToSearch(namePart);
            setToSuggestions([]);
            setSuccess(`Current location set as destination: ${namePart}`);
          } else if (target === 'stop') {
            const nextSeq = workspaceStops.length + 1;
            const newStop: RouteStopType = {
              stop: {
                stopName: namePart,
                latitude: lat,
                longitude: lng
              },
              sequenceNumber: nextSeq,
              distanceFromStart: 0.0,
              durationFromStartMins: 5,
              expectedArrivalTime: '08:00:00',
              expectedDepartureTime: '08:05:00'
            };
            setWorkspaceStops(prev => [...prev, newStop]);
            setStopSearch('');
            setStopSuggestions([]);
            setSuccess(`Current location added as stop: ${namePart}`);
          } else if (target === 'editStop') {
            if (editingStopIndex !== null) {
              const updated = [...workspaceStops];
              const stopToUpdate = updated[editingStopIndex];
              stopToUpdate.stop.stopName = namePart;
              stopToUpdate.stop.latitude = lat;
              stopToUpdate.stop.longitude = lng;
              setWorkspaceStops(updated);
              setEditStopName(namePart);
              setSuccess(`Stop #${editingStopIndex + 1} updated to current location: ${namePart}`);
            }
          }

          // Center and zoom map to the acquired coordinates
          if (mapRef.current) {
            mapRef.current.setView([lat, lng], 14);
          }
        } catch (err) {
          console.error('Failed to reverse geocode current location:', err);
          setError('Failed to resolve address for current location.');
        } finally {
          setLocateLoading(prev => ({ ...prev, [target]: false }));
        }
      },
      (geoError) => {
        setLocateLoading(prev => ({ ...prev, [target]: false }));
        let msg = 'Failed to get current location.';
        switch (geoError.code) {
          case geoError.PERMISSION_DENIED:
            msg = 'Location access was denied. Please allow location permissions in your browser.';
            break;
          case geoError.POSITION_UNAVAILABLE:
            msg = 'Location information is unavailable. Please check your GPS / network connection.';
            break;
          case geoError.TIMEOUT:
            msg = 'Location request timed out. Please try again.';
            break;
        }
        setError(msg);
      },
      {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 10000
      }
    );
  };

  // Drag Stop marker handler
  const handleMarkerDragEnd = async (index: number, newLat: number, newLng: number) => {
    const updated = [...workspaceStops];
    const stopToUpdate = updated[index];
    
    stopToUpdate.stop.latitude = newLat;
    stopToUpdate.stop.longitude = newLng;
    
    // Reverse geocode to suggest a new address/name
    const address = await MapService.reverseGeocode(newLat, newLng);
    stopToUpdate.stop.stopName = address.split(',')[0] || `Stop at ${newLat.toFixed(4)}`;
    
    setWorkspaceStops(updated);
    setSuccess(`Stop ${index + 1} position updated!`);
    calculateActiveRoute();
  };

  // Click Map coordinate stop adder
  const handleAddStopAtCoord = async (lat: number, lng: number) => {
    if (!isEditing) return;
    if (window.confirm(`Add a new stop at coordinates [${lat.toFixed(5)}, ${lng.toFixed(5)}]?`)) {
      const suggestedAddress = await MapService.reverseGeocode(lat, lng);
      const name = suggestedAddress.split(',')[0] || `Stop at ${lat.toFixed(4)}`;

      const nextSeq = workspaceStops.length + 1;
      const newStop: RouteStopType = {
        stop: {
          stopName: name,
          latitude: lat,
          longitude: lng
        },
        sequenceNumber: nextSeq,
        distanceFromStart: 0.0,
        durationFromStartMins: 5,
        expectedArrivalTime: '08:00:00',
        expectedDepartureTime: '08:05:00'
      };
      setWorkspaceStops(prev => [...prev, newStop]);
      setSuccess(`Stop "${name}" added to timeline`);
    }
  };

  // Click Map handler for Leaflet
  const MapClickHandler = () => {
    useMapEvents({
      click(e) {
        handleAddStopAtCoord(e.latlng.lat, e.latlng.lng);
      }
    });
    return null;
  };

  // Save Route trigger
  const handleSaveRoute = async () => {
    const startPointName = fromSearch.trim() || fromLoc?.name || '';
    const endPointName = toSearch.trim() || toLoc?.name || '';

    if (!routeName.trim()) {
      setError('Please provide a route name.');
      return;
    }
    if (!startPointName || !endPointName) {
      setError('Please select start (FROM) and destination (TO) locations.');
      return;
    }
    if (startPointName === endPointName) {
      setError('Start and destination locations cannot be identical.');
      return;
    }
    if (workspaceStops.length === 0) {
      setError('At least one stop must be defined along the route.');
      return;
    }

    setSaveLoading(true);
    setError('');

    try {
      let savedRouteId = editingRouteId;
      const routePayload = {
        routeName: routeName.trim(),
        startPoint: startPointName,
        endPoint: endPointName,
        distance,
        estimatedDurationMins: duration,
        startLatitude: fromLoc?.latitude || (workspaceStops.length > 0 ? workspaceStops[0].stop.latitude : null),
        startLongitude: fromLoc?.longitude || (workspaceStops.length > 0 ? workspaceStops[0].stop.longitude : null),
        endLatitude: toLoc?.latitude || (workspaceStops.length > 0 ? workspaceStops[workspaceStops.length - 1].stop.latitude : null),
        endLongitude: toLoc?.longitude || (workspaceStops.length > 0 ? workspaceStops[workspaceStops.length - 1].stop.longitude : null),
        polyline: routePolyline && routePolyline.length > 0 ? JSON.stringify(routePolyline) : null
      };

      // Prepare atomic stops synchronization payload
      const stopsPayload = workspaceStops.map((ws, idx) => ({
        stop: {
          stopName: ws.stop.stopName,
          latitude: ws.stop.latitude,
          longitude: ws.stop.longitude
        },
        sequenceNumber: idx + 1,
        distanceFromStart: ws.distanceFromStart || 0.0,
        durationFromStartMins: ws.durationFromStartMins || 5,
        expectedArrivalTime: ws.expectedArrivalTime ? (ws.expectedArrivalTime.length <= 5 ? ws.expectedArrivalTime + ':00' : ws.expectedArrivalTime.substring(0, 8)) : '08:00:00',
        expectedDepartureTime: ws.expectedDepartureTime ? (ws.expectedDepartureTime.length <= 5 ? ws.expectedDepartureTime + ':00' : ws.expectedDepartureTime.substring(0, 8)) : '08:05:00'
      }));

      let updatedVersion = 1;
      if (editingRouteId) {
        // Edit existing route - includes stops atomically in payload
        const putRes = await axios.put(`/api/admin/routes/${editingRouteId}`, {
          ...routePayload,
          stops: stopsPayload
        });
        updatedVersion = putRes.data.data?.version || 2;
      } else {
        // Create new route
        const res = await axios.post('/api/admin/routes', routePayload);
        savedRouteId = res.data.data.id;
        if (!savedRouteId) throw new Error('Save route failed');
        await axios.put(`/api/admin/routes/${savedRouteId}/stops/sync`, stopsPayload);
        updatedVersion = 1;
      }

      setSuccess(`Route "${routeName.trim()}" and ${stopsPayload.length} stops saved & broadcasted (v${updatedVersion})!`);
      setIsEditing(false);
      setEditingRouteId(null);
      mapRef.current = null;
      await fetchRoutes();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to save route. Check timeline sequences.');
    } finally {
      setSaveLoading(false);
    }
  };

  const handleRemoveStop = (index: number) => {
    const filtered = workspaceStops.filter((_, idx) => idx !== index).map((ws, idx) => ({
      ...ws,
      sequenceNumber: idx + 1
    }));
    setWorkspaceStops(filtered);
    setSuccess('Stop removed from sequence timeline.');
  };

  const handleReorderStops = (index: number, direction: 'UP' | 'DOWN') => {
    const updated = [...workspaceStops];
    const targetIdx = direction === 'UP' ? index - 1 : index + 1;
    if (targetIdx < 0 || targetIdx >= updated.length) return;

    const temp = updated[index];
    updated[index] = updated[targetIdx];
    updated[targetIdx] = temp;

    // Recalculate sequences
    const resequenced = updated.map((item, idx) => ({
      ...item,
      sequenceNumber: idx + 1
    }));
    setWorkspaceStops(resequenced);
  };

  const handleDeleteRoute = async (id: string) => {
    if (!window.confirm('Delete this route path? This action soft-deletes the path.')) return;
    try {
      const res = await axios.delete(`/api/admin/routes/${id}`);
      if (res.data.success) {
        setSuccess('Route deleted successfully.');
        fetchRoutes();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to delete route.');
    }
  };

  // Open detailed stop settings
  const openEditStopDetails = (index: number) => {
    const item = workspaceStops[index];
    setEditingStopIndex(index);
    setEditStopName(item.stop.stopName);
    setEditStopArr(item.expectedArrivalTime?.substring(0, 5) || '08:00');
    setEditStopDep(item.expectedDepartureTime?.substring(0, 5) || '08:05');
    setEditStopDist(item.distanceFromStart);
    setEditStopDur(item.durationFromStartMins);
  };

  const saveEditStopDetails = () => {
    if (editingStopIndex === null) return;
    const updated = [...workspaceStops];
    const target = updated[editingStopIndex];
    
    target.stop.stopName = editStopName;
    target.expectedArrivalTime = editStopArr + ':00';
    target.expectedDepartureTime = editStopDep + ':00';
    target.distanceFromStart = editStopDist;
    target.durationFromStartMins = editStopDur;

    setWorkspaceStops(updated);
    setEditingStopIndex(null);
    setSuccess('Stop parameters updated.');
  };

  // Positions list for Leaflet route polyline
  const polylinePositions: [number, number][] = routePolyline.length > 0
    ? routePolyline
    : [
        ...(fromLoc ? [[fromLoc.latitude, fromLoc.longitude] as [number, number]] : []),
        ...workspaceStops.map(s => [s.stop.latitude, s.stop.longitude] as [number, number]),
        ...(toLoc ? [[toLoc.latitude, toLoc.longitude] as [number, number]] : [])
      ];

  const mapCenter: [number, number] = fromLoc
    ? [fromLoc.latitude, fromLoc.longitude]
    : [12.971598, 80.2210];

  return (
    <div className="space-y-6">
      {/* Dynamic API warning message for fallback mode */}
      {!MapService.isGoogleMapsEnabled() && (
        <div className="bg-brandAmber/10 border border-brandAmber/20 text-brandAmber p-4 rounded-2xl text-xs flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 shrink-0 text-brandAmber" />
          <div className="font-semibold">
            Google Maps is not configured. Using development map mode (OpenStreetMap & OSRM engine).
          </div>
        </div>
      )}

      {/* HEADER PANEL */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-brandNavy tracking-tight">Route & Stop Intelligence Builder</h2>
          <p className="text-xs text-brandTextSecondary mt-0.5">Build optimal routes, search geocoding stops, and reorder sequence milestones</p>
        </div>
        {!isEditing && (
          <button
            onClick={handleOpenCreate}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md shadow-brandBlue/10 transition-all"
          >
            <Plus className="w-4 h-4" /> Create Route Path
          </button>
        )}
      </div>

      {error && <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs font-bold">{error}</div>}
      {success && <div className="p-4 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-2xl text-xs font-bold">{success}</div>}

      {isEditing ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* LEFT BUILDER SIDEBAR */}
          <div className="lg:col-span-5 space-y-6">
            <div className="bg-white border border-brandBorder rounded-3xl p-6 space-y-6 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Route Parameters</h3>
              
              <div className="space-y-4 text-xs">
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1">Route Name</label>
                  <input
                    type="text"
                    placeholder="e.g. CIT Campus - Central Bus Stand"
                    value={routeName}
                    onChange={(e) => setRouteName(e.target.value)}
                    className="w-full px-4 h-11 bg-brandBg/60 border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all text-xs"
                  />
                </div>

                {/* START POINT SEARCH */}
                <div className="relative">
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-brandTextPrimary font-bold">FROM (Starting Location)</label>
                    <button
                      type="button"
                      onClick={() => handleGetCurrentLocation('from')}
                      disabled={locateLoading['from']}
                      title="Use current GPS location"
                      className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-semibold text-brandBlue hover:text-brandBlue/80 hover:bg-brandBlue/5 rounded-lg border border-brandBlue/20 transition-all disabled:opacity-50"
                    >
                      {locateLoading['from'] ? (
                        <>
                          <Loader2 className="w-3 h-3 animate-spin" />
                          <span>Locating...</span>
                        </>
                      ) : (
                        <>
                          <Crosshair className="w-3 h-3 text-brandBlue" />
                          <span>Current Location</span>
                        </>
                      )}
                    </button>
                  </div>
                  <div className="relative">
                    <input
                      type="text"
                      placeholder="Search starting point..."
                      value={fromSearch}
                      onChange={(e) => setFromSearch(e.target.value)}
                      className="w-full pl-10 pr-4 h-11 bg-brandBg/60 border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all text-xs"
                    />
                    <Search className="absolute left-3 top-3.5 w-4 h-4 text-brandTextSecondary" />
                  </div>
                  {isFromSearching && (
                    <div className="absolute right-3 top-9 text-brandBlue text-[10px] font-bold animate-pulse">Searching...</div>
                  )}
                  {fromSuggestions.length > 0 && (
                    <div className="absolute left-0 right-0 mt-1 bg-white border border-brandBorder rounded-xl shadow-xl z-[2000] max-h-[180px] overflow-y-auto">
                      {fromSuggestions.map((item, idx) => (
                        <div
                          key={idx}
                          onClick={async () => {
                            const resolved = await MapService.resolveLocation(item);
                            setFromLoc(resolved);
                            setFromSearch(resolved.name);
                            setFromSuggestions([]);
                          }}
                          className="p-3 hover:bg-brandBlue/5 cursor-pointer text-brandTextPrimary border-b border-brandBorder/60 hover:text-brandBlue transition-colors"
                        >
                          <p className="font-bold text-xs">{item.name}</p>
                          <p className="text-[10px] text-brandTextSecondary truncate mt-0.5">{item.address}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* DESTINATION SEARCH */}
                <div className="relative">
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-brandTextPrimary font-bold">TO (Destination)</label>
                    <button
                      type="button"
                      onClick={() => handleGetCurrentLocation('to')}
                      disabled={locateLoading['to']}
                      title="Use current GPS location"
                      className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-semibold text-brandBlue hover:text-brandBlue/80 hover:bg-brandBlue/5 rounded-lg border border-brandBlue/20 transition-all disabled:opacity-50"
                    >
                      {locateLoading['to'] ? (
                        <>
                          <Loader2 className="w-3 h-3 animate-spin" />
                          <span>Locating...</span>
                        </>
                      ) : (
                        <>
                          <Crosshair className="w-3 h-3 text-brandBlue" />
                          <span>Current Location</span>
                        </>
                      )}
                    </button>
                  </div>
                  <div className="relative">
                    <input
                      type="text"
                      placeholder="Search destination..."
                      value={toSearch}
                      onChange={(e) => setToSearch(e.target.value)}
                      className="w-full pl-10 pr-4 h-11 bg-brandBg/60 border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all text-xs"
                    />
                    <Search className="absolute left-3 top-3.5 w-4 h-4 text-brandTextSecondary" />
                  </div>
                  {isToSearching && (
                    <div className="absolute right-3 top-9 text-brandBlue text-[10px] font-bold animate-pulse">Searching...</div>
                  )}
                  {toSuggestions.length > 0 && (
                    <div className="absolute left-0 right-0 mt-1 bg-white border border-brandBorder rounded-xl shadow-xl z-[2000] max-h-[180px] overflow-y-auto">
                      {toSuggestions.map((item, idx) => (
                        <div
                          key={idx}
                          onClick={async () => {
                            const resolved = await MapService.resolveLocation(item);
                            setToLoc(resolved);
                            setToSearch(resolved.name);
                            setToSuggestions([]);
                          }}
                          className="p-3 hover:bg-brandBlue/5 cursor-pointer text-brandTextPrimary border-b border-brandBorder/60 hover:text-brandBlue transition-colors"
                        >
                          <p className="font-bold text-xs">{item.name}</p>
                          <p className="text-[10px] text-brandTextSecondary truncate mt-0.5">{item.address}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* TIMELINE LIST */}
              <div className="border-t border-brandBorder pt-6 space-y-4">
                <h4 className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider">Sequence Timeline</h4>

                {/* ADD STOP SEARCH BOX */}
                <div className="space-y-2 text-xs">
                  <div className="flex items-center gap-2">
                    <div className="relative flex-1">
                      <input
                        type="text"
                        placeholder="+ Add Stop (Search location...)"
                        value={stopSearch}
                        onChange={(e) => setStopSearch(e.target.value)}
                        className="w-full pl-10 pr-4 h-11 bg-brandBg/40 border border-dashed border-brandBorder hover:border-brandBlue/50 rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all text-xs placeholder:text-brandTextSecondary/60"
                      />
                      <Search className="absolute left-3 top-3.5 w-4 h-4 text-brandTextSecondary" />
                      {isStopSearching && (
                        <div className="absolute right-3 top-3.5 text-brandBlue text-[10px] font-bold animate-pulse">Searching...</div>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => handleGetCurrentLocation('stop')}
                      disabled={locateLoading['stop']}
                      title="Add current GPS location as stop"
                      className="inline-flex items-center gap-1.5 px-3 h-11 bg-white hover:bg-brandBlue/5 border border-brandBlue/20 hover:border-brandBlue/40 text-brandBlue rounded-xl font-bold transition-all shrink-0 text-xs shadow-sm disabled:opacity-50"
                    >
                      {locateLoading['stop'] ? (
                        <>
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          <span>Locating...</span>
                        </>
                      ) : (
                        <>
                          <Crosshair className="w-3.5 h-3.5 text-brandBlue" />
                          <span>Current Location</span>
                        </>
                      )}
                    </button>
                  </div>
                  {stopSuggestions.length > 0 && (
                    <div className="mt-1 bg-white border border-brandBorder rounded-xl shadow-xl z-[2000] max-h-[180px] overflow-y-auto">
                      {stopSuggestions.map((item, idx) => (
                        <div
                          key={idx}
                          onClick={() => handleAddStopFromSearch(item)}
                          className="p-3 hover:bg-brandBlue/5 cursor-pointer text-brandTextPrimary border-b border-brandBorder/60 hover:text-brandBlue transition-colors"
                        >
                          <p className="font-bold text-xs">{item.name}</p>
                          <p className="text-[10px] text-brandTextSecondary truncate mt-0.5">{item.address}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* TIMELINE TIMING CARD LIST */}
                <div className="space-y-3 max-h-[280px] overflow-y-auto pr-1">
                  {workspaceStops.length === 0 ? (
                    <div className="p-5 border border-dashed border-brandBorder rounded-2xl text-center text-brandTextSecondary text-xs font-semibold">
                      No stops added. Add stops via search or click map coordinates.
                    </div>
                  ) : (
                    workspaceStops.map((ws, index) => (
                      <div key={index} className="p-3.5 bg-brandBg/40 border border-brandBorder rounded-2xl flex items-center justify-between text-xs hover:border-brandBlue/20 transition-colors">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-7 h-7 bg-brandBlue/10 border border-brandBlue/20 text-brandBlue font-extrabold rounded-lg flex items-center justify-center text-[10px] shrink-0">
                            {ws.sequenceNumber}
                          </div>
                          <div className="truncate">
                            <p className="font-bold text-brandTextPrimary truncate">{ws.stop.stopName}</p>
                            <div className="text-[10px] text-brandTextSecondary mt-0.5 font-bold flex items-center gap-1.5 flex-wrap">
                              <span className="flex items-center gap-1"><Clock className="w-3.5 h-3.5 text-brandBlue" /> {ws.expectedArrivalTime?.substring(0, 5) || '--:--'}</span>
                              <span>•</span>
                              <span>{ws.distanceFromStart} km total</span>
                              {ws.legDistanceKm !== undefined && ws.legDurationMins !== undefined && (
                                <>
                                  <span>•</span>
                                  <span className="text-brandBlue font-extrabold bg-brandBlue/5 px-1.5 py-0.5 rounded border border-brandBlue/10">
                                    + {ws.legDistanceKm} km ({ws.legDurationMins}m)
                                  </span>
                                </>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-1 shrink-0 ml-2">
                          <button
                            onClick={() => openEditStopDetails(index)}
                            className="p-1 hover:bg-brandBg text-brandTextSecondary hover:text-brandBlue rounded transition-all"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleReorderStops(index, 'UP')}
                            disabled={index === 0}
                            className="p-1 hover:bg-brandBg text-brandTextSecondary hover:text-brandTextPrimary disabled:opacity-20 rounded transition-all"
                          >
                            <ChevronUp className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleReorderStops(index, 'DOWN')}
                            disabled={index === workspaceStops.length - 1}
                            className="p-1 hover:bg-brandBg text-brandTextSecondary hover:text-brandTextPrimary disabled:opacity-20 rounded transition-all"
                          >
                            <ChevronDown className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleRemoveStop(index)}
                            className="p-1 hover:bg-brandRed/10 text-brandTextSecondary hover:text-brandRed rounded transition-all"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* SAVE / CANCEL BUTTONS */}
              <div className="border-t border-brandBorder pt-6 space-y-4">
                {/* Route statistics summary */}
                <div className="grid grid-cols-2 gap-4 text-center text-xs font-semibold">
                  <div className="bg-brandBg border border-brandBorder p-3 rounded-2xl">
                    <p className="text-[9px] text-brandTextSecondary uppercase tracking-wider mb-0.5 font-bold">Route Distance</p>
                    <p className="text-sm font-extrabold text-brandNavy">{distance} km</p>
                  </div>
                  <div className="bg-brandBg border border-brandBorder p-3 rounded-2xl">
                    <p className="text-[9px] text-brandTextSecondary uppercase tracking-wider mb-0.5 font-bold">Estimated Duration</p>
                    <p className="text-sm font-extrabold text-brandNavy">{duration} mins</p>
                  </div>
                </div>

                <div className="flex gap-3">
                  <button
                    onClick={handleSaveRoute}
                    disabled={saveLoading}
                    className="flex-1 h-12 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold flex items-center justify-center gap-2 shadow-md shadow-brandBlue/10 transition-all text-xs"
                  >
                    {saveLoading ? 'Saving...' : <><Save className="w-4 h-4" /> Save Route Path</>}
                  </button>
                  <button
                    onClick={() => { setIsEditing(false); setEditingRouteId(null); mapRef.current = null; }}
                    className="px-5 h-12 border border-brandBorder hover:bg-brandBg text-brandTextPrimary rounded-xl text-xs font-bold transition-all"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* RIGHT MAP EDITOR LAYER */}
          <div className="lg:col-span-7">
            <div className="bg-white border border-brandBorder rounded-3xl overflow-hidden flex flex-col h-[650px] shadow-sm">
              <div className="h-14 bg-brandBg border-b border-brandBorder px-4 flex justify-between items-center text-xs shrink-0">
                <div className="flex items-center gap-2 font-bold text-brandNavy">
                  <MapIcon className="w-4 h-4 text-brandBlue" />
                  <span>Visual Route Planner</span>
                </div>
                <span className="text-[10px] text-brandTextSecondary font-semibold">Click map to add stops, drag pins to shift coordinates</span>
              </div>

              <div className="flex-1 bg-brandBg relative z-0">
                {MapService.isGoogleMapsEnabled() ? (
                  <GoogleRouteMap
                    fromLoc={fromLoc}
                    toLoc={toLoc}
                    workspaceStops={workspaceStops}
                    routePolyline={routePolyline}
                    onAddStop={handleAddStopAtCoord}
                    onMarkerDragEnd={handleMarkerDragEnd}
                    center={mapCenter}
                    zoom={12}
                  />
                ) : (
                  <MapContainer
                    center={mapCenter}
                    zoom={12}
                    style={{ width: '100%', height: '100%' }}
                    ref={map => { mapRef.current = map; }}
                  >
                    <TileLayer
                      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                      attribution='&copy; OpenStreetMap contributors'
                    />
                    
                    {/* Start Point Marker */}
                    {fromLoc && (
                      <Marker position={[fromLoc.latitude, fromLoc.longitude]} icon={createStartIcon()}>
                        <Popup>
                          <div className="p-1">
                            <p className="font-bold text-brandNavy">START POINT</p>
                            <p className="text-xs text-brandTextSecondary mt-0.5">{fromLoc.name}</p>
                          </div>
                        </Popup>
                      </Marker>
                    )}

                    {/* End Point Marker */}
                    {toLoc && (
                      <Marker position={[toLoc.latitude, toLoc.longitude]} icon={createEndIcon()}>
                        <Popup>
                          <div className="p-1">
                            <p className="font-bold text-brandNavy">DESTINATION</p>
                            <p className="text-xs text-brandTextSecondary mt-0.5">{toLoc.name}</p>
                          </div>
                        </Popup>
                      </Marker>
                    )}

                    {/* Stops Map Events Binder */}
                    <MapClickHandler />

                    {/* Stops Markers */}
                    {workspaceStops.map((item, idx) => (
                      <Marker
                        key={idx}
                        position={[item.stop.latitude, item.stop.longitude]}
                        draggable={true}
                        icon={createStopIcon(item.sequenceNumber)}
                        eventHandlers={{
                          dragend: (event) => {
                            const marker = event.target;
                            const pos = marker.getLatLng();
                            handleMarkerDragEnd(idx, pos.lat, pos.lng);
                          }
                        }}
                      >
                        <Popup>
                          <div className="p-1 text-brandTextPrimary text-xs">
                            <p className="font-bold text-brandNavy">Stop #{item.sequenceNumber}</p>
                            <p className="mt-0.5 font-semibold text-brandTextPrimary">{item.stop.stopName}</p>
                            <p className="text-[10px] text-brandTextSecondary mt-1">Arrival: {item.expectedArrivalTime?.substring(0, 5)}</p>
                          </div>
                        </Popup>
                      </Marker>
                    ))}

                    {/* Polyline Route Path */}
                    {polylinePositions.length > 1 && (
                      <Polyline positions={polylinePositions} color="#2563EB" weight={4} opacity={0.8} />
                    )}
                  </MapContainer>
                )}
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* STANDARD ROUTES PAGE GRID LIST */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          <div className="lg:col-span-4 space-y-4">
            <div className="bg-white border border-brandBorder rounded-2xl overflow-hidden p-4 space-y-4 shadow-sm">
              <h3 className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider">Transit Paths</h3>

              {/* SEARCH FILTER */}
              <div className="relative">
                <input
                  type="text"
                  placeholder="Search routes..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="w-full pl-9 pr-4 h-10 bg-brandBg/60 border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 text-xs focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
                />
                <Search className="absolute left-3 top-3.5 w-3.5 h-3.5 text-brandTextSecondary" />
              </div>

              {loading ? (
                <div className="py-8 text-center text-brandTextSecondary text-xs font-semibold">Loading paths...</div>
              ) : routes.length === 0 ? (
                <div className="py-8 text-center text-brandTextSecondary text-xs font-semibold">No transit routes found.</div>
              ) : (
                <div className="space-y-2 max-h-[400px] overflow-y-auto pr-1">
                  {routes.map(r => (
                    <div
                      key={r.id}
                      onClick={() => handleOpenEdit(r)}
                      className="p-3.5 bg-white border border-brandBorder hover:border-brandBlue/35 hover:bg-brandBlue/5 rounded-xl flex items-center justify-between text-xs cursor-pointer transition-all"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="font-bold text-brandNavy truncate">{r.routeName}</p>
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-black bg-blue-50 text-blue-700 border border-blue-200 shrink-0">
                            v{r.version || 1}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 mt-1 text-[10px] text-brandTextSecondary font-bold">
                          <span>{r.distance} km</span>
                          <span>•</span>
                          <span>{r.estimatedDurationMins} mins</span>
                        </div>
                      </div>

                      <div className="flex gap-1 shrink-0 ml-2">
                        <button
                          onClick={(e) => { e.stopPropagation(); handleOpenEdit(r); }}
                          className="p-1.5 hover:bg-brandBg text-brandTextSecondary hover:text-brandBlue rounded transition-all"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDeleteRoute(r.id); }}
                          className="p-1.5 hover:bg-brandRed/10 text-brandTextSecondary hover:text-brandRed rounded transition-all"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="lg:col-span-8">
            <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary h-[500px] flex flex-col items-center justify-center shadow-sm">
              <Navigation className="w-12 h-12 mb-3 text-brandBlue opacity-60 animate-pulse" />
              <h4 className="text-sm font-bold text-brandNavy">No Route Selected</h4>
              <p className="text-xs text-brandTextSecondary mt-2 max-w-sm font-medium">Select an existing transit path from the sidebar list to inspect details, or click &quot;Create Route Path&quot; to build a fresh campus route schedule.</p>
            </div>
          </div>
        </div>
      )}

      {/* DETAILED STOP SETTINGS MODAL */}
      {editingStopIndex !== null && (
        <div className="fixed inset-0 bg-brandNavy/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 text-xs text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4 flex items-center gap-2">
              <PinIcon className="w-4 h-4 text-brandBlue" />
              <span>Configure Stop Parameters</span>
            </h3>
            
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-brandTextPrimary font-bold">Stop Name</label>
                  <button
                    type="button"
                    onClick={() => handleGetCurrentLocation('editStop')}
                    disabled={locateLoading['editStop']}
                    title="Update to current GPS location"
                    className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-semibold text-brandBlue hover:text-brandBlue/80 hover:bg-brandBlue/5 rounded-lg border border-brandBlue/20 transition-all disabled:opacity-50"
                  >
                    {locateLoading['editStop'] ? (
                      <>
                        <Loader2 className="w-3 h-3 animate-spin" />
                        <span>Locating...</span>
                      </>
                    ) : (
                      <>
                        <Crosshair className="w-3 h-3 text-brandBlue" />
                        <span>Current Location</span>
                      </>
                    )}
                  </button>
                </div>
                <input
                  type="text"
                  value={editStopName}
                  onChange={e => setEditStopName(e.target.value)}
                  className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1">Arrival Time</label>
                  <input
                    type="time"
                    value={editStopArr}
                    onChange={e => setEditStopArr(e.target.value)}
                    className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue"
                  />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1">Departure Time</label>
                  <input
                    type="time"
                    value={editStopDep}
                    onChange={e => setEditStopDep(e.target.value)}
                    className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1">Distance from start (km)</label>
                  <input
                    type="number"
                    step="0.1"
                    value={editStopDist}
                    onChange={e => setEditStopDist(parseFloat(e.target.value))}
                    className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue"
                  />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1">Duration from start (mins)</label>
                  <input
                    type="number"
                    value={editStopDur}
                    onChange={e => setEditStopDur(parseInt(e.target.value))}
                    className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue"
                  />
                </div>
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button
                  type="button"
                  onClick={() => setEditingStopIndex(null)}
                  className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={saveEditStopDetails}
                  className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all"
                >
                  Save Parameters
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
