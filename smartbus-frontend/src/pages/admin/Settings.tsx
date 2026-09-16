import React, { useState } from 'react';
import {
  Sliders,
  Globe,
  Save,
  CheckCircle
} from 'lucide-react';

export const Settings: React.FC = () => {
  const [transitSystemName, setTransitSystemName] = useState('SmartBus AI College System');
  const [gpsUpdateInterval, setGpsUpdateInterval] = useState(5);
  const [geofencingRadius, setGeofencingRadius] = useState(200);
  const [retentionPeriodDays, setRetentionPeriodDays] = useState(90);
  const [success, setSuccess] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setSuccess(true);
    setTimeout(() => setSuccess(false), 3000);
  };

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header section */}
      <div>
        <h2 className="text-xl font-bold text-brandNavy tracking-tight">System Configuration Settings</h2>
        <p className="text-xs text-brandTextSecondary mt-0.5 font-medium">Configure global parameter limits, notification thresholds, and network policies</p>
      </div>

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
