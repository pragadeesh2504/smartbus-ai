import axios from 'axios';

declare const google: any;

export interface LocationSearchResult {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  providerId?: string;
}

export interface RouteLeg {
  distanceKm: number;
  durationMins: number;
  startAddress?: string;
  endAddress?: string;
}

export interface RouteResult {
  polyline: [number, number][]; // Array of [lat, lng]
  distanceKm: number;
  durationMins: number;
  legs: RouteLeg[];
}

// Google Maps API Key configuration
const GOOGLE_API_KEY = (import.meta as any).env.VITE_GOOGLE_MAPS_API_KEY || '';
const isGoogleConfigured = !!GOOGLE_API_KEY && GOOGLE_API_KEY !== 'placeholder_key' && GOOGLE_API_KEY.trim() !== '';

let googleScriptLoaded = false;
let googleScriptPromise: Promise<void> | null = null;
let googleAuthFailed = false;

// Register global Google auth failure listener
if (typeof window !== 'undefined') {
  (window as any).gm_authFailure = () => {
    console.warn('Google Maps API authentication failed (invalid key or billing not enabled).');
    googleAuthFailed = true;
  };
}

// Dynamically load Google Maps script
function loadGoogleMapsScript(): Promise<void> {
  if (googleAuthFailed) {
    return Promise.reject(new Error('Google Maps authentication failed'));
  }
  if (googleScriptLoaded) return Promise.resolve();
  if (googleScriptPromise) return googleScriptPromise;

  if (!isGoogleConfigured) {
    return Promise.reject(new Error('Google Maps API key not configured'));
  }

  googleScriptPromise = new Promise((resolve, reject) => {
    if ((window as any).google && (window as any).google.maps) {
      googleScriptLoaded = true;
      resolve();
      return;
    }

    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${GOOGLE_API_KEY}&libraries=places,geometry`;
    script.async = true;
    script.defer = true;
    script.onload = () => {
      googleScriptLoaded = true;
      resolve();
    };
    script.onerror = (e) => {
      googleScriptPromise = null;
      console.warn('Failed to load Google Maps script tag', e);
      reject(new Error('Failed to load Google Maps script'));
    };
    document.head.appendChild(script);
  });

  return googleScriptPromise;
}

// Photon (Komoot) Geocoding Search (Robust, no rate-limiting, handles partial strings like "Chennai Insti")
async function searchPhoton(query: string): Promise<LocationSearchResult[]> {
  try {
    const res = await axios.get('https://photon.komoot.io/api/', {
      params: {
        q: query,
        limit: 8
      },
      timeout: 5000
    });

    if (res.data?.features && res.data.features.length > 0) {
      return res.data.features.map((f: any) => {
        const p = f.properties || {};
        const coords = f.geometry?.coordinates || [0, 0];
        const name = p.name || p.street || query;
        const addressParts = [
          p.name,
          p.street ? (p.housenumber ? `${p.housenumber} ${p.street}` : p.street) : null,
          p.district || p.suburb,
          p.city || p.town || p.county,
          p.state,
          p.postcode,
          p.country
        ].filter(Boolean);

        // Remove duplicate first element if same as name
        const displayAddress = addressParts.join(', ');

        return {
          name,
          address: displayAddress || name,
          latitude: coords[1],
          longitude: coords[0],
          providerId: p.osm_id?.toString()
        };
      });
    }
  } catch (err) {
    console.warn('Photon search error, will try Nominatim fallback', err);
  }
  return [];
}

// Fallback search using OpenStreetMap Nominatim API
async function searchNominatim(query: string): Promise<LocationSearchResult[]> {
  try {
    const res = await axios.get('https://nominatim.openstreetmap.org/search', {
      params: {
        format: 'json',
        q: query,
        limit: 8,
        addressdetails: 1,
        countrycodes: 'in'
      },
      headers: {
        'Accept-Language': 'en',
        'User-Agent': 'SmartBus-AI-Transit/1.0'
      },
      timeout: 5000
    });

    return res.data.map((item: any) => {
      const displayName = item.display_name;
      const parts = displayName.split(',');
      const name = parts[0]?.trim() || query;
      const address = parts.slice(1).join(',').trim() || displayName;

      return {
        name,
        address: address || displayName,
        latitude: parseFloat(item.lat),
        longitude: parseFloat(item.lon),
        providerId: item.place_id?.toString()
      };
    });
  } catch (error) {
    console.warn('Nominatim Search failed', error);
    return [];
  }
}

// Fallback reverse geocoding
async function reverseGeocodeOSM(lat: number, lng: number): Promise<string> {
  try {
    const res = await axios.get('https://nominatim.openstreetmap.org/reverse', {
      params: {
        format: 'json',
        lat,
        lon: lng,
        addressdetails: 1
      },
      headers: {
        'Accept-Language': 'en',
        'User-Agent': 'SmartBus-AI-Transit/1.0'
      },
      timeout: 5000
    });

    return res.data?.display_name || `Stop at ${lat.toFixed(5)}, ${lng.toFixed(5)}`;
  } catch (error) {
    console.warn('OSM Reverse Geocoding failed', error);
    return `Stop at ${lat.toFixed(5)}, ${lng.toFixed(5)}`;
  }
}

// Fallback routing using OSRM API with full leg details
async function calculateRouteOSRM(
  from: { lat: number; lng: number },
  to: { lat: number; lng: number },
  stops: { lat: number; lng: number }[]
): Promise<RouteResult> {
  try {
    const coords = [
      `${from.lng},${from.lat}`,
      ...stops.map((s) => `${s.lng},${s.lat}`),
      `${to.lng},${to.lat}`
    ].join(';');

    const url = `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`;
    const res = await axios.get(url, { timeout: 8000 });

    if (res.data?.routes && res.data.routes.length > 0) {
      const route = res.data.routes[0];
      const polyline: [number, number][] = route.geometry.coordinates.map(
        (c: [number, number]) => [c[1], c[0]] as [number, number]
      );
      
      const distanceKm = parseFloat((route.distance / 1000).toFixed(2));
      const durationMins = Math.round(route.duration / 60);

      const legs: RouteLeg[] = (route.legs || []).map((leg: any) => ({
        distanceKm: parseFloat(((leg.distance || 0) / 1000).toFixed(2)),
        durationMins: Math.max(1, Math.round((leg.duration || 0) / 60))
      }));

      return {
        polyline,
        distanceKm,
        durationMins,
        legs
      };
    }
  } catch (error) {
    console.error('OSRM Routing failed', error);
  }

  // Emergency straight-line fallback if offline
  const allPoints = [from, ...stops, to];
  const polyline: [number, number][] = allPoints.map(p => [p.lat, p.lng]);
  const legs: RouteLeg[] = [];
  let totalDist = 0;
  let totalDur = 0;

  for (let i = 0; i < allPoints.length - 1; i++) {
    const p1 = allPoints[i];
    const p2 = allPoints[i + 1];
    // Haversine calculation
    const R = 6371;
    const dLat = ((p2.lat - p1.lat) * Math.PI) / 180;
    const dLng = ((p2.lng - p1.lng) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((p1.lat * Math.PI) / 180) *
        Math.cos((p2.lat * Math.PI) / 180) *
        Math.sin(dLng / 2) *
        Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const legDist = parseFloat((R * c).toFixed(2));
    const legDur = Math.max(2, Math.round(legDist * 2));
    totalDist += legDist;
    totalDur += legDur;
    legs.push({ distanceKm: legDist, durationMins: legDur });
  }

  return {
    polyline,
    distanceKm: parseFloat(totalDist.toFixed(2)),
    durationMins: totalDur,
    legs
  };
}

export const MapService = {
  isGoogleMapsEnabled(): boolean {
    return isGoogleConfigured && !googleAuthFailed;
  },

  loadGoogleMapsScript,

  /**
   * Search location suggestions using Google Places Autocomplete when enabled,
   * with automatic fallback to Photon and Nominatim.
   */
  async search(query: string): Promise<LocationSearchResult[]> {
    if (!query || query.trim() === '') return [];

    if (this.isGoogleMapsEnabled()) {
      try {
        await loadGoogleMapsScript();
        const service = new google.maps.places.AutocompleteService();

        const predictions = await new Promise<any[]>((resolve, reject) => {
          service.getPlacePredictions(
            { input: query, componentRestrictions: { country: 'in' } },
            (res: any, status: any) => {
              if (status === google.maps.places.PlacesServiceStatus.OK && res) {
                resolve(res);
              } else if (status === google.maps.places.PlacesServiceStatus.ZERO_RESULTS) {
                resolve([]);
              } else {
                reject(status);
              }
            }
          );
        });

        if (predictions && predictions.length > 0) {
          // Return predictions immediately without slow/rate-limited geocoding loop
          return predictions.map(p => ({
            name: p.structured_formatting?.main_text || p.description.split(',')[0],
            address: p.description,
            latitude: 0,
            longitude: 0,
            providerId: p.place_id
          }));
        }
      } catch (error) {
        console.warn('Google Places Autocomplete failed, falling back to open geocoding engine', error);
      }
    }

    // Fallback: Photon then Nominatim
    const photonResults = await searchPhoton(query);
    if (photonResults.length > 0) return photonResults;

    return searchNominatim(query);
  },

  /**
   * Resolve coordinates for a selected location prediction.
   * If coordinates are already present, returns the location immediately.
   * If placeId (Google Place ID) is present, geocodes it via Geocoder.
   */
  async resolveLocation(loc: LocationSearchResult): Promise<LocationSearchResult> {
    if (loc.latitude !== 0 && loc.longitude !== 0) {
      return loc;
    }

    if (loc.providerId && this.isGoogleMapsEnabled()) {
      try {
        await loadGoogleMapsScript();
        const geocoder = new google.maps.Geocoder();

        const geoRes = await new Promise<any[]>((resolve, reject) => {
          geocoder.geocode({ placeId: loc.providerId }, (res: any, status: any) => {
            if (status === google.maps.GeocoderStatus.OK && res && res.length > 0) {
              resolve(res);
            } else {
              reject(status);
            }
          });
        });

        if (geoRes && geoRes.length > 0) {
          const first = geoRes[0];
          return {
            name: loc.name || first.formatted_address.split(',')[0],
            address: first.formatted_address || loc.address,
            latitude: first.geometry.location.lat(),
            longitude: first.geometry.location.lng(),
            providerId: loc.providerId
          };
        }
      } catch (err) {
        console.warn('Geocoding Google Place ID failed, resolving via text search', err);
      }
    }

    // Fallback resolution via text search
    const textSearch = await searchPhoton(loc.name || loc.address);
    if (textSearch.length > 0 && textSearch[0].latitude && textSearch[0].longitude) {
      return {
        ...loc,
        latitude: textSearch[0].latitude,
        longitude: textSearch[0].longitude
      };
    }

    return loc;
  },

  /**
   * Reverse geocode a latitude/longitude coordinate pair to an address name.
   */
  async reverseGeocode(lat: number, lng: number): Promise<string> {
    if (this.isGoogleMapsEnabled()) {
      try {
        await loadGoogleMapsScript();
        const geocoder = new google.maps.Geocoder();
        const response = await new Promise<any[]>((resolve, reject) => {
          geocoder.geocode({ location: { lat, lng } }, (res: any, status: any) => {
            if (status === google.maps.GeocoderStatus.OK && res && res.length > 0) {
              resolve(res);
            } else {
              reject(status);
            }
          });
        });

        if (response.length > 0) {
          return response[0].formatted_address;
        }
      } catch (error) {
        console.warn('Google reverse geocode failed, falling back to OSM', error);
      }
    }

    return reverseGeocodeOSM(lat, lng);
  },

  /**
   * Calculate driving road route with waypoints and individual route legs.
   */
  async calculateRoute(
    from: { lat: number; lng: number },
    to: { lat: number; lng: number },
    stops: { lat: number; lng: number }[]
  ): Promise<RouteResult> {
    if (this.isGoogleMapsEnabled()) {
      try {
        await loadGoogleMapsScript();
        const directionsService = new google.maps.DirectionsService();

        const waypoints = stops.map((s) => ({
          location: new google.maps.LatLng(s.lat, s.lng),
          stopover: true
        }));

        const request: any = {
          origin: new google.maps.LatLng(from.lat, from.lng),
          destination: new google.maps.LatLng(to.lat, to.lng),
          waypoints,
          optimizeWaypoints: false, // Strict order entered by Admin
          travelMode: google.maps.TravelMode.DRIVING
        };

        const response = await new Promise<any>((resolve, reject) => {
          directionsService.route(request, (res: any, status: any) => {
            if (status === google.maps.DirectionsStatus.OK && res) {
              resolve(res);
            } else {
              reject(new Error(`Directions service error: ${status}`));
            }
          });
        });

        if (response.routes && response.routes.length > 0) {
          const route = response.routes[0];
          const rawLegs = route.legs || [];
          
          let totalMeters = 0;
          let totalSeconds = 0;

          const legs: RouteLeg[] = rawLegs.map((leg: any) => {
            const legDistMeters = leg.distance?.value || 0;
            const legDurSeconds = leg.duration?.value || 0;
            totalMeters += legDistMeters;
            totalSeconds += legDurSeconds;

            return {
              distanceKm: parseFloat((legDistMeters / 1000).toFixed(2)),
              durationMins: Math.max(1, Math.round(legDurSeconds / 60)),
              startAddress: leg.start_address,
              endAddress: leg.end_address
            };
          });

          // Overview polyline coordinates
          const polylinePoints: [number, number][] = (route.overview_path || []).map(
            (p: any) => [p.lat(), p.lng()] as [number, number]
          );

          return {
            polyline: polylinePoints,
            distanceKm: parseFloat((totalMeters / 1000).toFixed(2)),
            durationMins: Math.max(1, Math.round(totalSeconds / 60)),
            legs
          };
        }
      } catch (error) {
        console.warn('Google Directions API failed, falling back to OSRM road routing', error);
      }
    }

    return calculateRouteOSRM(from, to, stops);
  },

  /**
   * Calculate emergency navigation recovery route from current GPS to next required stop.
   */
  async calculateRecoveryRoute(
    currentGps: { lat: number; lng: number },
    nextStopCoord: { lat: number; lng: number }
  ): Promise<[number, number][]> {
    try {
      const res = await this.calculateRoute(currentGps, nextStopCoord, []);
      return res.polyline || [];
    } catch (e) {
      return [
        [currentGps.lat, currentGps.lng],
        [nextStopCoord.lat, nextStopCoord.lng]
      ];
    }
  }
};
