import { getToken } from './storage';

// 定义 WebSocket 消息格式
export interface WebSocketMessage {
  type: string;
  payload: any;
  timestamp?: string;
  requestId?: string;
}

/**
 * 可重连、带心跳的 WebSocket 客户端
 */
export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private messageHandlers: Map<string, ((payload: any) => void)[]> = new Map();
  private sessionIdSupplier?: () => number | null;

  /**
   * url: websocket path, e.g. '/ws/customer'
   * sessionIdSupplier: optional function that returns current sessionId to include in handshake
   */
  constructor(url: string, sessionIdSupplier?: () => number | null) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.url = `${protocol}//${window.location.host}/api${url}`;
    this.sessionIdSupplier = sessionIdSupplier;
    this.connect();
  }

  private connect() {
    if (this.ws) {
      this.ws.close();
    }

    const token = getToken();
    if (!token) {
      console.warn('[WebSocket] No token found, connection aborted.');
      return;
    }

    // 如果有 sessionIdSupplier，则附加 sessionId 到握手参数，后端需要 sessionId 验证
    const params = new URLSearchParams();
    params.set('token', token);
    try {
      const sessionId = this.sessionIdSupplier ? this.sessionIdSupplier() : null;
      if (sessionId) {
        params.set('sessionId', String(sessionId));
      }
    } catch (e) {
      // 如果 sessionId 函数抛错，不应阻塞连接
      console.warn('[WebSocket] 获取 sessionId 时出错', e);
    }

    const fullUrl = `${this.url}?${params.toString()}`;
    this.ws = new WebSocket(fullUrl);

    this.ws.onopen = () => {
      console.log('[WebSocket] Connection established.');
      this.clearReconnectTimer();
      this.startHeartbeat();
    };

    this.ws.onmessage = (event) => {
      try {
        const message: WebSocketMessage = JSON.parse(event.data);
        this.handleMessage(message);
      } catch (error) {
        console.error('[WebSocket] Failed to parse message:', event.data);
      }
    };

    this.ws.onerror = (error) => {
      console.error('[WebSocket] Connection error:', error);
    };

    this.ws.onclose = (event) => {
      console.log(`[WebSocket] Connection closed: ${event.code}`);
      this.stopHeartbeat();
      // Don't reconnect automatically on normal closure
      if (event.code !== 1000) {
        this.scheduleReconnect();
      }
    };
  }

  private handleMessage(message: WebSocketMessage) {
    const handlers = this.messageHandlers.get(message.type);
    if (handlers) {
      handlers.forEach(handler => handler(message.payload));
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.send('PING', {});
    }, 30000); // 30-second heartbeat
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      console.log('[WebSocket] Reconnecting...');
      this.connect();
    }, 5000); // Reconnect after 5 seconds
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  public send(type: string, payload: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message: WebSocketMessage = {
        type,
        payload,
        timestamp: new Date().toISOString()
      };
      this.ws.send(JSON.stringify(message));
    } else {
      console.warn('[WebSocket] Connection not open. Message not sent:', { type, payload });
    }
  }

  public on(type: string, handler: (payload: any) => void) {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, []);
    }
    this.messageHandlers.get(type)!.push(handler);
  }

  public off(type: string, handler: (payload: any) => void) {
    const handlers = this.messageHandlers.get(type);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    }
  }

  public disconnect() {
    this.clearReconnectTimer();
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.onclose = null; // Prevent reconnection on manual disconnect
      this.ws.close(1000, 'Manual disconnect');
      this.ws = null;
    }
    console.log('[WebSocket] Disconnected manually.');
  }
}





