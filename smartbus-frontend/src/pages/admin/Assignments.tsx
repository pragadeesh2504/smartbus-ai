import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Sliders,
  CheckCircle,
  AlertTriangle,
  Bus as BusIcon,
  MapPin,
  User,
  Clock,
  Calendar,
  Check,
  Trash2,
  Edit2,
  Plus,
  ArrowLeft,
  RefreshCw,
  Layers
} from 'lucide-react';

interface BusType {
  id: string;
  busNumber: string;
  model: string;
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
  startPoint: string;
  endPoint: string;
  status: string;
}

interface StopType {
  id: string;
  stopName: string;
}

interface RouteStopType {
  stop: StopType;
  sequenceNumber: number;
}

interface AssignmentType {
  id: string;
  busId: string;
  busNumber: string;
  driverId: string;
  driverName: string;
  routeId: string;
  routeName: string;
  scheduleId: string;
  departureTime: string;
  assignedAt: string;
  status: string;
}

export const Assignments: React.FC = () => {
  // Existing Assignments State
  const [assignments, setAssignments] = useState<AssignmentType[]>([]);
  const [assignmentsLoading, setAssignmentsLoading] = useState(true);
  const [showWizard, setShowWizard] = useState(false);

  // Delete Confirmation State
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [assignmentToDelete, setAssignmentToDelete] = useState<AssignmentType | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  // Edit State
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingAssignment, setEditingAssignment] = useState<AssignmentType | null>(null);
  const [editBusId, setEditBusId] = useState('');
  const [editDriverId, setEditDriverId] = useState('');
  const [editRouteId, setEditRouteId] = useState('');
  const [editStatus, setEditStatus] = useState('ACTIVE');
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState('');

  // Wizard Data State
  const [buses, setBuses] = useState<BusType[]>([]);
  const [drivers, setDrivers] = useState<DriverType[]>([]);
  const [routes, setRoutes] = useState<RouteType[]>([]);
  const [stopsCount, setStopsCount] = useState(0);

  // Wizard Steps
  const [step, setStep] = useState(1);
  const [selectedBus, setSelectedBus] = useState<BusType | null>(null);
  const [selectedRoute, setSelectedRoute] = useState<RouteType | null>(null);
  const [selectedDriver, setSelectedDriver] = useState<DriverType | null>(null);
  const [departureTime, setDepartureTime] = useState('07:30');
  const [arrivalTime, setArrivalTime] = useState('08:15');
  const [daysOfWeek, setDaysOfWeek] = useState<string[]>(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
  
  const [wizardLoading, setWizardLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchAssignments = async () => {
    try {
      setAssignmentsLoading(true);
      const res = await axios.get('/api/admin/assignments');
      if (res.data.success) {
        setAssignments(res.data.data || []);
      }
    } catch (err: any) {
      console.error('Failed to fetch assignments', err);
    } finally {
      setAssignmentsLoading(false);
    }
  };

  const fetchWizardData = async () => {
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
    fetchAssignments();
    fetchWizardData();
  }, []);

  useEffect(() => {
    const fetchStops = async () => {
      if (selectedRoute) {
        try {
          const res = await axios.get(`/api/admin/routes/${selectedRoute.id}/stops`);
          if (res.data.success) {
            setStopsCount(res.data.data.length);
          }
        } catch (e) {
          setStopsCount(0);
        }
      }
    };
    fetchStops();
  }, [selectedRoute]);

  const handleDayToggle = (day: string) => {
    if (daysOfWeek.includes(day)) {
      setDaysOfWeek(daysOfWeek.filter(d => d !== day));
    } else {
      setDaysOfWeek([...daysOfWeek, day]);
    }
  };

  const handleConfirmAssignment = async () => {
    setError('');
    setSuccess('');
    setWizardLoading(true);
    try {
      const schedRes = await axios.post('/api/admin/schedules', {
        routeId: selectedRoute?.id,
        busId: selectedBus?.id,
        driverId: selectedDriver?.id,
        departureTime: departureTime + ':00',
        arrivalTime: arrivalTime + ':00',
        daysOfWeek: daysOfWeek.join(','),
        startDate: null,
        endDate: null
      });

      const schedId = schedRes.data.data?.id || schedRes.data.id;

      // Also ensure assignment is created / confirmed
      await axios.post('/api/admin/assignments', {
        busId: selectedBus?.id,
        driverId: selectedDriver?.id,
        routeId: selectedRoute?.id,
        scheduleId: schedId
      });

      setSuccess('Assignment deployment confirmed successfully!');
      setStep(7);
      await fetchAssignments();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Conflict detected in assignment matrices.');
    } finally {
      setWizardLoading(false);
    }
  };

  const resetWizard = () => {
    setSelectedBus(null);
    setSelectedRoute(null);
    setSelectedDriver(null);
    setDepartureTime('07:30');
    setArrivalTime('08:15');
    setDaysOfWeek(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
    setStep(1);
    setError('');
  };

  // Delete Action Handlers
  const handleDeleteClick = (assignment: AssignmentType) => {
    setAssignmentToDelete(assignment);
    setDeleteError('');
    setDeleteModalOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!assignmentToDelete || deleteLoading) return;
    setDeleteLoading(true);
    setDeleteError('');
    try {
      const res = await axios.delete(`/api/admin/assignments/${assignmentToDelete.id}`);
      if (res.data.success) {
        setSuccess('Assignment deleted successfully.');
        setDeleteModalOpen(false);
        setAssignmentToDelete(null);
        await fetchAssignments();
      }
    } catch (err: any) {
      if (err.response?.status === 404) {
        setSuccess('Assignment was already removed.');
        setDeleteModalOpen(false);
        setAssignmentToDelete(null);
        await fetchAssignments();
      } else {
        const msg = err.response?.data?.message || 'Failed to delete assignment. Please try again.';
        setDeleteError(msg);
      }
    } finally {
      setDeleteLoading(false);
    }
  };

  // Edit Action Handlers
  const handleEditClick = (assignment: AssignmentType) => {
    setEditingAssignment(assignment);
    setEditBusId(assignment.busId);
    setEditDriverId(assignment.driverId);
    setEditRouteId(assignment.routeId);
    setEditStatus(assignment.status || 'ACTIVE');
    setEditError('');
    setEditModalOpen(true);
  };

  const handleConfirmEdit = async () => {
    if (!editingAssignment || editLoading) return;
    setEditLoading(true);
    setEditError('');
    try {
      const res = await axios.put(`/api/admin/assignments/${editingAssignment.id}`, {
        busId: editBusId,
        driverId: editDriverId,
        routeId: editRouteId,
        status: editStatus
      });
      if (res.data.success) {
        setSuccess('Assignment updated successfully.');
        setEditModalOpen(false);
        setEditingAssignment(null);
        await fetchAssignments();
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to update assignment.';
      setEditError(msg);
    } finally {
      setEditLoading(false);
    }
  };

  const allDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
  const stepsList = ['Select Bus', 'Select Route', 'Select Driver', 'Time Window', 'Frequency', 'Review', 'Confirm'];

  return (
    <div className="space-y-6 max-w-6xl mx-auto pb-12">
      {/* Header section */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
            <Sliders className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-brandNavy tracking-tight">Deployments & Assignments</h2>
            <p className="text-xs text-brandTextSecondary mt-0.5 font-medium">Manage bus, driver, route, and schedule assignments across active operations</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {showWizard ? (
            <button
              onClick={() => { setShowWizard(false); resetWizard(); }}
              className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors flex items-center gap-1.5"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back to Assignments</span>
            </button>
          ) : (
            <>
              <button
                onClick={fetchAssignments}
                className="p-2 border border-brandBorder hover:bg-brandBg text-brandTextSecondary hover:text-brandNavy rounded-xl text-xs font-bold transition-colors"
                title="Refresh assignments"
              >
                <RefreshCw className={`w-4 h-4 ${assignmentsLoading ? 'animate-spin' : ''}`} />
              </button>
              <button
                onClick={() => { resetWizard(); setShowWizard(true); }}
                className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white font-bold rounded-xl text-xs shadow-md shadow-brandBlue/20 transition-all flex items-center gap-1.5"
              >
                <Plus className="w-4 h-4" />
                <span>Deploy New Assignment</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Global Alerts */}
      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs flex gap-2.5 items-start font-bold">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="p-4 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-2xl text-xs flex gap-2.5 items-start font-bold">
          <CheckCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{success}</span>
        </div>
      )}

      {/* VIEW 1: ASSIGNMENTS LIST / CARDS */}
      {!showWizard && (
        <div className="space-y-4">
          {assignmentsLoading ? (
            <div className="bg-white border border-brandBorder rounded-2xl p-12 text-center text-brandTextSecondary">
              <div className="w-8 h-8 border-2 border-brandBlue border-t-transparent rounded-full animate-spin mx-auto mb-3" />
              <p className="text-xs font-bold">Loading active assignments from PostgreSQL...</p>
            </div>
          ) : assignments.length === 0 ? (
            <div className="bg-white border border-brandBorder rounded-2xl p-12 text-center space-y-3">
              <div className="w-12 h-12 bg-brandBlue/5 border border-brandBlue/10 rounded-2xl flex items-center justify-center text-brandBlue mx-auto">
                <Layers className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-extrabold text-brandNavy">No assignments found.</h3>
              <p className="text-xs text-brandTextSecondary max-w-sm mx-auto font-medium">
                No active bus assignments have been deployed yet. Deploy your first bus assignment using the deployment wizard.
              </p>
              <button
                onClick={() => { resetWizard(); setShowWizard(true); }}
                className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white font-bold rounded-xl text-xs shadow-md shadow-brandBlue/20 transition-all inline-flex items-center gap-1.5 mt-2"
              >
                <Plus className="w-4 h-4" />
                <span>Deploy First Assignment</span>
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {assignments.map(a => (
                <div
                  key={a.id}
                  className="bg-white border border-brandBorder rounded-2xl p-5 space-y-4 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
                >
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] font-extrabold uppercase tracking-wider text-brandTextSecondary">
                        Assignment
                      </span>
                      <span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        a.status?.toUpperCase() === 'ACTIVE'
                          ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20'
                          : 'bg-brandTextSecondary/10 text-brandTextSecondary border border-brandBorder'
                      }`}>
                        {a.status}
                      </span>
                    </div>

                    <div className="space-y-2 text-xs">
                      <div className="flex items-center gap-2.5 font-bold text-brandNavy">
                        <BusIcon className="w-4 h-4 text-brandBlue shrink-0" />
                        <span>Bus: {a.busNumber}</span>
                      </div>

                      <div className="flex items-center gap-2.5 font-bold text-brandNavy">
                        <User className="w-4 h-4 text-brandBlue shrink-0" />
                        <span>Driver: {a.driverName}</span>
                      </div>

                      <div className="flex items-center gap-2.5 font-bold text-brandNavy">
                        <MapPin className="w-4 h-4 text-brandBlue shrink-0" />
                        <span>Route: {a.routeName}</span>
                      </div>

                      <div className="flex items-center gap-2.5 font-semibold text-brandTextSecondary">
                        <Clock className="w-4 h-4 text-brandBlue shrink-0" />
                        <span>Schedule: {a.departureTime || '08:00'}</span>
                      </div>
                    </div>
                  </div>

                  {/* Actions: [ Edit ] [ Delete ] */}
                  <div className="flex items-center gap-2 pt-3 border-t border-brandBorder">
                    <button
                      onClick={() => handleEditClick(a)}
                      className="flex-1 px-3 py-1.5 bg-brandBg hover:bg-brandBlue/10 border border-brandBorder hover:border-brandBlue/30 text-brandNavy hover:text-brandBlue rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                      <span>Edit</span>
                    </button>

                    <button
                      onClick={() => handleDeleteClick(a)}
                      className="flex-1 px-3 py-1.5 bg-red-50 hover:bg-red-100 border border-red-200 text-red-600 hover:text-red-700 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Delete</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* VIEW 2: SCHEDULE ASSIGNMENT WIZARD */}
      {showWizard && (
        <div className="space-y-6">
          {/* Progress timeline */}
          <div className="bg-white p-4 rounded-2xl border border-brandBorder flex justify-between items-center text-brandTextSecondary text-[10px] font-bold uppercase tracking-wider relative overflow-hidden shadow-sm">
            {stepsList.map((label, idx) => {
              const stepNum = idx + 1;
              const isCompleted = step > stepNum || step === 7;
              const isActive = step === stepNum;
              return (
                <div key={label} className="flex flex-col items-center gap-1.5 z-10">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center font-bold border transition-all ${
                    isCompleted ? 'bg-brandGreen border-brandGreen text-white' :
                    isActive ? 'bg-brandBlue border-brandBlue text-white shadow-md shadow-brandBlue/20' :
                    'bg-brandBg border-brandBorder text-brandTextSecondary'
                  }`}>
                    {isCompleted ? <Check className="w-4 h-4" /> : stepNum}
                  </div>
                  <span className={isActive ? 'text-brandBlue font-extrabold' : isCompleted ? 'text-brandGreen font-bold' : 'text-brandTextSecondary/70'}>
                    {label}
                  </span>
                </div>
              );
            })}
            <div className="absolute top-8 left-6 right-6 h-0.5 bg-brandBg -z-0 border-b border-brandBorder/50" />
          </div>

          {/* STEP 1: Select Bus */}
          {step === 1 && (
            <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Step 1: Select Operational Bus</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                {buses.map(b => (
                  <div
                    key={b.id}
                    onClick={() => setSelectedBus(b)}
                    className={`p-4 rounded-2xl border transition-all cursor-pointer flex flex-col gap-2 relative overflow-hidden ${
                      selectedBus?.id === b.id
                        ? 'bg-brandBlue/5 border-brandBlue text-brandBlue shadow-sm'
                        : 'bg-white border-brandBorder text-brandTextPrimary hover:border-brandBlue/30 hover:bg-brandBg/30'
                    }`}
                  >
                    <BusIcon className="w-6 h-6 text-brandBlue mb-1" />
                    <span className="font-bold text-sm text-brandNavy">{b.busNumber}</span>
                    <span className="text-[10px] text-brandTextSecondary font-semibold">{b.model}</span>
                    <span className={`inline-flex self-start mt-2 px-2 py-0.5 rounded-full font-bold text-[10px] ${
                      b.status?.toUpperCase() === 'ACTIVE' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' : 'bg-brandRed/10 text-brandRed border border-brandRed/20'
                    }`}>
                      {b.status}
                    </span>
                  </div>
                ))}
              </div>
              <div className="flex justify-end pt-3 border-t border-brandBorder">
                <button
                  onClick={() => setStep(2)}
                  disabled={!selectedBus}
                  className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 disabled:opacity-30 text-white rounded-xl text-xs font-bold shadow-md transition-all"
                >
                  Continue Route Configuration
                </button>
              </div>
            </div>
          )}

          {/* STEP 2: Select Route */}
          {step === 2 && (
            <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Step 2: Select Route Track</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {routes.map(r => (
                  <div
                    key={r.id}
                    onClick={() => setSelectedRoute(r)}
                    className={`p-4 rounded-2xl border transition-all cursor-pointer flex flex-col gap-2 relative overflow-hidden ${
                      selectedRoute?.id === r.id
                        ? 'bg-brandBlue/5 border-brandBlue text-brandBlue shadow-sm'
                        : 'bg-white border-brandBorder text-brandTextPrimary hover:border-brandBlue/30 hover:bg-brandBg/30'
                    }`}
                  >
                    <MapPin className="w-6 h-6 text-brandBlue mb-1" />
                    <span className="font-bold text-sm text-brandNavy">{r.routeName}</span>
                    <div className="flex gap-2 text-[10px] text-brandTextSecondary mt-2 font-bold">
                      <span>Start: {r.startPoint}</span>
                      <span>|</span>
                      <span>End: {r.endPoint}</span>
                    </div>
                  </div>
                ))}
              </div>
              <div className="flex justify-between pt-3 border-t border-brandBorder">
                <button onClick={() => setStep(1)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors">Back</button>
                <button
                  onClick={() => setStep(3)}
                  disabled={!selectedRoute}
                  className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 disabled:opacity-30 text-white rounded-xl text-xs font-bold shadow-md transition-all"
                >
                  Continue Driver Assignment
                </button>
              </div>
            </div>
          )}

          {/* STEP 3: Select Driver */}
          {step === 3 && (
            <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Step 3: Select Driver</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                {drivers.map(d => (
                  <div
                    key={d.id}
                    onClick={() => setSelectedDriver(d)}
                    className={`p-4 rounded-2xl border transition-all cursor-pointer flex flex-col gap-2 relative overflow-hidden ${
                      selectedDriver?.id === d.id
                        ? 'bg-brandBlue/5 border-brandBlue text-brandBlue shadow-sm'
                        : 'bg-white border-brandBorder text-brandTextPrimary hover:border-brandBlue/30 hover:bg-brandBg/30'
                    }`}
                  >
                    <User className="w-6 h-6 text-brandBlue mb-1" />
                    <span className="font-bold text-sm text-brandNavy">{d.name}</span>
                    <span className="text-[10px] text-brandTextSecondary font-semibold">{d.status}</span>
                    <span className={`inline-flex self-start mt-2 px-2 py-0.5 rounded-full font-bold text-[10px] ${
                      d.approvalStatus?.toUpperCase() === 'APPROVED' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' : 'bg-brandRed/10 text-brandRed border border-brandRed/20'
                    }`}>
                      Approval: {d.approvalStatus}
                    </span>
                  </div>
                ))}
              </div>
              <div className="flex justify-between pt-3 border-t border-brandBorder">
                <button onClick={() => setStep(2)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors">Back</button>
                <button
                  onClick={() => setStep(4)}
                  disabled={!selectedDriver}
                  className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 disabled:opacity-30 text-white rounded-xl text-xs font-bold shadow-md transition-all"
                >
                  Configure Schedule Time
                </button>
              </div>
            </div>
          )}

          {/* STEP 4: Configure Time */}
          {step === 4 && (
            <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Step 4: Configure Schedule Times</h3>
              <div className="grid grid-cols-2 gap-4 max-w-md">
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1 text-xs">Departure Time</label>
                  <input
                    type="time"
                    value={departureTime}
                    onChange={e => setDepartureTime(e.target.value)}
                    required
                    className="w-full px-4 py-2.5 bg-brandBg border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
                  />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-bold mb-1 text-xs">Expected Arrival Time</label>
                  <input
                    type="time"
                    value={arrivalTime}
                    onChange={e => setArrivalTime(e.target.value)}
                    required
                    className="w-full px-4 py-2.5 bg-brandBg border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
                  />
                </div>
              </div>
              <div className="flex justify-between pt-3 border-t border-brandBorder">
                <button onClick={() => setStep(3)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors">Back</button>
                <button
                  onClick={() => setStep(5)}
                  className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md transition-all"
                >
                  Configure Frequency
                </button>
              </div>
            </div>
          )}

          {/* STEP 5: Configure Days */}
          {step === 5 && (
            <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Step 5: Select Active Days</h3>
              <div className="flex flex-wrap gap-2.5">
                {allDays.map(day => (
                  <button
                    type="button"
                    key={day}
                    onClick={() => handleDayToggle(day)}
                    className={`px-3.5 py-1.5 rounded-xl border text-xs font-bold transition-all ${
                      daysOfWeek.includes(day)
                        ? 'bg-brandBlue border-brandBlue text-white shadow-sm'
                        : 'bg-brandBg border-brandBorder text-brandTextSecondary hover:border-brandTextPrimary hover:text-brandTextPrimary'
                    }`}
                  >
                    {day}
                  </button>
                ))}
              </div>
              <div className="flex justify-between pt-3 border-t border-brandBorder">
                <button onClick={() => setStep(4)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors">Back</button>
                <button
                  onClick={() => setStep(6)}
                  disabled={daysOfWeek.length === 0}
                  className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 disabled:opacity-30 text-white rounded-xl text-xs font-bold shadow-md transition-all"
                >
                  Review Deployment Summary
                </button>
              </div>
            </div>
          )}

          {/* STEP 6: Review Summary */}
          {step === 6 && selectedBus && selectedRoute && selectedDriver && (
            <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-6 shadow-sm">
              <h3 className="text-xs font-extrabold text-brandNavy uppercase tracking-wider">Step 6: Review Deployment Parameters</h3>
              
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 text-xs">
                <div className="p-4 bg-brandBg border border-brandBorder rounded-2xl space-y-2.5">
                  <p className="text-brandTextSecondary font-bold uppercase tracking-wider text-[9px]">Entities Bind</p>
                  <p className="flex items-center gap-2 font-bold text-brandNavy text-xs">
                    <BusIcon className="w-4 h-4 text-brandBlue shrink-0" /> Bus: {selectedBus.busNumber}
                  </p>
                  <p className="flex items-center gap-2 font-bold text-brandNavy text-xs">
                    <User className="w-4 h-4 text-brandBlue shrink-0" /> Driver: {selectedDriver.name}
                  </p>
                  <p className="flex items-center gap-2 font-bold text-brandNavy text-xs">
                    <MapPin className="w-4 h-4 text-brandBlue shrink-0" /> Route: {selectedRoute.routeName}
                  </p>
                </div>

                <div className="p-4 bg-brandBg border border-brandBorder rounded-2xl space-y-2.5">
                  <p className="text-brandTextSecondary font-bold uppercase tracking-wider text-[9px]">Timetable Details</p>
                  <p className="flex items-center gap-2 font-bold text-brandNavy text-xs">
                    <Clock className="w-4 h-4 text-brandBlue shrink-0" /> Time: {departureTime} to {arrivalTime}
                  </p>
                  <p className="flex items-center gap-2 font-bold text-brandNavy text-xs">
                    <Calendar className="w-4 h-4 text-brandBlue shrink-0" /> Days: {daysOfWeek.join(', ')}
                  </p>
                  <p className="flex items-center gap-2 font-bold text-brandNavy text-xs">
                    <CheckCircle className="w-4 h-4 text-brandBlue shrink-0" /> Total Stops: {stopsCount} stops
                  </p>
                </div>
              </div>

              <div className="p-3 bg-brandBg border border-brandBorder rounded-xl text-[10px] text-brandTextSecondary italic text-center font-semibold">
                System will check schedules conflicts, unapproved drivers and inactive assets before confirming.
              </div>

              <div className="flex justify-between pt-3 border-t border-brandBorder">
                <button onClick={() => setStep(5)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors">Back</button>
                <button
                  onClick={handleConfirmAssignment}
                  disabled={wizardLoading}
                  className="px-6 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md transition-all flex items-center gap-2"
                >
                  {wizardLoading && <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />}
                  <span>{wizardLoading ? 'Validating Conflict Matrices...' : 'Confirm Assignment'}</span>
                </button>
              </div>
            </div>
          )}

          {/* STEP 7: Completed Screen */}
          {step === 7 && (
            <div className="bg-white border border-brandBorder rounded-2xl p-8 text-center space-y-4 shadow-sm">
              <div className="w-14 h-14 bg-brandGreen/10 border border-brandGreen/35 text-brandGreen rounded-full flex items-center justify-center mx-auto shadow-md">
                <Check className="w-8 h-8" />
              </div>
              <h3 className="text-base font-extrabold text-brandNavy">Assignment Confirmed</h3>
              <p className="text-xs text-brandTextSecondary max-w-sm mx-auto font-medium">
                The bus, route, driver, and timetable parameters were validated with zero conflicts. Deployment is now active in PostgreSQL.
              </p>
              <div className="pt-4 flex gap-3 justify-center border-t border-brandBorder">
                <button
                  onClick={() => { setShowWizard(false); resetWizard(); fetchAssignments(); }}
                  className="px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md transition-all"
                >
                  View All Assignments
                </button>
                <button
                  onClick={resetWizard}
                  className="px-5 py-2.5 border border-brandBorder hover:bg-brandBg text-brandTextPrimary rounded-xl text-xs font-bold transition-all"
                >
                  Deploy Another Bus
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* DELETE CONFIRMATION DIALOG / MODAL */}
      {deleteModalOpen && assignmentToDelete && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full border border-red-200 shadow-2xl p-6 space-y-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-red-50 border border-red-200 flex items-center justify-center text-red-600 shrink-0">
                <Trash2 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-brandNavy">Delete Assignment?</h3>
                <p className="text-xs text-brandTextSecondary">This assignment will be removed.</p>
              </div>
            </div>

            {deleteError && (
              <div className="p-3.5 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 font-bold flex gap-2 items-start">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-red-600" />
                <span>{deleteError}</span>
              </div>
            )}

            <div className="bg-brandBg border border-brandBorder rounded-xl p-4 space-y-2 text-xs">
              <div className="flex justify-between items-center py-1 border-b border-brandBorder/50">
                <span className="text-brandTextSecondary font-semibold">Bus:</span>
                <span className="font-bold text-brandNavy">{assignmentToDelete.busNumber}</span>
              </div>
              <div className="flex justify-between items-center py-1 border-b border-brandBorder/50">
                <span className="text-brandTextSecondary font-semibold">Driver:</span>
                <span className="font-bold text-brandNavy">{assignmentToDelete.driverName}</span>
              </div>
              <div className="flex justify-between items-center py-1 border-b border-brandBorder/50">
                <span className="text-brandTextSecondary font-semibold">Route:</span>
                <span className="font-bold text-brandNavy">{assignmentToDelete.routeName}</span>
              </div>
              <div className="flex justify-between items-center py-1">
                <span className="text-brandTextSecondary font-semibold">Schedule:</span>
                <span className="font-bold text-brandNavy">{assignmentToDelete.departureTime || 'Standard Timetable'}</span>
              </div>
            </div>

            <p className="text-[11px] text-brandTextSecondary leading-relaxed">
              This assignment will be permanently removed from PostgreSQL. Future trips cannot be deployed under this assignment.
            </p>

            <div className="flex gap-3 justify-end pt-3 border-t border-brandBorder">
              <button
                type="button"
                onClick={() => {
                  if (!deleteLoading) {
                    setDeleteModalOpen(false);
                    setAssignmentToDelete(null);
                    setDeleteError('');
                  }
                }}
                disabled={deleteLoading}
                className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                disabled={deleteLoading}
                className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white font-bold rounded-xl text-xs shadow-md shadow-red-600/20 transition-all disabled:opacity-50 flex items-center gap-2"
              >
                {deleteLoading ? (
                  <>
                    <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>Deleting Assignment...</span>
                  </>
                ) : (
                  <>
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>Delete Assignment</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* EDIT MODAL */}
      {editModalOpen && editingAssignment && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full border border-brandBorder shadow-2xl p-6 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-brandBlue/5 border border-brandBlue/10 flex items-center justify-center text-brandBlue shrink-0">
                <Edit2 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-brandNavy">Edit Assignment</h3>
                <p className="text-xs text-brandTextSecondary">Update assignment details in PostgreSQL</p>
              </div>
            </div>

            {editError && (
              <div className="p-3.5 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 font-bold flex gap-2 items-start">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-red-600" />
                <span>{editError}</span>
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-brandTextPrimary font-bold mb-1">Assigned Bus</label>
                <select
                  value={editBusId}
                  onChange={e => setEditBusId(e.target.value)}
                  className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl font-medium text-brandNavy focus:outline-none focus:border-brandBlue"
                >
                  {buses.map(b => (
                    <option key={b.id} value={b.id}>{b.busNumber} ({b.model})</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-bold mb-1">Assigned Driver</label>
                <select
                  value={editDriverId}
                  onChange={e => setEditDriverId(e.target.value)}
                  className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl font-medium text-brandNavy focus:outline-none focus:border-brandBlue"
                >
                  {drivers.map(d => (
                    <option key={d.id} value={d.id}>{d.name} ({d.status})</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-bold mb-1">Route</label>
                <select
                  value={editRouteId}
                  onChange={e => setEditRouteId(e.target.value)}
                  className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl font-medium text-brandNavy focus:outline-none focus:border-brandBlue"
                >
                  {routes.map(r => (
                    <option key={r.id} value={r.id}>{r.routeName}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-bold mb-1">Assignment Status</label>
                <select
                  value={editStatus}
                  onChange={e => setEditStatus(e.target.value)}
                  className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl font-medium text-brandNavy focus:outline-none focus:border-brandBlue"
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="RELEASED">RELEASED</option>
                  <option value="COMPLETED">COMPLETED</option>
                </select>
              </div>
            </div>

            <div className="flex gap-3 justify-end pt-3 border-t border-brandBorder">
              <button
                type="button"
                onClick={() => {
                  if (!editLoading) {
                    setEditModalOpen(false);
                    setEditingAssignment(null);
                    setEditError('');
                  }
                }}
                disabled={editLoading}
                className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmEdit}
                disabled={editLoading}
                className="px-5 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white font-bold rounded-xl text-xs shadow-md shadow-brandBlue/20 transition-all disabled:opacity-50 flex items-center gap-2"
              >
                {editLoading ? (
                  <>
                    <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>Saving...</span>
                  </>
                ) : (
                  <span>Save Changes</span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
