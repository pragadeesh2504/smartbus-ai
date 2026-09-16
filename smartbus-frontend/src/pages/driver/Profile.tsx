import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { useAuth } from '../../context/AuthContext';
import {
  User,
  Mail,
  Phone,
  FileText,
  Bus,
  LogOut
} from 'lucide-react';

export const Profile: React.FC = () => {
  const { logout } = useAuth();
  const [profile, setProfile] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  const fetchProfile = async () => {
    try {
      const res = await axios.get('/api/driver/dashboard');
      setProfile(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue mx-auto"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <div className="p-2 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
          <User className="w-5 h-5" />
        </div>
        <h2 className="text-base font-bold text-brandNavy">Driver Profile</h2>
      </div>

      <div className="bg-white border border-brandBorder rounded-3xl p-6 shadow-sm space-y-6 text-brandTextPrimary">
        <div className="flex flex-col items-center justify-center text-center space-y-3 pb-6 border-b border-brandBorder">
          <div className="w-20 h-20 bg-brandBlue/5 border-2 border-brandBlue/10 rounded-full flex items-center justify-center text-brandBlue">
            <User className="w-10 h-10" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-brandNavy">{profile?.driverName}</h3>
            <span className="inline-block mt-1.5 text-[10px] px-3 py-1 rounded-full bg-brandBlue/5 text-brandBlue border border-brandBlue/10 font-bold uppercase tracking-wider">
              Approved Driver
            </span>
          </div>
        </div>

        <div className="space-y-4">
          <div className="flex items-center gap-4 text-xs font-semibold">
            <Mail className="w-5 h-5 text-brandTextSecondary shrink-0" />
            <div>
              <p className="text-[10px] text-brandTextSecondary uppercase font-bold">Email Address</p>
              <p className="text-brandNavy mt-0.5 font-bold">driver@smartbus.ai</p>
            </div>
          </div>

          <div className="flex items-center gap-4 text-xs font-semibold">
            <Phone className="w-5 h-5 text-brandTextSecondary shrink-0" />
            <div>
              <p className="text-[10px] text-brandTextSecondary uppercase font-bold">Mobile Number</p>
              <p className="text-brandNavy mt-0.5 font-bold">9876543210</p>
            </div>
          </div>

          <div className="flex items-center gap-4 text-xs font-semibold">
            <FileText className="w-5 h-5 text-brandTextSecondary shrink-0" />
            <div>
              <p className="text-[10px] text-brandTextSecondary uppercase font-bold">License Number</p>
              <p className="text-brandNavy mt-0.5 font-bold font-mono">TN0120231234567</p>
            </div>
          </div>

          {profile?.busNumber && (
            <div className="flex items-center gap-4 text-xs font-semibold">
              <Bus className="w-5 h-5 text-brandTextSecondary shrink-0" />
              <div>
                <p className="text-[10px] text-brandTextSecondary uppercase font-bold">Assigned Bus</p>
                <p className="text-brandNavy mt-0.5 font-bold">{profile.busNumber}</p>
              </div>
            </div>
          )}
        </div>

        <button
          onClick={logout}
          className="w-full h-12 bg-brandBg hover:bg-brandRed/10 border border-brandBorder text-brandRed font-bold rounded-2xl flex items-center justify-center gap-2 transition-all text-xs"
        >
          <LogOut className="w-4 h-4" />
          Log Out Account
        </button>
      </div>
    </div>
  );
};
