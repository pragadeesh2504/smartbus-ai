import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import {
  User as UserIcon,
  MapPin,
  Sparkles,
  Navigation,
  Compass,
  Check,
  Bus,
  CheckCircle2
} from 'lucide-react';

interface ProfileData {
  id: string;
  userId: string;
  collegeEmail: string;
  name: string;
  registerNumber: string;
  department: string;
  batch: string;
  phone: string;
  homeLatitude: number | null;
  homeLongitude: number | null;
  homeAddress: string | null;
  preferredRouteId: string | null;
  preferredRouteName: string | null;
  preferredBusId: string | null;
  preferredBusNumber: string | null;
  preferredStopId: string | null;
  preferredStopName: string | null;
  notificationPreferences: string;
}

interface StopSummary {
  id: string;
  stopName: string;
}

interface NearbyStop {
  stopId: string;
  stopName: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
}

interface RouteOption {
  id: string;
  routeName: string;
  stops: StopSummary[];
}

interface BusSummary {
  busId: string;
  busNumber: string;
  busCode: string;
  routeId?: string;
  routeName?: string;
}

export const Profile: React.FC = () => {
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [routes, setRoutes] = useState<RouteOption[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState<string>('');
  const [availableBuses, setAvailableBuses] = useState<BusSummary[]>([]);
  const [selectedBusId, setSelectedBusId] = useState<string>('');
  const [selectedStopId, setSelectedStopId] = useState<string>('');
  const [stops, setStops] = useState<StopSummary[]>([]);
  const [nearbyStops, setNearbyStops] = useState<NearbyStop[]>([]);
  const [loading, setLoading] = useState(true);
  const [saveSuccess, setSaveSuccess] = useState('');
  const [error, setError] = useState('');

  // Location fields
  const [lat, setLat] = useState<number | null>(null);
  const [lng, setLng] = useState<number | null>(null);
  const [address, setAddress] = useState('');

  // Custom Pin Icon
  const getPinIcon = () => L.divIcon({
    html: `<div class="bg-brandRed text-white p-2 rounded-full shadow-lg border border-white flex items-center justify-center w-8 h-8"><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-map-pin"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg></div>`,
    className: 'custom-home-pin',
    iconSize: [32, 32],
    iconAnchor: [16, 32],
  });

  const fetchProfileAndStops = async () => {
    try {
      // 1. Fetch Profile
      const profRes = await axios.get('/api/student/profile');
      const profData = profRes.data;
      setProfile(profData);
      setSelectedRouteId(profData.preferredRouteId || '');
      setSelectedBusId(profData.preferredBusId || '');
      setSelectedStopId(profData.preferredStopId || '');

      if (profData.homeLatitude) {
        setLat(profData.homeLatitude);
        setLng(profData.homeLongitude);
        setAddress(profData.homeAddress || '');
      } else {
        // Default center Chennai
        setLat(12.971598);
        setLng(77.594562);
      }

      // 2. Fetch all routes and stops
      const routesRes = await axios.get('/api/student/routes');
      const loadedRoutes: RouteOption[] = [];

      for (const r of routesRes.data) {
        try {
          const detailRes = await axios.get(`/api/student/routes/${r.id}`);
          const rStops: StopSummary[] = (detailRes.data.stops || []).map((s: any) => ({
            id: s.stopId,
            stopName: s.stopName
          }));
          loadedRoutes.push({
            id: r.id,
            routeName: r.routeName,
            stops: rStops
          });
        } catch (e) {
          console.warn(`Failed to fetch stops for route ${r.id}`, e);
        }
      }
      setRoutes(loadedRoutes);

      // Collect all stops
      const allStopsMap = new Map<string, StopSummary>();
      loadedRoutes.forEach(lr => lr.stops.forEach(s => allStopsMap.set(s.id, s)));
      setStops(Array.from(allStopsMap.values()));

      // 3. Load schedules/buses for current route if selected
      if (profData.preferredRouteId) {
        loadBusesForRoute(profData.preferredRouteId);
      } else {
        // Load all available buses
        const busesRes = await axios.get('/api/student/buses');
        setAvailableBuses(busesRes.data || []);
      }

      // 4. Fetch nearby stops if home coordinates exist
      if (profData.homeLatitude && profData.homeLongitude) {
        const nearbyRes = await axios.get('/api/student/stops/nearby');
        setNearbyStops(nearbyRes.data);
      }

      setError('');
    } catch (err) {
      console.error(err);
      setError('Failed to load profile specification details');
    } finally {
      setLoading(false);
    }
  };

  const loadBusesForRoute = async (routeId: string) => {
    try {
      const schedRes = await axios.get('/api/student/schedules');
      const schedList = schedRes.data || [];
      const filtered = schedList.filter((s: any) => s.routeId === routeId);
      const busesMap = new Map<string, BusSummary>();
      filtered.forEach((s: any) => {
        busesMap.set(s.busId, {
          busId: s.busId,
          busNumber: s.busNumber,
          busCode: s.busCode,
          routeId: s.routeId,
          routeName: s.routeName
        });
      });
      setAvailableBuses(Array.from(busesMap.values()));
    } catch (e) {
      console.warn('Failed to load buses for route', e);
    }
  };

  useEffect(() => {
    fetchProfileAndStops();
  }, []);

  // Map Click Listener to Update Pin Coordinates
  const MapEventsComponent = () => {
    useMapEvents({
      click(e) {
        setLat(e.latlng.lat);
        setLng(e.latlng.lng);
      },
    });
    return null;
  };

  const handleBrowserGeolocation = () => {
    if (!navigator.geolocation) {
      setError('Browser geolocation is not supported by your device.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(pos.coords.latitude);
        setLng(pos.coords.longitude);
        setAddress('Current Position coordinates');
      },
      () => {
        setError('Location permission denied. Please click on the map manually.');
      }
    );
  };

  const handleSaveLocation = async () => {
    if (lat === null || lng === null) {
      setError('Select a location coordinates pin on the map first.');
      return;
    }
    try {
      const res = await axios.post('/api/student/location/home', {
        latitude: lat,
        longitude: lng,
        address
      });
      if (res.status === 200 || res.data?.success || res.data?.id) {
        setSaveSuccess('Home location coordinates updated successfully.');
        setTimeout(() => setSaveSuccess(''), 4000);
        try {
          const nearbyRes = await axios.get('/api/student/stops/nearby');
          setNearbyStops(nearbyRes.data);
        } catch (ignored) {}
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to update home coordinates.');
    }
  };

  // Preference Consistency & Auto-Save
  const handleRouteChange = async (newRouteId: string) => {
    setSelectedRouteId(newRouteId);

    let nextBusId = selectedBusId;
    let nextStopId = selectedStopId;

    if (!newRouteId) {
      // Cleared route: clear bus and stop
      nextBusId = '';
      nextStopId = '';
      setSelectedBusId('');
      setSelectedStopId('');
      setAvailableBuses([]);
    } else {
      // Find route object
      const routeObj = routes.find(r => r.id === newRouteId);
      const validStops = routeObj?.stops || [];

      // Check if current stop belongs to new route
      if (nextStopId && !validStops.some(s => s.id === nextStopId)) {
        nextStopId = '';
        setSelectedStopId('');
      }

      // Reload valid buses for new route
      try {
        const schedRes = await axios.get('/api/student/schedules');
        const schedList = schedRes.data || [];
        const routeScheds = schedList.filter((s: any) => s.routeId === newRouteId);
        const busesMap = new Map<string, BusSummary>();
        routeScheds.forEach((s: any) => {
          busesMap.set(s.busId, {
            busId: s.busId,
            busNumber: s.busNumber,
            busCode: s.busCode,
            routeId: s.routeId,
            routeName: s.routeName
          });
        });
        const newBuses = Array.from(busesMap.values());
        setAvailableBuses(newBuses);

        // Check if current bus belongs to new route
        if (nextBusId && !newBuses.some(b => b.busId === nextBusId)) {
          nextBusId = '';
          setSelectedBusId('');
        }
      } catch (e) {
        console.warn('Could not filter buses for route', e);
      }
    }

    // Auto-save consistent state to backend
    await savePreferencesToBackend(newRouteId || null, nextBusId || null, nextStopId || null);
  };

  const handleBusChange = async (newBusId: string) => {
    setSelectedBusId(newBusId);
    await savePreferencesToBackend(selectedRouteId || null, newBusId || null, selectedStopId || null);
  };

  const handleStopChange = async (newStopId: string) => {
    setSelectedStopId(newStopId);
    await savePreferencesToBackend(selectedRouteId || null, selectedBusId || null, newStopId || null);
  };

  const savePreferencesToBackend = async (routeId: string | null, busId: string | null, stopId: string | null) => {
    try {
      const res = await axios.put('/api/student/preferences', {
        routeId,
        busId,
        stopId
      });
      const data = res.data;
      setProfile(prev => prev ? {
        ...prev,
        preferredRouteId: data.preferredRouteId,
        preferredRouteName: data.preferredRouteName,
        preferredBusId: data.preferredBusId,
        preferredBusNumber: data.preferredBusNumber,
        preferredStopId: data.preferredStopId,
        preferredStopName: data.preferredStopName
      } : null);

      setSaveSuccess('? Preferences updated');
      setTimeout(() => setSaveSuccess(''), 3000);
      setError('');
    } catch (e: any) {
      console.error('Failed to save preferences', e);
      setError(e.response?.data?.message || 'Failed to update preferences.');
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3 text-brandTextSecondary">
        <div className="w-8 h-8 border-4 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
        <p className="text-xs font-bold">Loading Profile Specs...</p>
      </div>
    );
  }

  const selectedRouteObj = routes.find(r => r.id === selectedRouteId);
  const routeStops = selectedRouteObj?.stops || [];

  return (
    <div className="flex flex-col gap-6 pb-6">
      {/* Title */}
      <div>
        <h1 className="text-xl font-bold tracking-tight text-brandNavy flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-brandBlue animate-pulse" /> My Profile & Preferences
        </h1>
        <p className="text-xs text-brandTextSecondary mt-1 font-medium">
          Personal identification and route transit preferences.
        </p>
      </div>

      {saveSuccess && (
        <div className="p-4 bg-brandGreen/10 border border-brandGreen/25 text-brandGreen text-xs rounded-2xl flex items-center gap-2 font-bold shadow-sm animate-fadeIn">
          <CheckCircle2 className="w-4 h-4 text-brandGreen" /> {saveSuccess}
        </div>
      )}

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold shadow-sm">
          {error}
        </div>
      )}

      {profile && (
        <>
          {/* Identity Card */}
          <div className="bg-white border border-brandBorder rounded-3xl p-5 flex flex-col gap-4 relative overflow-hidden shadow-sm text-brandTextPrimary">
            <div className="absolute right-0 top-0 w-32 h-32 bg-brandBlue/5 rounded-full blur-2xl"></div>
            
            <div className="flex items-center gap-3 border-b border-brandBorder pb-4">
              <div className="p-3 bg-brandBlue/5 border border-brandBlue/10 rounded-2xl text-brandBlue shrink-0">
                <UserIcon className="w-6 h-6" />
              </div>
              <div>
                <h3 className="font-extrabold text-brandNavy text-base">{profile.name}</h3>
                <p className="text-xs text-brandTextSecondary font-bold mt-0.5">{profile.collegeEmail}</p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 text-xs">
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                  Register Number
                </p>
                <p className="font-bold text-brandNavy">{profile.registerNumber}</p>
              </div>
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                  Department
                </p>
                <p className="font-bold text-brandNavy">{profile.department}</p>
              </div>
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                  Batch Year
                </p>
                <p className="font-bold text-brandNavy">{profile.batch}</p>
              </div>
              <div>
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                  Phone Number
                </p>
                <p className="font-bold text-brandNavy">{profile.phone || 'N/A'}</p>
              </div>
            </div>
          </div>

          {/* Transit Preferences Card (Route + Bus + Stop) */}
          <div className="bg-white border border-brandBorder rounded-3xl p-5 flex flex-col gap-4 shadow-sm">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-brandNavy flex items-center gap-2">
                <Compass className="w-4 h-4 text-brandBlue shrink-0" /> Transit Preferences (Auto-Saved)
              </h3>
              {profile.preferredBusNumber && (
                <span className="text-[10px] font-black uppercase text-brandBlue bg-brandBlue/10 border border-brandBlue/20 px-2.5 py-0.5 rounded-full flex items-center gap-1">
                  ?? {profile.preferredBusNumber}
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 gap-3.5">
              {/* Preferred Route */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider flex items-center justify-between">
                  <span>1. Preferred Route</span>
                  {profile.preferredRouteName && (
                    <span className="text-brandBlue lowercase font-normal">current: {profile.preferredRouteName}</span>
                  )}
                </label>
                <select
                  value={selectedRouteId}
                  onChange={(e) => handleRouteChange(e.target.value)}
                  className="w-full bg-white border border-brandBorder text-brandTextPrimary text-xs rounded-xl py-3 px-4 focus:border-brandBlue focus:ring-1 focus:ring-brandBlue outline-none transition-all font-bold"
                >
                  <option value="">-- Choose Preferred Route --</option>
                  {routes.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.routeName}
                    </option>
                  ))}
                </select>
              </div>

              {/* Preferred Bus (Serves Route) */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider flex items-center justify-between">
                  <span>2. Preferred Bus</span>
                  {selectedRouteId && (
                    <span className="text-slate-500 font-normal">Filtered to selected route</span>
                  )}
                </label>
                <select
                  value={selectedBusId}
                  onChange={(e) => handleBusChange(e.target.value)}
                  disabled={!selectedRouteId}
                  className="w-full bg-white border border-brandBorder text-brandTextPrimary text-xs rounded-xl py-3 px-4 focus:border-brandBlue focus:ring-1 focus:ring-brandBlue outline-none transition-all font-bold disabled:bg-slate-50 disabled:text-slate-400"
                >
                  <option value="">
                    {selectedRouteId ? '-- Choose Preferred Bus on Route --' : '-- Select Route First --'}
                  </option>
                  {availableBuses.map((b) => (
                    <option key={b.busId} value={b.busId}>
                      {b.busNumber} ({b.busCode})
                    </option>
                  ))}
                </select>
              </div>

              {/* Preferred Home Stop (Belongs to Route) */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider flex items-center justify-between">
                  <span>3. Home Transit Stop</span>
                  {profile.preferredStopName && (
                    <span className="text-amber-700 font-medium">? {profile.preferredStopName}</span>
                  )}
                </label>
                <select
                  value={selectedStopId}
                  onChange={(e) => handleStopChange(e.target.value)}
                  disabled={!selectedRouteId}
                  className="w-full bg-white border border-brandBorder text-brandTextPrimary text-xs rounded-xl py-3 px-4 focus:border-brandBlue focus:ring-1 focus:ring-brandBlue outline-none transition-all font-bold disabled:bg-slate-50 disabled:text-slate-400"
                >
                  <option value="">
                    {selectedRouteId ? '-- Choose Home Stop on Route --' : '-- Select Route First --'}
                  </option>
                  {routeStops.map((s, idx) => (
                    <option key={s.id} value={s.id}>
                      {idx + 1}. {s.stopName}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Display Nearby Stops list */}
            {nearbyStops.length > 0 && (
              <div className="border-t border-brandBorder pt-4 flex flex-col gap-2.5">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                  Nearby Stops by Proximity
                </p>
                <div className="flex flex-col gap-2">
                  {nearbyStops.slice(0, 3).map((ns) => (
                    <div
                      key={ns.stopId}
                      onClick={() => {
                        const matchingRoute = routes.find(r => r.stops.some(s => s.id === ns.stopId));
                        if (matchingRoute) {
                          handleRouteChange(matchingRoute.id);
                        }
                        handleStopChange(ns.stopId);
                      }}
                      className="bg-brandBg border border-brandBorder hover:border-brandBlue/30 hover:bg-brandBlue/5 p-2.5 rounded-xl flex items-center justify-between text-xs cursor-pointer transition-all shadow-sm"
                    >
                      <span className="font-bold text-brandNavy">{ns.stopName}</span>
                      <span className="text-[10px] font-extrabold text-brandBlue">
                        {ns.distanceMeters < 1000 
                          ? `${Math.round(ns.distanceMeters)}m` 
                          : `${(ns.distanceMeters / 1000).toFixed(1)} km`}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Home Geolocation Config Map Card */}
          <div className="bg-white border border-brandBorder rounded-3xl p-5 flex flex-col gap-4 shadow-sm text-brandTextPrimary">
            <div className="flex justify-between items-center">
              <h3 className="text-sm font-bold text-brandNavy flex items-center gap-2">
                <MapPin className="w-4 h-4 text-brandBlue shrink-0" /> Geolocation Home Coordinates
              </h3>
              <button
                onClick={handleBrowserGeolocation}
                className="text-[10px] bg-brandBg hover:bg-brandBg/80 text-brandBlue font-extrabold px-3 py-1.5 rounded-xl border border-brandBorder transition-colors flex items-center gap-1.5 shadow-sm"
              >
                <Navigation className="w-3.5 h-3.5" /> Locate Me
              </button>
            </div>

            <div className="bg-brandBg border border-brandBorder rounded-2xl p-2.5 flex flex-col gap-1.5">
              <label className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider">
                Home Address Description
              </label>
              <input
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="Enter address or landmark name..."
                className="w-full bg-white border border-brandBorder rounded-xl py-2 px-3 text-xs text-brandTextPrimary focus:border-brandBlue focus:ring-1 focus:ring-brandBlue outline-none transition-all font-semibold"
              />
            </div>

            {/* Leaflet Draggable Pin picker */}
            {lat !== null && lng !== null && (
              <div className="h-[25vh] rounded-2xl overflow-hidden border border-brandBorder relative z-0">
                <MapContainer center={[lat, lng]} zoom={13} style={{ height: '100%', width: '100%' }}>
                  <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
                  <Marker position={[lat, lng]} icon={getPinIcon()} />
                  <MapEventsComponent />
                </MapContainer>
              </div>
            )}

            <button
              onClick={handleSaveLocation}
              className="w-full h-11 bg-brandNavy hover:bg-brandNavy/90 text-white font-bold rounded-xl text-xs transition-colors flex items-center justify-center gap-2 shadow-sm"
            >
              <Check className="w-4 h-4" /> Save Geolocation Coordinates
            </button>
          </div>
        </>
      )}
    </div>
  );
};
