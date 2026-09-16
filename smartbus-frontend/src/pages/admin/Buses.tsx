import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Bus,
  Search,
  Plus,
  Edit2,
  Trash2,
  RefreshCw,
  QrCode,
  Printer,
  ChevronLeft,
  ChevronRight,
  Shield,
  X
} from 'lucide-react';

interface BusType {
  id: string;
  busNumber: string;
  registrationNumber: string;
  manufacturer: string;
  manufacturingYear: number;
  busType: string;
  busCode: string;
  gpsDeviceId: string | null;
  gpsDeviceStatus: string;
  model: string;
  capacity: number;
  status: string;
}

interface GpsDeviceType {
  id: string;
  deviceId: string;
  status: string;
}

export const Buses: React.FC = () => {
  const [buses, setBuses] = useState<BusType[]>([]);
  const [gpsDevices, setGpsDevices] = useState<GpsDeviceType[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);

  // Modals state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [qrModalOpen, setQrModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [selectedBus, setSelectedBus] = useState<BusType | null>(null);
  const [busToDelete, setBusToDelete] = useState<BusType | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [qrData, setQrData] = useState<{ busCode: string; token: string; payload: string } | null>(null);

  // Form states
  const [busNumber, setBusNumber] = useState('');
  const [registrationNumber, setRegistrationNumber] = useState('');
  const [manufacturer, setManufacturer] = useState('');
  const [manufacturingYear, setManufacturingYear] = useState<number>(2024);
  const [busTypeField, setBusTypeField] = useState('AC');
  const [model, setModel] = useState('');
  const [capacity, setCapacity] = useState(40);
  const [gpsDeviceId, setGpsDeviceId] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchBuses = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/admin/buses', {
        params: { page, size, search, status: statusFilter }
      });
      if (res.data.success) {
        setBuses(res.data.data.content);
        setTotalElements(res.data.data.totalElements);
        setTotalPages(res.data.data.totalPages);
      }
    } catch (e: any) {
      setError('Failed to fetch buses.');
    } finally {
      setLoading(false);
    }
  };

  const fetchGpsDevices = async () => {
    try {
      const res = await axios.get('/api/admin/gps-devices');
      if (res.data.success) {
        setGpsDevices(res.data.data);
      }
    } catch (e) {}
  };

  useEffect(() => {
    fetchBuses();
  }, [page, statusFilter]);

  useEffect(() => {
    fetchGpsDevices();
  }, []);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchBuses();
  };

  const openCreateModal = () => {
    setError('');
    setSuccess('');
    setBusNumber('');
    setRegistrationNumber('');
    setManufacturer('');
    setManufacturingYear(2024);
    setBusTypeField('AC');
    setModel('');
    setCapacity(40);
    setGpsDeviceId('');
    setCreateModalOpen(true);
  };

  const handleCreateBus = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post('/api/admin/buses', {
        busNumber,
        registrationNumber,
        manufacturer,
        manufacturingYear,
        busType: busTypeField,
        model,
        capacity,
        gpsDeviceId: gpsDeviceId || null
      });
      if (res.data.success) {
        setSuccess('Bus created successfully.');
        setCreateModalOpen(false);
        fetchBuses();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to create bus.');
    }
  };

  const openEditModal = (bus: BusType) => {
    setError('');
    setSuccess('');
    setSelectedBus(bus);
    setBusNumber(bus.busNumber);
    setRegistrationNumber(bus.registrationNumber || '');
    setManufacturer(bus.manufacturer || '');
    setManufacturingYear(bus.manufacturingYear || 2024);
    setBusTypeField(bus.busType || 'AC');
    setModel(bus.model || '');
    setCapacity(bus.capacity || 40);
    setGpsDeviceId(bus.gpsDeviceId || '');
    setStatus(bus.status || 'ACTIVE');
    setEditModalOpen(true);
  };

  const handleEditBus = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedBus) return;
    try {
      const res = await axios.put(`/api/admin/buses/${selectedBus.id}`, {
        busNumber,
        registrationNumber,
        manufacturer,
        manufacturingYear,
        busType: busTypeField,
        model,
        capacity,
        gpsDeviceId: gpsDeviceId || null,
        status
      });
      if (res.data.success) {
        setSuccess('Bus details updated successfully.');
        setEditModalOpen(false);
        fetchBuses();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to edit bus.');
    }
  };

  const handleToggleStatus = async (bus: BusType) => {
    try {
      const nextStatus = bus.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      const res = await axios.put(`/api/admin/buses/${bus.id}`, {
        busNumber: bus.busNumber,
        registrationNumber: bus.registrationNumber,
        manufacturer: bus.manufacturer,
        manufacturingYear: bus.manufacturingYear,
        busType: bus.busType,
        model: bus.model,
        capacity: bus.capacity,
        gpsDeviceId: bus.gpsDeviceId,
        status: nextStatus
      });
      if (res.data.success) {
        setSuccess(`Bus status marked ${nextStatus}.`);
        fetchBuses();
      }
    } catch (e: any) {
      setError('Failed to update bus status.');
    }
  };

  const handleViewQr = async (bus: BusType) => {
    try {
      setSelectedBus(bus);
      const res = await axios.get(`/api/admin/buses/${bus.id}/qr`);
      if (res.data.success) {
        setQrData(res.data.data);
        setQrModalOpen(true);
      }
    } catch (e) {
      setError('Failed to fetch bus QR token.');
    }
  };

  const handleRegenerateQr = async () => {
    if (!selectedBus) return;
    if (!window.confirm('Warning: Regenerating the token will revoke the existing driver QR layout immediately. Continue?')) return;
    try {
      const res = await axios.post(`/api/admin/buses/${selectedBus.id}/qr/regenerate`);
      if (res.data.success) {
        setQrData(res.data.data);
        setSuccess('QR token rotated successfully.');
      }
    } catch (e) {
      setError('Failed to rotate QR token.');
    }
  };

  const openDeleteModal = (bus: BusType) => {
    setDeleteError('');
    setBusToDelete(bus);
    setDeleteModalOpen(true);
  };

  const handleDeleteBus = async () => {
    if (!busToDelete) return;
    setDeleteLoading(true);
    setDeleteError('');
    try {
      const res = await axios.delete(`/api/admin/buses/${busToDelete.id}`);
      if (res.data.success) {
        setSuccess(`Bus ${busToDelete.busNumber} decommissioned successfully.`);
        setDeleteModalOpen(false);
        setBusToDelete(null);
        fetchBuses();
      }
    } catch (e: any) {
      setDeleteError(e.response?.data?.message || 'Failed to delete bus.');
    } finally {
      setDeleteLoading(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 print:hidden">
        <div>
          <h2 className="text-xl font-bold text-brandNavy tracking-tight font-sans">Fleet Vehicles Management</h2>
          <p className="text-xs text-brandTextSecondary mt-0.5">Register, configure, and inspect college shuttle parameters</p>
        </div>
        <button
          onClick={openCreateModal}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md shadow-brandBlue/10 transition-all"
        >
          <Plus className="w-4 h-4" /> Register New Bus
        </button>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs font-bold print:hidden">
          {error}
        </div>
      )}

      {success && (
        <div className="p-4 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-2xl text-xs font-bold print:hidden">
          {success}
        </div>
      )}

      {/* Filter / Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-brandBorder flex flex-col md:flex-row gap-4 justify-between items-center print:hidden shadow-sm">
        <form onSubmit={handleSearchSubmit} className="relative w-full md:w-80">
          <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
            <Search className="w-4 h-4" />
          </span>
          <input
            type="text"
            placeholder="Search by bus number or code..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-xs text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue placeholder:text-brandTextSecondary/60 transition-all"
          />
        </form>

        <div className="flex items-center gap-3 w-full md:w-auto">
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            className="w-full md:w-40 px-3 py-2 bg-white border border-brandBorder rounded-xl text-xs text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all font-semibold"
          >
            <option value="">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="MAINTENANCE">Maintenance</option>
          </select>
        </div>
      </div>

      {/* Main Table */}
      <div className="bg-white rounded-2xl border border-brandBorder overflow-hidden print:hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="bg-brandBg border-b border-brandBorder text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold">
                <th className="py-3.5 px-4">Bus No.</th>
                <th className="py-3.5 px-4">Bus Code</th>
                <th className="py-3.5 px-4">Registration</th>
                <th className="py-3.5 px-4">Model & Make</th>
                <th className="py-3.5 px-4">Capacity</th>
                <th className="py-3.5 px-4">GPS Hardware</th>
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
              ) : buses.length > 0 ? (
                buses.map((bus) => (
                  <tr key={bus.id} className="border-b border-brandBorder last:border-0 hover:bg-brandBlue/5 transition-all text-brandTextPrimary">
                    <td className="py-3.5 px-4 font-bold text-brandNavy">{bus.busNumber}</td>
                    <td className="py-3.5 px-4 font-mono font-bold text-brandBlue">{bus.busCode}</td>
                    <td className="py-3.5 px-4 font-semibold text-brandTextSecondary">{bus.registrationNumber || 'N/A'}</td>
                    <td className="py-3.5 px-4 font-medium text-brandTextPrimary">{bus.manufacturer ? `${bus.manufacturer} ${bus.model}` : bus.model}</td>
                    <td className="py-3.5 px-4 font-medium text-brandTextSecondary">{bus.capacity} seats</td>
                    <td className="py-3.5 px-4">
                      {bus.gpsDeviceId ? (
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full font-bold text-[10px] ${
                          bus.gpsDeviceStatus?.toUpperCase() === 'ONLINE' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' : 'bg-brandRed/10 text-brandRed border border-brandRed/20'
                        }`}>
                          {bus.gpsDeviceStatus}
                        </span>
                      ) : (
                        <span className="text-brandTextSecondary/60 text-[10px] italic font-semibold">Not Configured</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        bus.status?.toUpperCase() === 'ACTIVE' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' :
                        bus.status?.toUpperCase() === 'MAINTENANCE' ? 'bg-brandAmber/10 text-brandAmber border border-brandAmber/20' :
                        'bg-brandTextSecondary/10 text-brandTextSecondary border border-brandBorder'
                      }`}>
                        {bus.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right flex items-center justify-end gap-2.5">
                      <button
                        onClick={() => openEditModal(bus)}
                        className="p-1 hover:bg-brandBg rounded-lg text-brandTextSecondary hover:text-brandBlue transition-colors"
                        title="Edit Bus"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleToggleStatus(bus)}
                        className="p-1 hover:bg-brandBg rounded-lg text-brandTextSecondary hover:text-brandBlue transition-colors"
                        title={bus.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      >
                        <RefreshCw className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleViewQr(bus)}
                        className="p-1.5 bg-brandBlue/10 hover:bg-brandBlue rounded-lg text-brandBlue hover:text-white transition-all shadow-sm"
                        title="QR Console"
                      >
                        <QrCode className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => openDeleteModal(bus)}
                        className="p-1 hover:bg-brandRed/10 rounded-lg text-brandTextSecondary hover:text-brandRed transition-colors"
                        title="Delete Bus"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="text-center py-10 text-brandTextSecondary font-medium">
                    No buses found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination controls */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-brandBorder flex justify-between items-center text-brandTextSecondary text-xs">
            <span className="font-medium">Showing {buses.length} of {totalElements} vehicles</span>
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

      {/* CREATE MODAL */}
      {createModalOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4 print:hidden">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 relative overflow-hidden text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4">Register New Transit Bus</h3>
            <form onSubmit={handleCreateBus} className="space-y-3.5 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Bus Name/Number</label>
                  <input type="text" placeholder="e.g. BUS-104" value={busNumber} onChange={e => setBusNumber(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Registration Number</label>
                  <input type="text" placeholder="e.g. TN-01-XY-9876" value={registrationNumber} onChange={e => setRegistrationNumber(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Manufacturer</label>
                  <input type="text" placeholder="e.g. Volvo" value={manufacturer} onChange={e => setManufacturer(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Manufacturing Year</label>
                  <input type="number" value={manufacturingYear} onChange={e => setManufacturingYear(parseInt(e.target.value))} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Bus Type</label>
                  <select value={busTypeField} onChange={e => setBusTypeField(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="AC">AC Coach</option>
                    <option value="NON_AC">Non-AC Coach</option>
                    <option value="MINI">Mini Bus</option>
                  </select>
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Model Name</label>
                  <input type="text" placeholder="e.g. B8R Hybrid" value={model} onChange={e => setModel(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Seating Capacity</label>
                  <input type="number" value={capacity} onChange={e => setCapacity(parseInt(e.target.value))} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Assigned GPS Device</label>
                  <select value={gpsDeviceId} onChange={e => setGpsDeviceId(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">No Hardware Bound</option>
                    {gpsDevices.map(d => (
                      <option key={d.id} value={d.id}>{d.deviceId} ({d.status})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button type="button" onClick={() => setCreateModalOpen(false)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all">Confirm Bus</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EDIT MODAL */}
      {editModalOpen && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4 print:hidden">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4">Edit Vehicle Properties</h3>
            <form onSubmit={handleEditBus} className="space-y-3.5 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Bus Number</label>
                  <input type="text" value={busNumber} onChange={e => setBusNumber(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Registration</label>
                  <input type="text" value={registrationNumber} onChange={e => setRegistrationNumber(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Manufacturer</label>
                  <input type="text" value={manufacturer} onChange={e => setManufacturer(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Manufacturing Year</label>
                  <input type="number" value={manufacturingYear} onChange={e => setManufacturingYear(parseInt(e.target.value))} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Bus Type</label>
                  <select value={busTypeField} onChange={e => setBusTypeField(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="AC">AC Coach</option>
                    <option value="NON_AC">Non-AC Coach</option>
                    <option value="MINI">Mini Bus</option>
                  </select>
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Model</label>
                  <input type="text" value={model} onChange={e => setModel(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Seating Capacity</label>
                  <input type="number" value={capacity} onChange={e => setCapacity(parseInt(e.target.value))} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Assigned GPS Device</label>
                  <select value={gpsDeviceId} onChange={e => setGpsDeviceId(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                    <option value="">No Hardware Bound</option>
                    {gpsDevices.map(d => (
                      <option key={d.id} value={d.id}>{d.deviceId} ({d.status})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-semibold mb-1">Operational Status</label>
                <select value={status} onChange={e => setStatus(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all">
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="MAINTENANCE">MAINTENANCE</option>
                  <option value="INACTIVE">INACTIVE</option>
                </select>
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button type="button" onClick={() => setEditModalOpen(false)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* QR & PRINT WIZARD MODAL */}
      {qrModalOpen && qrData && selectedBus && (
        <div className="fixed inset-0 bg-brandNavy/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="w-full max-w-sm flex flex-col bg-white border border-brandBorder rounded-3xl overflow-hidden relative shadow-2xl print:bg-white print:border-none print:w-full print:max-w-none print:shadow-none print:inset-0 print:absolute">
            {/* Header control strip */}
            <div className="h-14 bg-brandBg border-b border-brandBorder px-4 flex justify-between items-center shrink-0 print:hidden">
              <span className="text-xs font-bold text-brandNavy">QR Transit Registry</span>
              <div className="flex gap-2">
                <button
                  onClick={handleRegenerateQr}
                  className="p-1.5 bg-white hover:bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary font-semibold transition-all text-[11px] flex items-center gap-1.5"
                  title="Revoke and Generate new QR Token"
                >
                  <RefreshCw className="w-3 h-3 animate-spin-hover" /> Reset
                </button>
                <button
                  onClick={handlePrint}
                  className="p-1.5 bg-brandBlue hover:bg-brandBlue/90 rounded-xl text-white transition-all text-[11px] font-semibold flex items-center gap-1.5 shadow-sm"
                >
                  <Printer className="w-3 h-3" /> Print
                </button>
                <button
                  onClick={() => setQrModalOpen(false)}
                  className="p-1.5 bg-white border border-brandBorder hover:bg-brandBg rounded-xl text-brandTextSecondary hover:text-brandTextPrimary transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Printable Bus QR Card */}
            <div className="flex-1 flex flex-col items-center p-8 bg-white text-center font-sans print:p-12 print:justify-center">
              {/* Branding Header */}
              <div className="mb-6 flex flex-col items-center">
                <div className="p-2.5 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue mb-2">
                  <Shield className="w-6 h-6" />
                </div>
                <h4 className="text-sm font-extrabold text-brandNavy tracking-widest uppercase">
                  SmartBus AI
                </h4>
                <p className="text-[10px] text-brandBlue font-bold uppercase tracking-wider mt-0.5">
                  College Transport Service
                </p>
              </div>

              {/* Vehicle Identifiers */}
              <div className="w-full bg-brandBg border border-brandBorder rounded-2xl py-3 px-4 mb-6">
                <p className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider">Bus Number</p>
                <p className="text-lg font-black text-brandNavy tracking-tight uppercase">
                  {selectedBus.busNumber}
                </p>
                <p className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider mt-2.5">Bus Code</p>
                <p className="text-sm font-bold font-mono text-brandBlue tracking-wider">
                  {selectedBus.busCode}
                </p>
              </div>

              {/* QR Code Renders via public qrserver API */}
              <div className="p-4 bg-white rounded-2xl border border-brandBorder shadow-sm mb-6 flex items-center justify-center">
                <img
                  src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrData.payload)}`}
                  alt={`QR for ${selectedBus.busNumber}`}
                  className="w-48 h-48 block"
                />
              </div>

              <div className="text-[10px] text-brandTextSecondary leading-normal max-w-[240px]">
                <p className="font-bold text-brandTextPrimary">Scan to verify this bus duty assignment.</p>
                <p className="mt-1 font-medium">Secure verification token. Exposes no direct database credentials.</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* DELETE BUS CONFIRMATION MODAL */}
      {deleteModalOpen && busToDelete && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 text-brandTextPrimary shadow-2xl space-y-4">
            <div className="flex justify-between items-start">
              <div className="flex items-center gap-3">
                <div className="p-2.5 bg-brandRed/10 text-brandRed rounded-xl">
                  <Trash2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-brandNavy">Delete Bus?</h3>
                  <p className="text-[11px] text-brandTextSecondary">Confirm decommissioning vehicle from fleet</p>
                </div>
              </div>
              <button
                onClick={() => { setDeleteModalOpen(false); setBusToDelete(null); }}
                className="p-1 hover:bg-brandBg text-brandTextSecondary rounded-lg transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {deleteError && (
              <div className="p-3 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-xl text-xs font-bold">
                {deleteError}
              </div>
            )}

            <div className="p-4 bg-brandBg/60 border border-brandBorder rounded-2xl space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-brandTextSecondary font-medium">Bus:</span>
                <span className="font-bold text-brandNavy">{busToDelete.busNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brandTextSecondary font-medium">Model:</span>
                <span className="font-bold text-brandNavy">{busToDelete.model}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brandTextSecondary font-medium">Registration:</span>
                <span className="font-mono font-semibold text-brandTextPrimary">{busToDelete.registrationNumber || 'N/A'}</span>
              </div>
            </div>

            <p className="text-xs text-brandTextSecondary font-medium">
              This vehicle will be decommissioned from active service. Historical trip and audit records will be preserved.
            </p>

            <div className="flex gap-3 justify-end pt-2 border-t border-brandBorder">
              <button
                type="button"
                onClick={() => { setDeleteModalOpen(false); setBusToDelete(null); }}
                className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-all"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDeleteBus}
                disabled={deleteLoading}
                className="px-4 py-2 bg-brandRed hover:bg-brandRed/90 disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-md transition-all"
              >
                {deleteLoading ? 'Decommissioning...' : 'Delete Bus'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
