import React, { useEffect, useState } from 'react';
import axios from 'axios';
import {
  Bell,
  Calendar,
  Check,
  Mail,
  MailOpen
} from 'lucide-react';

export const Notifications: React.FC = () => {
  const [notifications, setNotifications] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchNotifications = async () => {
    try {
      const res = await axios.get('/api/driver/notifications');
      setNotifications(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNotifications();
  }, []);

  const markAllAsRead = async () => {
    try {
      // Mark all read
      await Promise.all(
        notifications
          .filter((n) => !n.isRead)
          .map((n) => axios.post(`/api/driver/notifications/${n.id}/read`))
      );
      fetchNotifications();
    } catch (e) {
      console.error(e);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue mx-auto"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-brandBlue/5 border border-brandBlue/10 rounded-xl text-brandBlue">
            <Bell className="w-5 h-5" />
          </div>
          <h2 className="text-base font-bold text-brandNavy">Announcements</h2>
        </div>
        {notifications.some((n) => !n.isRead) && (
          <button
            onClick={markAllAsRead}
            className="text-xs text-brandBlue hover:text-brandBlue/90 font-extrabold flex items-center gap-1.5 transition-colors"
          >
            <Check className="w-4 h-4" />
            Mark all read
          </button>
        )}
      </div>

      <div className="space-y-4">
        {notifications.length > 0 ? (
          notifications.map((n) => (
            <div
              key={n.id}
              className={`bg-white border border-brandBorder rounded-3xl p-5 shadow-sm space-y-3 relative overflow-hidden transition-all text-brandTextPrimary ${
                !n.isRead ? 'border-l-4 border-l-brandBlue pl-4' : ''
              }`}
            >
              <div className="flex justify-between items-start">
                <h3 className={`text-sm font-bold text-brandNavy ${!n.isRead ? 'text-brandBlue' : 'text-brandTextPrimary'}`}>
                  {n.title}
                </h3>
                {!n.isRead ? (
                  <Mail className="w-4 h-4 text-brandBlue shrink-0 mt-0.5" />
                ) : (
                  <MailOpen className="w-4 h-4 text-brandTextSecondary/50 shrink-0 mt-0.5" />
                )}
              </div>

              <p className="text-xs text-brandTextSecondary leading-relaxed font-semibold">{n.message}</p>

              <div className="flex items-center gap-1.5 text-[10px] text-brandTextSecondary pt-2 border-t border-brandBorder font-bold">
                <Calendar className="w-3.5 h-3.5 text-brandTextSecondary/60" />
                <span>{new Date(n.createdAt).toLocaleString()}</span>
              </div>
            </div>
          ))
        ) : (
          <div className="bg-white border border-brandBorder rounded-3xl p-8 text-center text-brandTextSecondary font-bold">
            No notification announcements received yet.
          </div>
        )}
      </div>
    </div>
  );
};
