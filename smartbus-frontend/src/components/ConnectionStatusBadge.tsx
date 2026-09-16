import React from 'react';
import { ConnectionState } from '../services/websocketService';

interface ConnectionStatusBadgeProps {
  state: ConnectionState;
  retryAttempt?: number;
  className?: string;
}

/**
 * SEC-12: Small, unobtrusive connection status indicator.
 * 🟢 Live (CONNECTED)
 * 🔴/🟡 Reconnecting (RECONNECTING / CONNECTING)
 * ⚪ Disconnected (DISCONNECTED)
 */
export const ConnectionStatusBadge: React.FC<ConnectionStatusBadgeProps> = ({
  state,
  retryAttempt = 0,
  className = ''
}) => {
  if (state === 'CONNECTED') {
    return (
      <span
        title="Live WebSocket stream active"
        className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/80 shadow-sm ${className}`}
      >
        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
        Live
      </span>
    );
  }

  if (state === 'RECONNECTING' || state === 'CONNECTING') {
    return (
      <span
        title={`Reconnecting to live telemetry stream (attempt ${retryAttempt + 1})`}
        className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200/80 shadow-sm ${className}`}
      >
        <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping"></span>
        Reconnecting{retryAttempt > 0 ? ` (${retryAttempt + 1})` : '...'}
      </span>
    );
  }

  return (
    <span
      title="WebSocket stream disconnected"
      className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-500 border border-slate-200 shadow-sm ${className}`}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
      Disconnected
    </span>
  );
};
