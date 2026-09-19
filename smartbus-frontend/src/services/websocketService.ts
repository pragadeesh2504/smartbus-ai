/**
 * SEC-12: Centralized WebSocket service and utilities with exponential backoff,
 * connection lifecycle management, and automatic subscription restoration.
 */

export type ConnectionState = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED';

export const BACKOFF_DELAYS = [1000, 2000, 4000, 8000, 16000, 30000];

/**
 * Calculate reconnect delay with exponential backoff:
 * 1st retry -> 1s
 * 2nd retry -> 2s
 * 3rd retry -> 4s
 * 4th retry -> 8s
 * 5th retry -> 16s
 * 6th+ retry -> 30s
 */
export function getReconnectDelay(retryAttempt: number): number {
  if (retryAttempt < 0) return BACKOFF_DELAYS[0];
  const index = Math.min(retryAttempt, BACKOFF_DELAYS.length - 1);
  return BACKOFF_DELAYS[index];
}

/**
 * Resolve the WebSocket URL, handling Vite dev proxy (port 5173 -> 8080)
 * and production same-origin deployment.
 */
export function getWebSocketUrl(): string {
  const token = localStorage.getItem('accessToken');
  const collegeId = localStorage.getItem('collegeId');
  const params = new URLSearchParams();
  if (token) params.set('token', token);
  if (collegeId) params.set('collegeId', collegeId);
  const query = params.toString() ? `?${params.toString()}` : '';

  if (import.meta.env.VITE_WS_BASE_URL) {
    const base = import.meta.env.VITE_WS_BASE_URL.replace(/\/+$/, '');
    return `${base}/ws/live${query}`;
  }
  const loc = window.location;
  const wsProtocol = loc.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsHost = loc.host === 'localhost:5173' ? 'localhost:8080' : loc.host;
  return `${wsProtocol}//${wsHost}/ws/live${query}`;
}

export interface SmartWebSocketOptions {
  url?: string;
  onMessage?: (data: any, raw: MessageEvent) => void;
  onStateChange?: (state: ConnectionState, retryAttempt: number) => void;
  subscriptions?: (string | object)[];
  autoReconnect?: boolean;
}

/**
 * Managed WebSocket client with exponential backoff, subscription recovery,
 * duplicate connection prevention, and clean teardown.
 */
export class SmartWebSocketClient {
  private socket: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private retryAttempt = 0;
  private isIntentionallyClosed = false;
  private state: ConnectionState = 'DISCONNECTED';
  private subscriptions: Set<string> = new Set();
  private options: SmartWebSocketOptions;

  constructor(options: SmartWebSocketOptions = {}) {
    this.options = {
      autoReconnect: true,
      ...options
    };
    if (options.subscriptions) {
      options.subscriptions.forEach(s => this.addSubscription(s));
    }
  }

  public connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return; // Prevent duplicate connection
    }

    this.isIntentionallyClosed = false;
    this.clearReconnectTimer();
    this.setState(this.retryAttempt > 0 ? 'RECONNECTING' : 'CONNECTING');

    const url = this.options.url || getWebSocketUrl();
    try {
      this.socket = new WebSocket(url);

      this.socket.onopen = () => {
        this.retryAttempt = 0;
        this.setState('CONNECTED');
        this.restoreSubscriptions();
      };

      this.socket.onmessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data);
          this.options.onMessage?.(data, event);
        } catch {
          this.options.onMessage?.(event.data, event);
        }
      };

      this.socket.onclose = () => {
        this.socket = null;
        if (this.isIntentionallyClosed) {
          this.setState('DISCONNECTED');
          return;
        }

        if (this.options.autoReconnect) {
          this.scheduleReconnect();
        } else {
          this.setState('DISCONNECTED');
        }
      };

      this.socket.onerror = (e) => {
        // Log minimal error without exposing tokens or sensitive data
        console.warn('WebSocket connection error encountered');
      };
    } catch (err) {
      if (!this.isIntentionallyClosed && this.options.autoReconnect) {
        this.scheduleReconnect();
      } else {
        this.setState('DISCONNECTED');
      }
    }
  }

  private scheduleReconnect(): void {
    this.clearReconnectTimer();
    const delay = getReconnectDelay(this.retryAttempt);
    this.setState('RECONNECTING');

    this.reconnectTimer = window.setTimeout(() => {
      this.retryAttempt++;
      this.connect();
    }, delay);
  }

  public addSubscription(sub: string | object): void {
    let finalSub = sub;
    if (typeof sub === 'object' && sub !== null) {
      const token = localStorage.getItem('accessToken');
      const collegeId = localStorage.getItem('collegeId');
      finalSub = {
        ...sub,
        ...(token ? { token } : {}),
        ...(collegeId ? { collegeId } : {})
      };
    }
    const subStr = typeof finalSub === 'string' ? finalSub : JSON.stringify(finalSub);
    this.subscriptions.add(subStr);
    if (this.isConnected() && this.socket) {
      this.socket.send(subStr);
    }
  }

  private restoreSubscriptions(): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    this.subscriptions.forEach((subStr) => {
      try {
        this.socket?.send(subStr);
      } catch (err) {
        console.warn('Failed to restore subscription frame');
      }
    });
  }

  public send(data: string | object): boolean {
    if (this.isConnected() && this.socket) {
      const payload = typeof data === 'string' ? data : JSON.stringify(data);
      this.socket.send(payload);
      return true;
    }
    return false;
  }

  public isConnected(): boolean {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  public getState(): ConnectionState {
    return this.state;
  }

  public getRetryAttempt(): number {
    return this.retryAttempt;
  }

  private setState(newState: ConnectionState): void {
    this.state = newState;
    this.options.onStateChange?.(newState, this.retryAttempt);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  public disconnect(): void {
    this.isIntentionallyClosed = true;
    this.clearReconnectTimer();
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.setState('DISCONNECTED');
  }
}
