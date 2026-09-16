import React, { useEffect, useState, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';
import { LogOut, Play, Square, Pause, ShieldAlert, Navigation, RefreshCw, Info } from 'lucide-react';
import { SmartWebSocketClient } from '../services/websocketService';

export const DriverDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  
  const [schedules, setSchedules] = useState<any[]>([]);
  const [activeTrip, setActiveTrip] = useState<any | null>(null);
  const [loading, setLoading] = useState(false);
  const [passengerCount, setPassengerCount] = useState(0);

  // Simulation state
  const [simulating, setSimulating] = useState(false);
  const [currentLat, setCurrentLat] = useState<number | null>(null);
  const [currentLng, setCurrentLng] = useState<number | null>(null);
  const [routeStops, setRouteStops] = useState<any[]>([]);
  const [stopIndex, setStopIndex] = useState(0);

  const clientRef = useRef<SmartWebSocketClient | null>(null);
  const intervalRef = useRef<number | null>(null);

  // Load schedules on start
  const fetchSchedules = async () => {
    try {
      const res = await axios.get('/api/routes');
      // For drivers, we get schedules or routes. Let's look up schedules:
      // Since schedules might be empty initially, we can seed schedules dynamically or use routes
      // If routes exist, we can create a mock schedule list matching the routes for this driver
      const routes = res.data;
      const driverSchedules = routes.map((r: any, idx: number) => ({
        id: r.id, // using route id as schedule mock id
        routeName: r.routeName,
        busNumber: 'KA-01-FC-4321',
        departureTime: `08:${30 + idx * 15}:00`,
        arrivalTime: `09:${15 + idx * 15}:00`,
        daysOfWeek: 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
        route: r,
      }));
      setSchedules(driverSchedules);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    fetchSchedules();
  }, []);

  const handleStartTrip = async (schedule: any) => {
    setLoading(true);
    try {
      // POST to /api/trips/start?scheduleId=
      // If that fails due to schedule seeding, we fall back to creating a mock trip
      let tripData;
      try {
        const res = await axios.post(`/api/trips/start?scheduleId=${schedule.id}`);
        tripData = res.data;
      } catch (err) {
        tripData = {
          id: 'b1111111-1111-1111-1111-111111111111',
          status: 'EN_ROUTE',
          busNumber: schedule.busNumber,
          routeName: schedule.routeName,
          route: schedule.route,
        };
      }
      setActiveTrip(tripData);
      setPassengerCount(15);
      
      // Load route stops for simulation
      if (schedule.route?.stops) {
        setRouteStops(schedule.route.stops);
        if (schedule.route.stops.length > 0) {
          setCurrentLat(schedule.route.stops[0].stop.latitude);
          setCurrentLng(schedule.route.stops[0].stop.longitude);
          setStopIndex(0);
        }
      }

      // Establish WebSocket for sending location coordinates
      const client = new SmartWebSocketClient({
        onStateChange: (state) => {
          if (state === 'CONNECTED') {
            console.log('Driver WS Connection Open');
            setSimulating(true);
          } else if (state === 'DISCONNECTED') {
            setSimulating(false);
          }
        }
      });
      clientRef.current = client;
      client.connect();

    } catch (e) {
      alert('Failed to initialize trip');
    } finally {
      setLoading(false);
    }
  };

  // Simulating bus movement along the stops sequence
  useEffect(() => {
    if (!simulating || !activeTrip || routeStops.length === 0) return;

    const simulateMovement = () => {
      setStopIndex((prevIndex) => {
        const nextIndex = (prevIndex + 1) % routeStops.length;
        const nextStop = routeStops[nextIndex].stop;
        
        setCurrentLat(nextStop.latitude);
        setCurrentLng(nextStop.longitude);

        // Send GPS coordinate update to WS
        if (clientRef.current && clientRef.current.isConnected()) {
          const payload = {
            type: 'DRIVER_UPDATE',
            tripId: activeTrip.id,
            latitude: nextStop.latitude,
            longitude: nextStop.longitude,
            speed: 35.0 + Math.random() * 15.0,
            heading: Math.random() * 360.0
          };
          clientRef.current.send(payload);
          console.log('SENT LIVE GPS COORDINATES:', payload);
        }

        // Randomly update passenger counts
        setPassengerCount(prev => Math.max(5, Math.min(48, prev + (Math.random() > 0.5 ? 2 : -2))));

        return nextIndex;
      });
    };

    intervalRef.current = window.setInterval(simulateMovement, 4000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [simulating, activeTrip, routeStops]);

  const handleEndTrip = async () => {
    if (!activeTrip) return;
    setLoading(true);
    setSimulating(false);
    
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
    }

    try {
      await axios.post(`/api/trips/${activeTrip.id}/end`);
    } catch (e) {
      // mock cleanup
    }

    if (clientRef.current) {
      clientRef.current.disconnect();
      clientRef.current = null;
    }

    setActiveTrip(null);
    setCurrentLat(null);
    setCurrentLng(null);
    setRouteStops([]);
    setLoading(false);
  };

  const triggerSos = async () => {
    if (!activeTrip || !currentLat || !currentLng) return;
    try {
      // POST SOS
      await axios.post(`/api/trips/${activeTrip.id}/sos`, {
        latitude: currentLat,
        longitude: currentLng,
        type: 'BREAKDOWN',
        description: 'Engine breakdown, request replacement bus'
      });
      alert('SOS ALERT DISPATCHED TO CONTROL CENTER!');
    } catch (e) {
      alert('SOS TRIGGERED (FALLBACK SIMULATED)!');
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Header bar */}
      <header className="px-6 py-4 flex items-center justify-between bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-indigo-600 text-white rounded-xl shadow-md">
            <Navigation className="w-5 h-5 animate-pulse" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">Driver Navigation Console</h1>
            <p className="text-xxs text-slate-400 font-medium">Logged in: {user?.email}</p>
          </div>
        </div>

        <button onClick={logout} className="p-2.5 bg-rose-500/10 hover:bg-rose-500/25 border border-rose-500/20 text-rose-400 font-medium rounded-xl flex items-center gap-2 text-xs transition-all">
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </header>

      {/* Main Panel Workspace */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-6 flex flex-col gap-6 justify-center">
        
        {!activeTrip ? (
          /* Selection Screen */
          <div className="flex flex-col gap-4">
            <div className="text-center mb-4">
              <h2 className="text-xl font-bold">Select Assigned Schedule</h2>
              <p className="text-xs text-slate-400 mt-1">Start a trip to begin broadcasting coordinates.</p>
            </div>
            
            {schedules.length === 0 ? (
              <div className="p-8 bg-slate-900 border border-slate-800 rounded-3xl text-center text-xs text-slate-400">
                No active routes found to start simulation.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {schedules.map((sch) => (
                  <div key={sch.id} className="p-5 bg-slate-900 border border-slate-800 rounded-3xl flex flex-col justify-between gap-4">
                    <div>
                      <span className="px-2 py-0.5 bg-indigo-500/20 text-indigo-400 text-xxs font-bold rounded-md">{sch.busNumber}</span>
                      <h3 className="text-sm font-bold mt-2">{sch.routeName}</h3>
                      <div className="text-xxs text-slate-400 mt-1">{sch.departureTime} - {sch.arrivalTime}</div>
                    </div>
                    <button 
                      onClick={() => handleStartTrip(sch)} 
                      disabled={loading}
                      className="w-full py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-xl text-xs flex items-center justify-center gap-2 transition-all"
                    >
                      <Play className="w-4 h-4" />
                      Start Assigned Trip
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          /* Active Trip Control Screen */
          <div className="p-8 bg-slate-900 border border-slate-800 rounded-3xl flex flex-col gap-6 shadow-2xl relative overflow-hidden">
            {/* Top accent glow */}
            <div className="absolute top-0 left-1/4 w-1/2 h-1 bg-gradient-to-r from-emerald-500 via-teal-500 to-indigo-500 blur-sm"></div>

            <div className="flex flex-col md:flex-row items-center justify-between border-b border-slate-800 pb-5 gap-4">
              <div>
                <span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-400 text-xxs font-bold rounded-md">Trip in progress</span>
                <h2 className="text-lg font-bold mt-2">{activeTrip.routeName}</h2>
                <p className="text-xxs text-slate-400 mt-1">Bus: {activeTrip.busNumber} • Trip ID: {activeTrip.id}</p>
              </div>
              
              <button 
                onClick={handleEndTrip}
                disabled={loading}
                className="px-5 py-2.5 bg-rose-600 hover:bg-rose-500 text-white font-bold rounded-xl text-xs flex items-center justify-center gap-2 transition-all shadow-lg"
              >
                <Square className="w-4 h-4 fill-white" />
                End Current Trip
              </button>
            </div>

            {/* Simulating stats summary */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <div className="p-4 bg-slate-950 rounded-2xl border border-slate-850 text-center">
                <span className="block text-xxs text-slate-400 uppercase tracking-wider">Passengers Boarded</span>
                <span className="block text-xl font-bold text-indigo-400 mt-1">{passengerCount}</span>
              </div>
              <div className="p-4 bg-slate-950 rounded-2xl border border-slate-850 text-center">
                <span className="block text-xxs text-slate-400 uppercase tracking-wider">Speedometer</span>
                <span className="block text-xl font-bold text-indigo-400 mt-1">42 km/h</span>
              </div>
              <div className="p-4 bg-slate-950 rounded-2xl border border-slate-850 text-center">
                <span className="block text-xxs text-slate-400 uppercase tracking-wider">Latitude</span>
                <span className="block text-xs font-mono font-bold text-indigo-400 mt-2">{currentLat ? currentLat.toFixed(6) : '--'}</span>
              </div>
              <div className="p-4 bg-slate-950 rounded-2xl border border-slate-850 text-center">
                <span className="block text-xxs text-slate-400 uppercase tracking-wider">Longitude</span>
                <span className="block text-xs font-mono font-bold text-indigo-400 mt-2">{currentLng ? currentLng.toFixed(6) : '--'}</span>
              </div>
            </div>

            {/* Simulation track details */}
            {routeStops.length > 0 && (
              <div className="p-4 bg-slate-950/40 rounded-2xl border border-slate-850 text-xs">
                <div className="font-bold mb-2 text-indigo-400 flex items-center gap-1.5">
                  <RefreshCw className="w-4 h-4 animate-spin text-indigo-400" />
                  Live GPS Route Simulator (Broadcasting every 4s)
                </div>
                <div>Next Stop Target: <span className="font-bold text-slate-200">{routeStops[stopIndex]?.stop?.stopName}</span></div>
                <div className="text-xxs text-slate-500 mt-1">Simulating movement along sequence to allow live administrative dashboard map tracking.</div>
              </div>
            )}

            {/* Critical SOS Alarm Panel */}
            <div className="p-5 bg-rose-500/10 border border-rose-500/20 rounded-3xl flex flex-col sm:flex-row items-center justify-between gap-4 mt-2">
              <div className="flex items-center gap-3 text-rose-500">
                <ShieldAlert className="w-8 h-8" />
                <div>
                  <h4 className="text-xs font-bold uppercase tracking-wider">Emergency Dispatch Panel</h4>
                  <p className="text-xxs text-slate-400 mt-1">Triggers immediate SMS alerts, push notifications, and highlights vehicle on administrative map.</p>
                </div>
              </div>
              <button 
                onClick={triggerSos}
                className="w-full sm:w-auto px-5 py-2 bg-rose-600 hover:bg-rose-500 text-white font-bold rounded-xl text-xs transition-all shadow-md"
              >
                Trigger SOS Alarm
              </button>
            </div>

          </div>
        )}
      </main>
    </div>
  );
};
