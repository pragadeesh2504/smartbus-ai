import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Sliders,
  Globe,
  Save,
  CheckCircle,
  Building2,
  Copy,
  Check,
  Edit3,
  AlertCircle,
  X
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

interface CollegeData {
  name: string;
  collegeCode: string | null;
  status: string;
}

export const Settings: React.FC = () => {
  const { user, updateUser } = useAuth();
  const [collegeData, setCollegeData] = useState<CollegeData>({
    name: user?.collegeName || '',
    collegeCode: user?.collegeCode || null,
    status: 'ACTIVE'
  });
  const [transitSystemName, setTransitSystemName] = useState('SmartBus AI College System');
  const [gpsUpdateInterval, setGpsUpdateInterval] = useState(5);
  const [geofencingRadius, setGeofencingRadius] = useState(200);
  const [retentionPeriodDays, setRetentionPeriodDays] = useState(90);
  const [success, setSuccess] = useState(false);
  const [copied, setCopied] = useState(false);

  // College Code modal state
  const [showCodeModal, setShowCodeModal] = useState(false);
  const [newCollegeCode, setNewCollegeCode] = useState('');
  const [codeLoading, setCodeLoading] = useState(false);
  const [codeError, setCodeError] = useState('');
  const [codeSuccess, setCodeSuccess] = useState('');

  const fetchCollegeInfo = async () => {
    try {
      const res = await axios.get('/api/admin/college');
      if (res.data?.data) {
        setCollegeData({
          name: res.data.data.name,
          collegeCode: res.data.data.collegeCode,
          status: res.data.data.status
        });
        if (res.data.data.collegeCode && res.data.data.collegeCode !== user?.collegeCode) {
          updateUser({ collegeCode: res.data.data.collegeCode });
        }
      }
    } catch {
      // Fall back to context
    }
  };

  useEffect(() => {
    fetchCollegeInfo();
  }, []);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setSuccess(true);
    setTimeout(() => setSuccess(false), 3000);
  };

  const handleOpenCodeModal = () => {
    setNewCollegeCode(collegeData.collegeCode || '');
    setCodeError('');
    setCodeSuccess('');
    setShowCodeModal(true);
  };

  const handleUpdateCollegeCode = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newCollegeCode.trim().toUpperCase();
    if (!clean) {
      setCodeError('College code cannot be blank');
      return;
    }
    if (!/^[A-Z0-9_-]{2,20}$/.test(clean)) {
      setCodeError('College code must be between 2 and 20 alphanumeric characters (dashes and underscores allowed)');
      return;
    }

    setCodeLoading(true);
    setCodeError('');
    try {
      const res = await axios.put('/api/admin/college/code', { collegeCode: clean });
      const updatedCode = res.data?.data?.collegeCode || clean;
      setCollegeData(prev => ({ ...prev, collegeCode: updatedCode }));
      updateUser({ collegeCode: updatedCode });
      setCodeSuccess(`College Code updated to ${updatedCode}. New student registrations and logins must use this code.`);
      setTimeout(() => {
        setShowCodeModal(false);
      }, 1500);
    } catch (err: any) {
      setCodeError(err.response?.data?.message || 'Failed to update college code.');
    } finally {
      setCodeLoading(false);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header section */}
      <div>
        <h2 className="text-xl font-bold text-brandNavy tracking-tight">System Configuration Settings</h2>
        <p className="text-xs text-brandTextSecondary mt-0.5 font-medium">Configure global parameter limits, notification thresholds, and campus policies</p>
      </div>

      {/* College Information Card */}
      <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-bold text-brandNavy uppercase tracking-wider flex items-center gap-2">
            <Building2 className="w-4 h-4 text-brandBlue" /> College Information
          </h3>
          <button
            type="button"
            onClick={handleOpenCodeModal}
            className="px-3 py-1.5 bg-brandBg hover:bg-brandBorder/50 text-brandBlue border border-brandBorder rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors"
          >
            <Edit3 className="w-3.5 h-3.5" />
            <span>{collegeData.collegeCode ? 'Change College Code' : 'Set College Code'}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs pt-1">
          <div>
            <span className="text-brandTextSecondary block mb-1 font-medium">College Name:</span>
            <span className="font-bold text-brandNavy text-sm block">{collegeData.name}</span>
          </div>

          <div>
            <span className="text-brandTextSecondary block mb-1 font-medium">College Code:</span>
            <div className="flex items-center gap-2">
              {collegeData.collegeCode ? (
                <>
                  <span className="font-mono font-bold text-brandBlue bg-brandBlue/10 px-2.5 py-1 rounded-lg border border-brandBlue/20 text-sm">
                    {collegeData.collegeCode}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(collegeData.collegeCode!)}
                    className="p-1.5 bg-white hover:bg-brandBg border border-brandBorder text-brandBlue rounded-lg text-xs font-semibold flex items-center gap-1 transition-colors"
                    title="Copy College Code"
                  >
                    {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                  </button>
                </>
              ) : (
                <span className="italic text-amber-600 font-semibold text-xs">Not configured</span>
              )}
            </div>
          </div>

          <div>
            <span className="text-brandTextSecondary block mb-1 font-medium">Status:</span>
            <span
              className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold ${
                collegeData.status === 'ACTIVE'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                  : 'bg-rose-50 text-rose-700 border border-rose-200'
              }`}
            >
              {collegeData.status}
            </span>
          </div>
        </div>
      </div>

      {/* Change College Code Modal */}
      {showCodeModal && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-md p-6 space-y-4 relative animate-scale-up">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Building2 className="w-5 h-5 text-brandBlue" />
                <h3 className="text-base font-bold text-slate-900">
                  {collegeData.collegeCode ? 'Change College Code' : 'Set College Code'}
                </h3>
              </div>
              <button
                onClick={() => setShowCodeModal(false)}
                className="p-1 text-slate-400 hover:text-slate-600 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {codeError && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-xl text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{codeError}</span>
              </div>
            )}

            {codeSuccess && (
              <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-xl text-xs flex items-center gap-2">
                <CheckCircle className="w-4 h-4 shrink-0" />
                <span>{codeSuccess}</span>
              </div>
            )}

            <form onSubmit={handleUpdateCollegeCode} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  College Code (e.g. CIT, LOYOLA, SRM) *
                </label>
                <input
                  type="text"
                  value={newCollegeCode}
                  onChange={e => setNewCollegeCode(e.target.value.toUpperCase())}
                  placeholder="e.g. CIT"
                  required
                  maxLength={20}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono font-bold uppercase focus:outline-none focus:border-brandBlue focus:bg-white transition-all"
                />
                <p className="text-[11px] text-slate-500 mt-1">
                  Alphanumeric characters, 2-20 characters. Must be globally unique across all colleges.
                </p>
              </div>

              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-[11px] text-amber-900 space-y-1">
                <div className="font-bold flex items-center gap-1.5">
                  <AlertCircle className="w-3.5 h-3.5 text-amber-600" />
                  <span>Important Note on Code Changes</span>
                </div>
                <p>
                  Existing students remain safely linked to this college by institution ID. However, any new student registration or future student login will immediately require this new code.
                </p>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowCodeModal(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={codeLoading}
                  className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold disabled:opacity-60 transition-all"
                >
                  {codeLoading ? 'Saving...' : 'Save College Code'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {success && (
        <div className="p-4 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-2xl text-xs flex gap-2.5 items-center font-bold">
          <CheckCircle className="w-4 h-4 shrink-0" />
          <span>System configurations updated successfully.</span>
        </div>
      )}

      <form onSubmit={handleSave} className="space-y-6">
        {/* Section 1: General Transit Configuration */}
        <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
          <h3 className="text-xs font-bold text-brandNavy uppercase tracking-wider flex items-center gap-2">
            <Sliders className="w-4 h-4 text-brandBlue" /> General Transit Controls
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div>
              <label className="block text-brandTextPrimary font-semibold mb-1">Transit System Label</label>
              <input
                type="text"
                value={transitSystemName}
                onChange={e => setTransitSystemName(e.target.value)}
                className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
              />
            </div>
            <div>
              <label className="block text-brandTextPrimary font-semibold mb-1">GPS Stream Frequency (seconds)</label>
              <input
                type="number"
                value={gpsUpdateInterval}
                onChange={e => setGpsUpdateInterval(parseInt(e.target.value))}
                className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
              />
            </div>
          </div>
        </div>

        {/* Section 2: Safety & Geofencing Parameters */}
        <div className="bg-white border border-brandBorder rounded-2xl p-6 space-y-4 shadow-sm">
          <h3 className="text-xs font-bold text-brandNavy uppercase tracking-wider flex items-center gap-2">
            <Globe className="w-4 h-4 text-brandBlue" /> Geofencing & Alert Rules
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div>
              <label className="block text-brandTextPrimary font-semibold mb-1">Stop Geofence Radius (meters)</label>
              <input
                type="number"
                value={geofencingRadius}
                onChange={e => setGeofencingRadius(parseInt(e.target.value))}
                className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
              />
            </div>
            <div>
              <label className="block text-brandTextPrimary font-semibold mb-1">Telemetry Retention Period (days)</label>
              <input
                type="number"
                value={retentionPeriodDays}
                onChange={e => setRetentionPeriodDays(parseInt(e.target.value))}
                className="w-full px-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
              />
            </div>
          </div>
        </div>

        {/* Save button */}
        <div className="flex justify-end pt-3">
          <button
            type="submit"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold shadow-md shadow-brandBlue/10 transition-all animate-hover"
          >
            <Save className="w-4 h-4" /> Save System Properties
          </button>
        </div>
      </form>
    </div>
  );
};
