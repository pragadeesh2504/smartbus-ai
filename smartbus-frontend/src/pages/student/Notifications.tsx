import React, { useEffect, useState } from 'react';
import axios from 'axios';
import {
  Bell,
  Check,
  CheckCheck,
  Clock,
  AlertTriangle,
  Bus as BusIcon,
  MapPin
} from 'lucide-react';

interface Notification {
  id: string;
  title: string;
  message: string;
  type: string; // INFO, ARRIVAL, DELAY, EMERGENCY
  read: boolean;
  createdAt: string;
}

export const Notifications: React.FC = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchNotifications = async () => {
    try {
      const res = await axios.get('/api/student/notifications');
      setNotifications(res.data);
      setError('');
    } catch (err) {
      console.error(err);
      setError('Failed to fetch notifications feed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNotifications();
  }, []);

  const handleMarkAsRead = async (id: string) => {
    try {
      await axios.patch(`/api/student/notifications/${id}/read`);
      // Update locally
      setNotifications(notifications.map(n => n.id === id ? { ...n, read: true } : n));
    } catch (err) {
      console.error('Error marking read:', err);
    }
  };

  const handleMarkAllAsRead = async () => {
    const unread = notifications.filter(n => !n.read);
    for (const n of unread) {
      try {
        await axios.patch(`/api/student/notifications/${n.id}/read`);
      } catch (err) {
        console.error(err);
      }
    }
    setNotifications(notifications.map(n => ({ ...n, read: true })));
  };

  // Helper to format timestamps relative to current time
  const formatTime = (timeStr: string) => {
    try {
      const date = new Date(timeStr);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      
      const diffMins = Math.floor(diffMs / (1000 * 60));
      if (diffMins < 1) return 'Just now';
      if (diffMins < 60) return `${diffMins}m ago`;
      
      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours}h ago`;
      
      return date.toLocaleDateString();
    } catch (e) {
      return '';
    }
  };

  const getNotificationIcon = (title: string, type: string) => {
    const lowerTitle = title.toLowerCase();
    if (lowerTitle.includes('approaching')) {
      return <MapPin className="w-5 h-5 text-brandBlue" />;
    }
    if (lowerTitle.includes('arrived')) {
      return <Check className="w-5 h-5 text-brandGreen" />;
    }
    if (type === 'EMERGENCY') {
      return <AlertTriangle className="w-5 h-5 text-brandRed animate-pulse" />;
    }
    return <BusIcon className="w-5 h-5 text-brandTextSecondary" />;
  };

  return (
    <div className="flex flex-col gap-6 pb-4">
      {/* Title & Actions */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-brandNavy flex items-center gap-2">
            <Bell className="w-5 h-5 text-brandBlue" /> Notifications Feed
          </h1>
          <p className="text-xs text-brandTextSecondary mt-1 font-medium">
            Personal and transit geofence alerts history.
          </p>
        </div>

        {notifications.some(n => !n.read) && (
          <button
            onClick={handleMarkAllAsRead}
            className="text-xs text-brandBlue hover:text-brandBlue/90 font-extrabold flex items-center gap-1.5 bg-brandBlue/5 px-3 py-1.5 rounded-xl border border-brandBlue/10 transition-all shadow-sm shrink-0"
          >
            <CheckCheck className="w-4 h-4" /> Read All
          </button>
        )}
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold">
          {error}
        </div>
      )}

      {/* Notifications list */}
      <div>
        {loading && notifications.length === 0 ? (
          <div className="flex justify-center py-8">
            <div className="w-6 h-6 border-2 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
          </div>
        ) : notifications.length > 0 ? (
          <div className="flex flex-col gap-3">
            {notifications.map((n) => (
              <div
                key={n.id}
                className={`bg-white border rounded-3xl p-4.5 flex gap-4 transition-all relative overflow-hidden text-brandTextPrimary shadow-sm ${
                  !n.read 
                    ? 'border-brandBlue/35 bg-white shadow-md shadow-brandBlue/5' 
                    : 'border-brandBorder'
                }`}
              >
                {/* Left Dot for unread */}
                {!n.read && (
                  <span className="absolute left-2.5 top-[22px] w-2 h-2 bg-brandBlue rounded-full animate-pulse"></span>
                )}

                {/* Icon Container */}
                <div className="p-3 bg-brandBg border border-brandBorder rounded-2xl flex items-center justify-center h-11 w-11 shrink-0">
                  {getNotificationIcon(n.title, n.type)}
                </div>

                {/* Details */}
                <div className="flex-1 flex flex-col gap-1 pr-6">
                  <div className="flex items-start justify-between">
                    <h3 className={`text-sm ${!n.read ? 'text-brandNavy font-extrabold' : 'text-brandTextSecondary font-bold'}`}>
                      {n.title}
                    </h3>
                    <span className="text-[10px] text-brandTextSecondary font-bold flex items-center gap-1 whitespace-nowrap pt-0.5">
                      <Clock className="w-3.5 h-3.5" /> {formatTime(n.createdAt)}
                    </span>
                  </div>
                  <p className="text-xs text-brandTextSecondary leading-relaxed font-semibold">
                    {n.message}
                  </p>
                </div>

                {/* Mark as read tick button */}
                {!n.read && (
                  <button
                    onClick={() => handleMarkAsRead(n.id)}
                    className="absolute right-3.5 bottom-3.5 p-1.5 bg-brandBg hover:bg-brandGreen/10 text-brandTextSecondary hover:text-brandGreen border border-brandBorder rounded-lg transition-colors shadow-sm"
                    title="Mark as Read"
                  >
                    <Check className="w-4 h-4" />
                  </button>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary flex flex-col items-center justify-center gap-2 shadow-sm font-semibold">
            <Bell className="w-10 h-10 text-brandTextSecondary/50 mb-1" />
            <p className="font-bold text-sm text-brandNavy">Notifications Feed is Empty</p>
            <p className="text-xs text-brandTextSecondary max-w-xs">
              When transit alerts or geofencing triggers occur, updates will be logged here.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
