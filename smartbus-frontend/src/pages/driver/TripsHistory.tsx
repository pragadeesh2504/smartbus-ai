import React, { useEffect, useState } from 'react';
import axios from 'axios';
import {
  History,
  Calendar,
  Bus,
  MapPin,
  Clock,
  Compass,
  CheckCircle,
  XCircle
} from 'lucide-react';

export const TripsHistory: React.FC = () => {
  const [trips, setTrips] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchHistory = async () => {
    try {
      const res = await axios.get('/api/driver/trips');
      // Sort: latest trips first
      const sorted = res.data.sort((a: any, b: any) => {
        if (!a.startTime || !b.startTime) return 0;
        return new Date(b.startTime).getTime() - new Date(a.startTime).getTime();
      });
      setTrips(sorted);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
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
          <History className="w-5 h-5" />
        </div>
        <h2 className="text-base font-bold text-brandNavy">Duty Trip History</h2>
      </div>

      <div className="space-y-4">
        {trips.length > 0 ? (
          trips.map((t) => (
            <div
              key={t.tripId}
              className="bg-white border border-brandBorder rounded-3xl p-5 shadow-sm space-y-4 relative overflow-hidden text-brandTextPrimary"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-xs text-brandTextSecondary font-bold">
                  <Calendar className="w-4 h-4 text-brandTextSecondary shrink-0" />
                  <span>
                    {t.startTime ? new Date(t.startTime).toLocaleDateString(undefined, {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric'
                    }) : 'N/A'}
                  </span>
                </div>
                <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-tight flex items-center gap-1.5 shadow-sm ${
                  t.status === 'COMPLETED'
                    ? 'bg-brandGreen/15 border border-brandGreen/25 text-brandGreen'
                    : 'bg-brandRed/15 border border-brandRed/25 text-brandRed'
                }`}>
                  {t.status === 'COMPLETED' ? <CheckCircle className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
                  {t.status}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center gap-2 text-xs font-bold text-brandNavy">
                  <Bus className="w-4 h-4 text-brandBlue shrink-0" />
                  <span>{t.busNumber}</span>
                </div>

                <div className="flex items-center gap-2 text-xs font-bold text-brandNavy">
                  <Compass className="w-4 h-4 text-brandBlue shrink-0" />
                  <span>
                    {t.distance ? `${t.distance.toFixed(2)} km` : '0.00 km'}
                  </span>
                </div>
              </div>

              <div className="flex items-start gap-2 text-xs text-brandTextPrimary font-bold">
                <MapPin className="w-4 h-4 text-brandBlue shrink-0 mt-0.5" />
                <span className="truncate max-w-[280px]">{t.routeName}</span>
              </div>

              <div className="border-t border-brandBorder pt-3 flex items-center justify-between text-[11px] text-brandTextSecondary font-bold">
                <div className="flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-brandTextSecondary shrink-0" />
                  <span>Duration: {t.duration || 0} mins</span>
                </div>
                <span>
                  {t.startTime ? new Date(t.startTime).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : ''}
                  {t.endTime ? ` - ${new Date(t.endTime).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}` : ''}
                </span>
              </div>
            </div>
          ))
        ) : (
          <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary font-bold">
            No operational trips completed yet.
          </div>
        )}
      </div>
    </div>
  );
};
