import { useEffect, useState, useRef } from 'react';
import { SmartWebSocketClient, ConnectionState } from '../services/websocketService';

export interface LiveBusLocation {
  tripId: string;
  busNumber: string;
  latitude: number;
  longitude: number;
  speed: number;
  heading: number;
  routeName: string;
}

export interface UseWebSocketResult {
  busLocations: Record<string, LiveBusLocation>;
  connectionState: ConnectionState;
  retryAttempt: number;
}

/**
 * SEC-12: Upgraded useWebSocket hook with exponential backoff,
 * subscription recovery, and explicit connection state.
 */
export const useWebSocket = (enabled: boolean = true) => {
  const [busLocations, setBusLocations] = useState<Record<string, LiveBusLocation>>({});
  const [connectionState, setConnectionState] = useState<ConnectionState>('DISCONNECTED');
  const [retryAttempt, setRetryAttempt] = useState<number>(0);
  const clientRef = useRef<SmartWebSocketClient | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const client = new SmartWebSocketClient({
      subscriptions: [{ type: 'SUBSCRIBE_CLIENT' }],
      onStateChange: (state, retry) => {
        setConnectionState(state);
        setRetryAttempt(retry);
      },
      onMessage: (data) => {
        if (data && data.type === 'BUS_LOCATION_UPDATE') {
          setBusLocations((prev) => {
            const updated = {
              ...prev,
              [data.tripId]: {
                tripId: data.tripId,
                busNumber: data.busNumber,
                latitude: data.latitude,
                longitude: data.longitude,
                speed: data.speed,
                heading: data.heading,
                routeName: data.routeName,
              },
            };
            try {
              localStorage.setItem('offline_bus_cache', JSON.stringify(updated));
            } catch {
              // ignore storage errors
            }
            return updated;
          });
        }
      }
    });

    clientRef.current = client;
    client.connect();

    return () => {
      client.disconnect();
      clientRef.current = null;
    };
  }, [enabled]);

  // Load offline cache on initialization
  useEffect(() => {
    const cached = localStorage.getItem('offline_bus_cache');
    if (cached) {
      try {
        setBusLocations(JSON.parse(cached));
      } catch {
        // ignore parsing errors
      }
    }
  }, []);

  return busLocations;
};

/**
 * Dedicated hook for components needing explicit connection state + raw message stream
 */
export const useSmartWebSocket = (options: {
  enabled?: boolean;
  subscriptions?: (string | object)[];
  onMessage?: (data: any) => void;
}) => {
  const [connectionState, setConnectionState] = useState<ConnectionState>('DISCONNECTED');
  const [retryAttempt, setRetryAttempt] = useState<number>(0);
  const clientRef = useRef<SmartWebSocketClient | null>(null);

  useEffect(() => {
    if (options.enabled === false) return;

    const client = new SmartWebSocketClient({
      subscriptions: options.subscriptions || [{ type: 'SUBSCRIBE_CLIENT' }],
      onStateChange: (state, retry) => {
        setConnectionState(state);
        setRetryAttempt(retry);
      },
      onMessage: (data) => {
        options.onMessage?.(data);
      }
    });

    clientRef.current = client;
    client.connect();

    return () => {
      client.disconnect();
      clientRef.current = null;
    };
  }, [options.enabled, JSON.stringify(options.subscriptions)]);

  const send = (data: string | object) => {
    return clientRef.current?.send(data) ?? false;
  };

  return {
    connectionState,
    retryAttempt,
    send,
    isConnected: connectionState === 'CONNECTED'
  };
};
