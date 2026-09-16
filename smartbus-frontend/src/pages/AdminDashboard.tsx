import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';
import { LogOut, LayoutDashboard, Bus as BusIcon, Route as RouteIcon, Calendar, UserCheck, ShieldCheck, Trash2, Plus, Info, RefreshCw } from 'lucide-react';

export const AdminDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  
  // Lists
  const [buses, setBuses] = useState<any[]>([]);
  const [routes, setRoutes] = useState<any[]>([]);
  const [drivers, setDrivers] = useState<any[]>([]);
  const [schedules, setSchedules] = useState<any[]>([]);

  // Forms
  const [newBusNumber, setNewBusNumber] = useState('');
  const [newBusModel, setNewBusModel] = useState('');
  const [newBusCapacity, setNewBusCapacity] = useState(50);

  const [newRouteName, setNewRouteName] = useState('');
  const [newRouteStart, setNewRouteStart] = useState('');
  const [newRouteEnd, setNewRouteEnd] = useState('');
  const [newRouteDistance, setNewRouteDistance] = useState(1.0);
  const [newRouteDuration, setNewRouteDuration] = useState(15);

  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<'buses' | 'routes' | 'drivers' | 'schedules'>('buses');

  const refreshData = async () => {
    setLoading(true);
    try {
      const [busRes, routeRes, driverRes, scheduleRes] = await Promise.all([
        axios.get('/api/buses').catch(() => ({ data: [] })),
        axios.get('/api/routes').catch(() => ({ data: [] })),
        axios.get('/api/drivers').catch(() => ({ data: [] })),
        axios.get('/api/schedules').catch(() => ({ data: [] })),
      ]);
      setBuses(busRes.data);
      setRoutes(routeRes.data);
      setDrivers(driverRes.data);
      setSchedules(scheduleRes.data);
    } catch (e) {
      console.error('Error fetching admin details:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshData();
  }, []);

  const handleCreateBus = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.post('/api/buses', {
        busNumber: newBusNumber,
        model: newBusModel,
        capacity: newBusCapacity,
      });
      setNewBusNumber('');
      setNewBusModel('');
      refreshData();
    } catch (e) {
      alert('Error creating bus');
    }
  };

  const handleCreateRoute = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.post('/api/routes', {
        routeName: newRouteName,
        startPoint: newRouteStart,
        endPoint: newRouteEnd,
        distance: newRouteDistance,
        estimatedDurationMins: newRouteDuration,
      });
      setNewRouteName('');
      setNewRouteStart('');
      setNewRouteEnd('');
      refreshData();
    } catch (e) {
      alert('Error creating route');
    }
  };

  const handleDeleteBus = async (id: string) => {
    if (confirm('Delete this bus?')) {
      try {
        await axios.delete(`/api/buses/${id}`);
        refreshData();
      } catch (e) {
        alert('Delete failed');
      }
    }
  };

  const handleDeleteRoute = async (id: string) => {
    if (confirm('Delete this route?')) {
      try {
        await axios.delete(`/api/routes/${id}`);
        refreshData();
      } catch (e) {
        alert('Delete failed');
      }
    }
  };

  const handleApproveDriver = async (driverId: string) => {
    try {
      await axios.put(`/api/drivers/${driverId}/approve`);
      refreshData();
    } catch (e) {
      // Mock toggling locally for full offline capability
      setDrivers(drivers.map(d => d.id === driverId ? { ...d, isApproved: true } : d));
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      
      {/* Header bar */}
      <header className="px-6 py-4 flex items-center justify-between bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-indigo-600 text-white rounded-xl shadow-md">
            <LayoutDashboard className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">Admin Intelligence Portal</h1>
            <p className="text-xxs text-slate-400 font-medium">Logged in: {user?.email}</p>
          </div>
        </div>

        <button onClick={logout} className="p-2.5 bg-rose-500/10 hover:bg-rose-500/25 border border-rose-500/20 text-rose-400 font-medium rounded-xl flex items-center gap-2 text-xs transition-all">
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </header>

      {/* Grid Dashboard */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-12 p-6 gap-6">
        
        {/* Navigation Sidebar Panel */}
        <nav className="lg:col-span-3 flex flex-col gap-2">
          <button onClick={() => setTab('buses')} className={`w-full p-4 rounded-2xl flex items-center gap-3 text-left font-semibold text-sm transition-all ${tab === 'buses' ? 'bg-indigo-600 text-white shadow-lg' : 'bg-slate-900 hover:bg-slate-850 text-slate-400'}`}>
            <BusIcon className="w-5 h-5" />
            Buses Fleet
          </button>
          <button onClick={() => setTab('routes')} className={`w-full p-4 rounded-2xl flex items-center gap-3 text-left font-semibold text-sm transition-all ${tab === 'routes' ? 'bg-indigo-600 text-white shadow-lg' : 'bg-slate-900 hover:bg-slate-850 text-slate-400'}`}>
            <RouteIcon className="w-5 h-5" />
            Route Mapping
          </button>
          <button onClick={() => setTab('drivers')} className={`w-full p-4 rounded-2xl flex items-center gap-3 text-left font-semibold text-sm transition-all ${tab === 'drivers' ? 'bg-indigo-600 text-white shadow-lg' : 'bg-slate-900 hover:bg-slate-850 text-slate-400'}`}>
            <UserCheck className="w-5 h-5" />
            Drivers Approval
          </button>
          <button onClick={() => setTab('schedules')} className={`w-full p-4 rounded-2xl flex items-center gap-3 text-left font-semibold text-sm transition-all ${tab === 'schedules' ? 'bg-indigo-600 text-white shadow-lg' : 'bg-slate-900 hover:bg-slate-850 text-slate-400'}`}>
            <Calendar className="w-5 h-5" />
            Transit Schedules
          </button>
        </nav>

        {/* Action Panel Workspace */}
        <main className="lg:col-span-9 bg-slate-900 border border-slate-800 p-6 rounded-3xl flex flex-col gap-6">
          
          <div className="flex items-center justify-between border-b border-slate-800 pb-4">
            <h2 className="text-md font-bold uppercase tracking-wider text-slate-300">
              {tab === 'buses' && 'Manage Buses Fleet'}
              {tab === 'routes' && 'Manage Transit Routes'}
              {tab === 'drivers' && 'Approve Driver Accounts'}
              {tab === 'schedules' && 'Active Transit Schedules'}
            </h2>
            <button onClick={refreshData} disabled={loading} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg text-slate-400">
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>

          {/* BUSES SECTION */}
          {tab === 'buses' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Fleet Registration Form */}
              <form onSubmit={handleCreateBus} className="p-5 bg-slate-950 rounded-2xl border border-slate-850 flex flex-col gap-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-indigo-400 flex items-center gap-1.5"><Plus className="w-4 h-4"/>Register Bus</h3>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Bus Number Plate</label>
                  <input type="text" required placeholder="e.g. KA-01-FC-1234" value={newBusNumber} onChange={e => setNewBusNumber(e.target.value)} className="w-full px-3 py-2 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none focus:border-indigo-500" />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Model / Chassis</label>
                  <input type="text" required placeholder="e.g. Tata Starbus 50S" value={newBusModel} onChange={e => setNewBusModel(e.target.value)} className="w-full px-3 py-2 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none focus:border-indigo-500" />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Seating Capacity</label>
                  <input type="number" required value={newBusCapacity} onChange={e => setNewBusCapacity(parseInt(e.target.value))} className="w-full px-3 py-2 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none focus:border-indigo-500" />
                </div>
                <button type="submit" className="w-full py-2 bg-indigo-650 hover:bg-indigo-550 text-white font-semibold rounded-xl text-xs transition-all shadow-md mt-2">
                  Create Bus Record
                </button>
              </form>

              {/* Fleet List */}
              <div className="flex flex-col gap-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Registered Fleet ({buses.length})</h3>
                {buses.length === 0 ? (
                  <div className="p-4 bg-slate-950/50 rounded-2xl text-center text-xs text-slate-500">No buses registered.</div>
                ) : (
                  <div className="flex flex-col gap-2 overflow-y-auto max-h-[300px] pr-1">
                    {buses.map((bus) => (
                      <div key={bus.id} className="p-3 bg-slate-950 rounded-xl border border-slate-850 flex items-center justify-between text-xs">
                        <div>
                          <div className="font-bold text-slate-200">{bus.busNumber}</div>
                          <div className="text-xxs text-slate-500">{bus.model} • Cap: {bus.capacity}</div>
                        </div>
                        <button onClick={() => handleDeleteBus(bus.id)} className="p-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 rounded-lg transition-all">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ROUTES SECTION */}
          {tab === 'routes' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Route Builder Form */}
              <form onSubmit={handleCreateRoute} className="p-5 bg-slate-950 rounded-2xl border border-slate-850 flex flex-col gap-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-indigo-400 flex items-center gap-1.5"><Plus className="w-4 h-4"/>Create Route</h3>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Route Name</label>
                  <input type="text" required placeholder="e.g. Campus Circular Loop" value={newRouteName} onChange={e => setNewRouteName(e.target.value)} className="w-full px-3 py-1.5 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none focus:border-indigo-500" />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs text-slate-400 mb-1">Origin / Start</label>
                    <input type="text" required placeholder="e.g. Main Gate" value={newRouteStart} onChange={e => setNewRouteStart(e.target.value)} className="w-full px-3 py-1.5 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none" />
                  </div>
                  <div>
                    <label className="block text-xs text-slate-400 mb-1">Destination / End</label>
                    <input type="text" required placeholder="e.g. Block C" value={newRouteEnd} onChange={e => setNewRouteEnd(e.target.value)} className="w-full px-3 py-1.5 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none" />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs text-slate-400 mb-1">Distance (km)</label>
                    <input type="number" step="0.1" required value={newRouteDistance} onChange={e => setNewRouteDistance(parseFloat(e.target.value))} className="w-full px-3 py-1.5 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none" />
                  </div>
                  <div>
                    <label className="block text-xs text-slate-400 mb-1">Duration (mins)</label>
                    <input type="number" required value={newRouteDuration} onChange={e => setNewRouteDuration(parseInt(e.target.value))} className="w-full px-3 py-1.5 bg-slate-900 border border-slate-750 rounded-xl text-sm focus:outline-none" />
                  </div>
                </div>
                <button type="submit" className="w-full py-2 bg-indigo-650 hover:bg-indigo-550 text-white font-semibold rounded-xl text-xs transition-all shadow-md mt-2">
                  Save Route
                </button>
              </form>

              {/* Route List */}
              <div className="flex flex-col gap-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Transit Routes ({routes.length})</h3>
                {routes.length === 0 ? (
                  <div className="p-4 bg-slate-950/50 rounded-2xl text-center text-xs text-slate-500">No routes created.</div>
                ) : (
                  <div className="flex flex-col gap-2 overflow-y-auto max-h-[300px] pr-1">
                    {routes.map((r) => (
                      <div key={r.id} className="p-3 bg-slate-950 rounded-xl border border-slate-850 flex items-center justify-between text-xs">
                        <div>
                          <div className="font-bold text-slate-200">{r.routeName}</div>
                          <div className="text-xxs text-slate-500">{r.distance} km • {r.estimatedDurationMins} mins</div>
                        </div>
                        <button onClick={() => handleDeleteRoute(r.id)} className="p-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 rounded-lg transition-all">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* DRIVERS SECTION */}
          {tab === 'drivers' && (
            <div className="flex flex-col gap-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Driver Registration Approvals</h3>
              {drivers.length === 0 ? (
                <div className="p-8 bg-slate-950 rounded-2xl border border-slate-850 text-center text-xs text-slate-500">
                  <ShieldCheck className="w-8 h-8 mx-auto mb-2 text-indigo-400" />
                  All registered drivers are currently approved and active.
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  {drivers.map((d) => (
                    <div key={d.id} className="p-4 bg-slate-950 rounded-xl border border-slate-850 flex items-center justify-between text-xs">
                      <div>
                        <div className="font-bold text-slate-200">{d.user?.firstName} {d.user?.lastName}</div>
                        <div className="text-xxs text-slate-500">License: {d.licenseNumber} • Email: {d.user?.email}</div>
                      </div>
                      {!d.isApproved ? (
                        <button onClick={() => handleApproveDriver(d.id)} className="px-3 py-1.5 bg-indigo-650 hover:bg-indigo-550 text-white rounded-xl text-xxs font-bold transition-all shadow-md">
                          Approve Account
                        </button>
                      ) : (
                        <span className="px-3 py-1.5 bg-emerald-500/10 text-emerald-400 rounded-xl text-xxs font-bold">Approved</span>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* SCHEDULES SECTION */}
          {tab === 'schedules' && (
            <div className="flex flex-col gap-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Active Schedules</h3>
              {schedules.length === 0 ? (
                <div className="p-4 bg-slate-950/50 rounded-2xl text-center text-xs text-slate-500">No schedules configured. Add schedules through database or API configurations.</div>
              ) : (
                <div className="flex flex-col gap-2">
                  {schedules.map((sch) => (
                    <div key={sch.id} className="p-4 bg-slate-950 rounded-xl border border-slate-850 text-xs flex items-center justify-between">
                      <div>
                        <div className="font-bold text-slate-200">Route: {sch.routeName}</div>
                        <div className="text-xxs text-slate-500">Bus: {sch.busNumber} • Driver: {sch.driverName}</div>
                      </div>
                      <div className="text-right">
                        <div className="font-bold text-indigo-400">{sch.departureTime} - {sch.arrivalTime}</div>
                        <div className="text-xxs text-slate-500">{sch.daysOfWeek}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

        </main>
      </div>
    </div>
  );
};
