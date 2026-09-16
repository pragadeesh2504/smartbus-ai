import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  CalendarDays,
  Plus,
  ChevronLeft,
  ChevronRight,
  User,
  Bus,
  Clock,
  Ban,
  Edit2,
  Trash2
} from 'lucide-react';

interface ScheduleType {
  id: string;
  routeId: string;
  routeName: string;
  busId: string;
  busNumber: string;
  driverId: string;
  driverName: string;
  departureTime: string;
  arrivalTime: string;
  daysOfWeek: string;
  startDate: string | null;
  endDate: string | null;
  status: string;
}

interface BusType {
  id: string;
  busNumber: string;
  status: string;
}

interface DriverType {
  id: string;
  name: string;
  status: string;
  approvalStatus: string;
}

interface RouteType {
  id: string;
  routeName: string;
  status: string;
}

export const Schedules: React.FC = () => {
  const [schedules, setSchedules] = useState<ScheduleType[]>([]);
  const [buses, setBuses] = useState<BusType[]>([]);
  const [drivers, setDrivers] = useState<DriverType[]>([]);
  const [routes, setRoutes] = useState<RouteType[]>([]);
  
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [loading, setLoading] = useState(true);

  // Form states
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingScheduleId, setEditingScheduleId] = useState<string | null>(null);
  const [routeId, setRouteId] = useState('');
  const [busId, setBusId] = useState('');
  const [driverId, setDriverId] = useState('');
  const [departureTime, setDepartureTime] = useState('08:30');
  const [arrivalTime, setArrivalTime] = useState('09:00');
  const [daysOfWeek, setDaysOfWeek] = useState<string[]>(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchSchedules = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/admin/schedules', {
        params: { page, size }
      });
      if (res.data.success) {
        setSchedules(res.data.data.content);
        setTotalElements(res.data.data.totalElements);
        setTotalPages(res.data.data.totalPages);
      }
    } catch (e) {
      setError('Failed to fetch schedules.');
    } finally {
      setLoading(false);
    }
  };

  const fetchFormOptions = async () => {
    try {
      const busesRes = await axios.get('/api/admin/buses', { params: { size: 100 } });
      const driversRes = await axios.get('/api/admin/drivers', { params: { size: 100 } });
      const routesRes = await axios.get('/api/admin/routes', { params: { size: 100 } });
      
      if (busesRes.data.success) setBuses(busesRes.data.data.content);
      if (driversRes.data.success) setDrivers(driversRes.data.data.content);
      if (routesRes.data.success) setRoutes(routesRes.data.data.content);
    } catch (e) {}
  };

  useEffect(() => {
    fetchSchedules();
  }, [page]);

  useEffect(() => {
    fetchFormOptions();
  }, []);

  const openCreateModal = () => {
    setError('');
    setSuccess('');
    setEditingScheduleId(null);
    setRouteId('');
    setBusId('');
    setDriverId('');
    setDepartureTime('08:30');
    setArrivalTime('09:00');
    setDaysOfWeek(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
    setStartDate('');
    setEndDate('');
    setCreateModalOpen(true);
  };

  const openEditModal = (s: ScheduleType) => {
    setError('');
    setSuccess('');
    setEditingScheduleId(s.id);
    setRouteId(s.routeId);
    setBusId(s.busId);
    setDriverId(s.driverId);
    setDepartureTime(s.departureTime.substring(0, 5));
    setArrivalTime(s.arrivalTime.substring(0, 5));
    setDaysOfWeek(s.daysOfWeek ? s.daysOfWeek.split(',').map(d => d.trim()) : ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
    setStartDate(s.startDate || '');
    setEndDate(s.endDate || '');
    setEditModalOpen(true);
  };

  const handleCreateSchedule = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post('/api/admin/schedules', {
        routeId,
        busId,
        driverId,
        departureTime: departureTime + ':00',
        arrivalTime: arrivalTime + ':00',
        daysOfWeek: daysOfWeek.join(','),
        startDate: startDate || null,
        endDate: endDate || null
      });
      if (res.data.success) {
        setSuccess('Schedule pattern generated successfully.');
        setCreateModalOpen(false);
        fetchSchedules();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to create schedule.');
    }
  };

  const handleUpdateSchedule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingScheduleId) return;
    try {
      const res = await axios.put(`/api/admin/schedules/${editingScheduleId}`, {
        routeId,
        busId,
        driverId,
        departureTime: departureTime + ':00',
        arrivalTime: arrivalTime + ':00',
        daysOfWeek: daysOfWeek.join(','),
        startDate: startDate || null,
        endDate: endDate || null
      });
      if (res.data.success) {
        setSuccess('Schedule updated successfully.');
        setEditModalOpen(false);
        setEditingScheduleId(null);
        fetchSchedules();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to update schedule.');
    }
  };

  const handleDeleteSchedule = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this schedule? Associated driver assignments will be released.')) {
      return;
    }
    try {
      const res = await axios.delete(`/api/admin/schedules/${id}`);
      if (res.data.success) {
        setSuccess('Schedule deleted successfully.');
        fetchSchedules();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to delete schedule.');
    }
  };

  const handleToggleStatus = async (id: string, currentStatus: string) => {
    const nextStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      const res = await axios.patch(`/api/admin/schedules/${id}/status`, null, {
        params: { status: nextStatus }
      });
      if (res.data.success) {
        fetchSchedules();
      }
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to toggle status.');
    }
  };

  const handleDayToggle = (day: string) => {
    if (daysOfWeek.includes(day)) {
      setDaysOfWeek(daysOfWeek.filter(d => d !== day));
    } else {
      setDaysOfWeek([...daysOfWeek, day]);
    }
  };

  const allDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

  return (
    <div className="space-y-6">
      {/* Header section */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-brandNavy tracking-tight">Active Transit Schedules</h2>
          <p className="text-xs text-brandTextSecondary mt-0.5 font-medium">Establish recurring transit timetables and assign operators</p>
        </div>
        <button
          onClick={openCreateModal}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md shadow-brandBlue/10 transition-all"
        >
          <Plus className="w-4 h-4" /> Create Timetable
        </button>
      </div>

      {error && <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs font-bold">{error}</div>}
      {success && <div className="p-4 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-2xl text-xs font-bold">{success}</div>}

      {/* Schedules list table */}
      <div className="bg-white rounded-2xl border border-brandBorder overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="bg-brandBg border-b border-brandBorder text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold">
                <th className="py-3.5 px-4">Bus</th>
                <th className="py-3.5 px-4">Driver</th>
                <th className="py-3.5 px-4">Route Name</th>
                <th className="py-3.5 px-4">Time window</th>
                <th className="py-3.5 px-4">Days</th>
                <th className="py-3.5 px-4">Date interval</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} className="text-center py-12">
                    <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue mx-auto"></div>
                  </td>
                </tr>
              ) : schedules.length > 0 ? (
                schedules.map((s) => (
                  <tr key={s.id} className="border-b border-brandBorder last:border-0 hover:bg-brandBlue/5 transition-all text-brandTextPrimary font-medium">
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-2">
                        <Bus className="w-4 h-4 text-brandBlue shrink-0" />
                        <span className="font-bold text-brandNavy">{s.busNumber}</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-2">
                        <User className="w-4 h-4 text-brandTextSecondary shrink-0" />
                        <span className="font-semibold text-brandTextPrimary">{s.driverName}</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-4 font-bold text-brandNavy">{s.routeName}</td>
                    <td className="py-3.5 px-4 font-mono text-brandBlue font-bold">
                      <div className="flex items-center gap-1.5">
                        <Clock className="w-3.5 h-3.5 text-brandTextSecondary" />
                        <span>{s.departureTime.substring(0, 5)} - {s.arrivalTime.substring(0, 5)}</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-[10px] text-brandTextSecondary font-bold max-w-[150px] truncate" title={s.daysOfWeek}>
                      {s.daysOfWeek}
                    </td>
                    <td className="py-3.5 px-4 text-[10px] text-brandTextSecondary font-semibold">
                      {s.startDate || 'N/A'} to {s.endDate || 'N/A'}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        s.status?.toUpperCase() === 'ACTIVE' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' : 'bg-brandTextSecondary/10 text-brandTextSecondary border border-brandBorder'
                      }`}>
                        {s.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right flex items-center justify-end gap-1.5">
                      <button
                        onClick={() => openEditModal(s)}
                        className="p-1 hover:bg-brandBg text-brandTextSecondary hover:text-brandBlue rounded transition-all"
                        title="Edit Timetable"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleToggleStatus(s.id, s.status)}
                        className="p-1 hover:bg-brandBg text-brandTextSecondary hover:text-brandBlue rounded transition-all"
                        title={s.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      >
                        <Ban className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDeleteSchedule(s.id)}
                        className="p-1 hover:bg-brandRed/10 text-brandTextSecondary hover:text-brandRed rounded transition-all"
                        title="Delete Timetable"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="text-center py-10 text-brandTextSecondary font-medium">
                    No active timetables found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="p-4 border-t border-brandBorder flex justify-between items-center text-brandTextSecondary text-xs">
            <span className="font-semibold">Showing {schedules.length} of {totalElements} schedules</span>
            <div className="flex gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="p-1.5 bg-white border border-brandBorder hover:bg-brandBg disabled:opacity-30 rounded-lg text-brandTextPrimary font-bold transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page === totalPages - 1}
                onClick={() => setPage(page + 1)}
                className="p-1.5 bg-white border border-brandBorder hover:bg-brandBg disabled:opacity-30 rounded-lg text-brandTextPrimary font-bold transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* CREATE TIMETABLE MODAL */}
      {createModalOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-lg rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4">Add Schedule Pattern</h3>
            <form onSubmit={handleCreateSchedule} className="space-y-3.5 text-xs">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Select Route</label>
                  <select value={routeId} onChange={e => setRouteId(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">Choose Route</option>
                    {routes.map(r => (
                      <option key={r.id} value={r.id}>{r.routeName} ({r.status})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Select Bus</label>
                  <select value={busId} onChange={e => setBusId(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">Choose Bus</option>
                    {buses.map(b => (
                      <option key={b.id} value={b.id}>{b.busNumber} ({b.status})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Select Driver</label>
                  <select value={driverId} onChange={e => setDriverId(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">Choose Driver</option>
                    {drivers.map(d => (
                      <option key={d.id} value={d.id}>{d.name} (Approval: {d.approvalStatus})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Departure Time</label>
                  <input type="time" value={departureTime} onChange={e => setDepartureTime(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Expected Arrival Time</label>
                  <input type="time" value={arrivalTime} onChange={e => setArrivalTime(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Active Start Date</label>
                  <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Active End Date</label>
                  <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-semibold mb-2">Days of Week</label>
                <div className="flex flex-wrap gap-2">
                  {allDays.map(day => (
                    <button
                      type="button"
                      key={day}
                      onClick={() => handleDayToggle(day)}
                      className={`px-3 py-1.5 rounded-lg border font-bold text-xs transition-all ${
                        daysOfWeek.includes(day)
                          ? 'bg-brandBlue border-brandBlue text-white shadow-sm'
                          : 'bg-brandBg border-brandBorder text-brandTextSecondary hover:border-brandTextPrimary hover:text-brandTextPrimary'
                      }`}
                    >
                      {day.substring(0, 3)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button type="button" onClick={() => setCreateModalOpen(false)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all">Save Schedule</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EDIT TIMETABLE MODAL */}
      {editModalOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-lg rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4">Edit Schedule Pattern</h3>
            <form onSubmit={handleUpdateSchedule} className="space-y-3.5 text-xs">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Select Route</label>
                  <select value={routeId} onChange={e => setRouteId(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">Choose Route</option>
                    {routes.map(r => (
                      <option key={r.id} value={r.id}>{r.routeName} ({r.status})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Select Bus</label>
                  <select value={busId} onChange={e => setBusId(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">Choose Bus</option>
                    {buses.map(b => (
                      <option key={b.id} value={b.id}>{b.busNumber} ({b.status})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Select Driver</label>
                  <select value={driverId} onChange={e => setDriverId(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">Choose Driver</option>
                    {drivers.map(d => (
                      <option key={d.id} value={d.id}>{d.name} (Approval: {d.approvalStatus})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Departure Time</label>
                  <input type="time" value={departureTime} onChange={e => setDepartureTime(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Expected Arrival Time</label>
                  <input type="time" value={arrivalTime} onChange={e => setArrivalTime(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Active Start Date</label>
                  <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Active End Date</label>
                  <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-semibold mb-2">Days of Week</label>
                <div className="flex flex-wrap gap-2">
                  {allDays.map(day => (
                    <button
                      type="button"
                      key={day}
                      onClick={() => handleDayToggle(day)}
                      className={`px-3 py-1.5 rounded-lg border font-bold text-xs transition-all ${
                        daysOfWeek.includes(day)
                          ? 'bg-brandBlue border-brandBlue text-white shadow-sm'
                          : 'bg-brandBg border-brandBorder text-brandTextSecondary hover:border-brandTextPrimary hover:text-brandTextPrimary'
                      }`}
                    >
                      {day.substring(0, 3)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button type="button" onClick={() => { setEditModalOpen(false); setEditingScheduleId(null); }} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all">Update Schedule</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
