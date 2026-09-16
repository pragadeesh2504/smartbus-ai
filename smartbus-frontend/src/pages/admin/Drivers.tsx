import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Search,
  Plus,
  Ban,
  ThumbsUp,
  ThumbsDown,
  Edit,
  Trash2,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  KeyRound,
  Eye,
  EyeOff
} from 'lucide-react';

interface DriverType {
  id: string;
  name: string;
  phone: string;
  email: string;
  licenseNumber: string;
  licenseExpiry: string;
  emergencyContact: string;
  status: string;
  approvalStatus: string;
  employeeId: string;
  averageRating: number;
}

export const Drivers: React.FC = () => {
  const [drivers, setDrivers] = useState<DriverType[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [search, setSearch] = useState('');
  const [approvalFilter, setApprovalFilter] = useState('');
  const [loading, setLoading] = useState(true);

  // Modals / forms state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [selectedDriver, setSelectedDriver] = useState<DriverType | null>(null);
  const [driverToDelete, setDriverToDelete] = useState<DriverType | null>(null);
  const [driverForPassword, setDriverForPassword] = useState<DriverType | null>(null);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [licenseNumber, setLicenseNumber] = useState('');
  const [licenseExpiry, setLicenseExpiry] = useState('');
  const [emergencyContact, setEmergencyContact] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchDrivers = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/admin/drivers', {
        params: { page, size, search, approvalStatus: approvalFilter }
      });
      if (res.data.success) {
        setDrivers(res.data.data.content);
        setTotalElements(res.data.data.totalElements);
        setTotalPages(res.data.data.totalPages);
      }
    } catch (e) {
      setError('Failed to fetch drivers list.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDrivers();
  }, [page, approvalFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchDrivers();
  };

  const openCreateModal = () => {
    setError('');
    setSuccess('');
    setName('');
    setEmail('');
    setPhone('');
    setLicenseNumber('');
    setLicenseExpiry('');
    setEmergencyContact('');
    setEmployeeId('');
    setCreateModalOpen(true);
  };

  const handleCreateDriver = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post('/api/admin/drivers', {
        name,
        email,
        phone,
        licenseNumber,
        licenseExpiry,
        emergencyContact,
        employeeId
      });
      if (res.data.success) {
        setSuccess('Driver registered successfully.');
        setCreateModalOpen(false);
        fetchDrivers();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to register driver.');
    }
  };

  const openEditModal = (driver: DriverType) => {
    setError('');
    setSuccess('');
    setSelectedDriver(driver);
    setName(driver.name);
    setEmail(driver.email);
    setPhone(driver.phone || '');
    setLicenseNumber(driver.licenseNumber);
    setLicenseExpiry(driver.licenseExpiry || '');
    setEmergencyContact(driver.emergencyContact || '');
    setEmployeeId(driver.employeeId || '');
    setEditModalOpen(true);
  };

  const handleEditDriver = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDriver) return;
    try {
      const res = await axios.put(`/api/admin/drivers/${selectedDriver.id}`, {
        name,
        email,
        phone,
        licenseNumber,
        licenseExpiry,
        emergencyContact,
        employeeId
      });
      if (res.data.success) {
        setSuccess('Driver profile updated successfully.');
        setEditModalOpen(false);
        fetchDrivers();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to update driver details.');
    }
  };

  const openDeleteModal = (driver: DriverType) => {
    setError('');
    setSuccess('');
    setDriverToDelete(driver);
    setDeleteModalOpen(true);
  };

  const handleDeleteDriver = async () => {
    if (!driverToDelete) return;
    try {
      const res = await axios.delete(`/api/admin/drivers/${driverToDelete.id}`);
      if (res.data.success) {
        setSuccess(`Driver ${driverToDelete.name} deleted successfully.`);
        setDeleteModalOpen(false);
        setDriverToDelete(null);
        fetchDrivers();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to delete driver.');
    }
  };

  const openSetPasswordModal = (driver: DriverType) => {
    setPasswordError('');
    setDriverForPassword(driver);
    setNewPassword('');
    setConfirmPassword('');
    setShowPassword(false);
    setPasswordModalOpen(true);
  };

  const handleSetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!driverForPassword) return;

    if (newPassword.length < 8) {
      setPasswordError('Password must be at least 8 characters long.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError('Passwords do not match.');
      return;
    }

    try {
      setPasswordLoading(true);
      setPasswordError('');
      const res = await axios.post(`/api/admin/drivers/${driverForPassword.id}/password`, {
        newPassword,
        confirmPassword
      });
      if (res.data.success) {
        setSuccess('Password updated successfully.');
        setPasswordModalOpen(false);
        setDriverForPassword(null);
      }
    } catch (e: any) {
      setPasswordError(e.response?.data?.message || 'Failed to update driver password.');
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleApproval = async (driverId: string, statusVal: string) => {
    try {
      const res = await axios.post(`/api/admin/drivers/${driverId}/approve`, null, {
        params: { approvalStatus: statusVal }
      });
      if (res.data.success) {
        setSuccess(`Driver registration status updated: ${statusVal}`);
        fetchDrivers();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to update approval status.');
    }
  };

  const handleToggleSuspension = async (driver: DriverType) => {
    try {
      const nextStatus = driver.status === 'SUSPENDED' ? 'AVAILABLE' : 'SUSPENDED';
      const res = await axios.post(`/api/admin/drivers/${driver.id}/suspend`, null, {
        params: { status: nextStatus }
      });
      if (res.data.success) {
        setSuccess(`Driver is now ${nextStatus}.`);
        fetchDrivers();
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to toggle suspension.');
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-brandNavy tracking-tight">Driver Crew Registry</h2>
          <p className="text-xs text-brandTextSecondary mt-0.5 font-medium">Verify driver profiles, manage credentials, and audit certifications</p>
        </div>
        <button
          onClick={openCreateModal}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md shadow-brandBlue/10 transition-all"
        >
          <Plus className="w-4 h-4" /> Register Driver
        </button>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs font-bold">
          {error}
        </div>
      )}

      {success && (
        <div className="p-4 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-2xl text-xs font-bold">
          {success}
        </div>
      )}

      {/* Filter / Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-brandBorder flex flex-col md:flex-row gap-4 justify-between items-center shadow-sm">
        <form onSubmit={handleSearchSubmit} className="relative w-full md:w-80">
          <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
            <Search className="w-4 h-4" />
          </span>
          <input
            type="text"
            placeholder="Search by name, email, employee ID..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-xs text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
          />
        </form>

        <div className="flex items-center gap-3 w-full md:w-auto">
          <select
            value={approvalFilter}
            onChange={(e) => { setApprovalFilter(e.target.value); setPage(0); }}
            className="w-full md:w-40 px-3 py-2 bg-white border border-brandBorder rounded-xl text-xs text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all font-semibold"
          >
            <option value="">All Approvals</option>
            <option value="APPROVED">Approved</option>
            <option value="PENDING">Pending</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
      </div>

      {/* Main Table */}
      <div className="bg-white rounded-2xl border border-brandBorder overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="bg-brandBg border-b border-brandBorder text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold">
                <th className="py-3.5 px-4">Driver Name</th>
                <th className="py-3.5 px-4">Employee ID</th>
                <th className="py-3.5 px-4">Contact</th>
                <th className="py-3.5 px-4">License Spec</th>
                <th className="py-3.5 px-4">Expiry</th>
                <th className="py-3.5 px-4">Rating</th>
                <th className="py-3.5 px-4">Duty Status</th>
                <th className="py-3.5 px-4">Verification</th>
                <th className="py-3.5 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={9} className="text-center py-12">
                    <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue mx-auto"></div>
                  </td>
                </tr>
              ) : drivers.length > 0 ? (
                drivers.map((driver) => (
                  <tr key={driver.id} className="border-b border-brandBorder last:border-0 hover:bg-brandBlue/5 transition-all text-brandTextPrimary font-medium">
                    <td className="py-3.5 px-4 font-bold text-brandNavy">{driver.name}</td>
                    <td className="py-3.5 px-4 font-bold text-brandTextSecondary">{driver.employeeId || 'N/A'}</td>
                    <td className="py-3.5 px-4">
                      <p className="font-semibold text-brandTextPrimary">{driver.email}</p>
                      <p className="text-[10px] text-brandTextSecondary mt-0.5">{driver.phone}</p>
                    </td>
                    <td className="py-3.5 px-4 font-mono font-semibold text-brandTextSecondary">{driver.licenseNumber}</td>
                    <td className="py-3.5 px-4 text-[10px] font-bold text-brandTextSecondary">{driver.licenseExpiry || 'N/A'}</td>
                    <td className="py-3.5 px-4 font-bold text-brandBlue">★ {driver.averageRating?.toFixed(1) || '0.0'}</td>
                    <td className="py-3.5 px-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        driver.status?.toUpperCase() === 'AVAILABLE' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' :
                        driver.status?.toUpperCase() === 'SUSPENDED' ? 'bg-brandRed/10 text-brandRed border border-brandRed/20' :
                        'bg-brandTextSecondary/10 text-brandTextSecondary border border-brandBorder'
                      }`}>
                        {driver.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        driver.approvalStatus?.toUpperCase() === 'APPROVED' ? 'bg-brandGreen/10 text-brandGreen border border-brandGreen/20' :
                        driver.approvalStatus?.toUpperCase() === 'REJECTED' ? 'bg-brandRed/10 text-brandRed border border-brandRed/20' :
                        'bg-brandAmber/10 text-brandAmber border border-brandAmber/20'
                      }`}>
                        {driver.approvalStatus}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right flex items-center justify-end gap-2.5">
                      <button
                        onClick={() => openEditModal(driver)}
                        className="p-1 hover:bg-brandBlue/15 text-brandTextSecondary hover:text-brandBlue rounded transition-all"
                        title="Edit Driver Profile"
                      >
                        <Edit className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => openSetPasswordModal(driver)}
                        className="p-1 hover:bg-brandAmber/15 text-brandTextSecondary hover:text-brandAmber rounded transition-all"
                        title="Set Driver Password"
                      >
                        <KeyRound className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => openDeleteModal(driver)}
                        className="p-1 hover:bg-brandRed/15 text-brandTextSecondary hover:text-brandRed rounded transition-all"
                        title="Delete Driver"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                      {driver.approvalStatus?.toUpperCase() === 'PENDING' && (
                        <>
                          <button
                            onClick={() => handleApproval(driver.id, 'APPROVED')}
                            className="p-1 hover:bg-brandGreen/15 text-brandTextSecondary hover:text-brandGreen rounded transition-all"
                            title="Approve Driver Documents"
                          >
                            <ThumbsUp className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleApproval(driver.id, 'REJECTED')}
                            className="p-1 hover:bg-brandRed/15 text-brandTextSecondary hover:text-brandRed rounded transition-all"
                            title="Reject Driver Documents"
                          >
                            <ThumbsDown className="w-4 h-4" />
                          </button>
                        </>
                      )}
                      <button
                        onClick={() => handleToggleSuspension(driver)}
                        className={`p-1 rounded transition-colors ${
                          driver.status === 'SUSPENDED'
                            ? 'hover:bg-brandGreen/15 text-brandGreen'
                            : 'hover:bg-brandRed/15 text-brandTextSecondary hover:text-brandRed'
                        }`}
                        title={driver.status === 'SUSPENDED' ? 'Lift Suspension' : 'Suspend Driver'}
                      >
                        <Ban className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={9} className="text-center py-10 text-brandTextSecondary font-medium">
                    No drivers found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="p-4 border-t border-brandBorder flex justify-between items-center text-brandTextSecondary text-xs">
            <span className="font-semibold">Showing {drivers.length} of {totalElements} drivers</span>
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
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4">Register New Driver Profile</h3>
            <form onSubmit={handleCreateDriver} className="space-y-3.5 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Driver Full Name</label>
                  <input type="text" placeholder="e.g. Kumar S" value={name} onChange={e => setName(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Employee ID</label>
                  <input type="text" placeholder="e.g. DRV-102" value={employeeId} onChange={e => setEmployeeId(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Email Address</label>
                  <input type="email" placeholder="kumar@smartbus.ai" value={email} onChange={e => setEmail(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Phone Number</label>
                  <input type="text" placeholder="e.g. 9876543210" value={phone} onChange={e => setPhone(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Commercial DL Number</label>
                  <input type="text" placeholder="e.g. TN0120230000000" value={licenseNumber} onChange={e => setLicenseNumber(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">License Expiry Date</label>
                  <input type="date" value={licenseExpiry} onChange={e => setLicenseExpiry(e.target.value)} required className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-semibold mb-1">Emergency Contact Info</label>
                <input type="text" placeholder="e.g. Spouse/Parent Phone" value={emergencyContact} onChange={e => setEmergencyContact(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all" />
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button type="button" onClick={() => setCreateModalOpen(false)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all">Save Driver</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EDIT MODAL */}
      {editModalOpen && selectedDriver && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <h3 className="text-sm font-bold text-brandNavy mb-4">Edit Driver Profile: {selectedDriver.name}</h3>
            <form onSubmit={handleEditDriver} className="space-y-3.5 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Driver Full Name</label>
                  <input type="text" value={name} onChange={e => setName(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Employee ID</label>
                  <input type="text" value={employeeId} onChange={e => setEmployeeId(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Email Address</label>
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Phone Number</label>
                  <input type="text" value={phone} onChange={e => setPhone(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">Commercial DL Number</label>
                  <input type="text" value={licenseNumber} onChange={e => setLicenseNumber(e.target.value)} required className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
                <div>
                  <label className="block text-brandTextPrimary font-semibold mb-1">License Expiry Date</label>
                  <input type="date" value={licenseExpiry} onChange={e => setLicenseExpiry(e.target.value)} className="w-full px-3 py-2 bg-white border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
                </div>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-semibold mb-1">Emergency Contact Info</label>
                <input type="text" value={emergencyContact} onChange={e => setEmergencyContact(e.target.value)} className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold" />
              </div>

              <div className="pt-2 pb-1 border-t border-brandBorder/60 flex items-center justify-between">
                <div>
                  <span className="font-semibold text-brandTextPrimary">Account Password</span>
                  <p className="text-[10px] text-brandTextSecondary">Set or reset driver temporary credentials</p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    const d = selectedDriver;
                    setEditModalOpen(false);
                    openSetPasswordModal(d);
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-brandAmber/10 border border-brandAmber/30 hover:bg-brandAmber/20 text-brandAmber rounded-xl text-xs font-bold transition-all"
                >
                  <KeyRound className="w-3.5 h-3.5" /> Set Password
                </button>
              </div>

              <div className="flex gap-3 justify-end pt-3">
                <button type="button" onClick={() => setEditModalOpen(false)} className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl font-bold shadow-md transition-all">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deleteModalOpen && driverToDelete && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-sm rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <div className="flex items-center gap-3 text-brandRed mb-3">
              <div className="p-2 bg-brandRed/10 rounded-xl">
                <AlertTriangle className="w-5 h-5 text-brandRed" />
              </div>
              <h3 className="text-sm font-bold text-brandNavy">Delete Driver Account</h3>
            </div>
            <p className="text-xs text-brandTextSecondary leading-relaxed mb-4">
              Are you sure you want to delete <strong className="text-brandNavy">{driverToDelete.name}</strong> ({driverToDelete.email})?
              The driver will be deactivated and soft-deleted. This action is blocked if the driver is currently assigned to an active trip or schedule.
            </p>
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                onClick={() => { setDeleteModalOpen(false); setDriverToDelete(null); }}
                className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl text-xs transition-all"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDeleteDriver}
                className="px-4 py-2 bg-brandRed hover:bg-brandRed/90 text-white rounded-xl font-bold text-xs shadow-md transition-all"
              >
                Confirm Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* SET PASSWORD MODAL */}
      {passwordModalOpen && driverForPassword && (
        <div className="fixed inset-0 bg-brandNavy/65 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-brandBorder w-full max-w-md rounded-3xl p-6 text-brandTextPrimary shadow-2xl">
            <div className="flex items-center gap-3 text-brandBlue mb-4">
              <div className="p-2.5 bg-brandBlue/10 rounded-xl">
                <KeyRound className="w-5 h-5 text-brandBlue" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-brandNavy">Set Driver Password</h3>
                <p className="text-[11px] text-brandTextSecondary font-medium">{driverForPassword.name} ({driverForPassword.email})</p>
              </div>
            </div>

            {passwordError && (
              <div className="p-3 mb-3 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-xl text-xs font-semibold">
                {passwordError}
              </div>
            )}

            <form onSubmit={handleSetPassword} className="space-y-3.5 text-xs">
              <div>
                <label className="block text-brandTextPrimary font-semibold mb-1">New Password</label>
                <div className="relative">
                  <input
                    type={showPassword ? "text" : "password"}
                    placeholder="Enter new password (min 8 characters)"
                    value={newPassword}
                    onChange={e => setNewPassword(e.target.value)}
                    required
                    minLength={8}
                    className="w-full px-3 py-2 pr-9 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-brandTextSecondary hover:text-brandNavy"
                  >
                    {showPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-brandTextPrimary font-semibold mb-1">Confirm Password</label>
                <div className="relative">
                  <input
                    type={showPassword ? "text" : "password"}
                    placeholder="Confirm new password"
                    value={confirmPassword}
                    onChange={e => setConfirmPassword(e.target.value)}
                    required
                    minLength={8}
                    className="w-full px-3 py-2 pr-9 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all font-semibold"
                  />
                </div>
              </div>

              <p className="text-[11px] text-brandTextSecondary leading-relaxed">
                Password must be at least 8 characters long. After saving, the driver can immediately log in with their registered email and this new password. Existing approval requirements (Approved + Active) remain enforced.
              </p>

              <div className="flex gap-3 justify-end pt-3">
                <button
                  type="button"
                  onClick={() => { setPasswordModalOpen(false); setDriverForPassword(null); }}
                  className="px-4 py-2 border border-brandBorder hover:bg-brandBg text-brandTextPrimary font-bold rounded-xl transition-all"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={passwordLoading}
                  className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 disabled:opacity-50 text-white rounded-xl font-bold shadow-md transition-all flex items-center gap-1.5"
                >
                  {passwordLoading ? 'Updating...' : 'Set Password'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
