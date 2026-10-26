import { Client } from '@stomp/stompjs';

function resolveWsBrokerUrl() {
  // 1. Explicit WS broker URL from environment
  const explicitWs = (import.meta.env.VITE_WS_BASE_URL || import.meta.env.VITE_WS_URL || '').trim();
  if (explicitWs) {
    const clean = explicitWs.replace(/\/+$/, '');
    return clean.endsWith('/ws-raw') ? clean : `${clean}/ws-raw`;
  }

  // 2. Derive WebSocket URL from VITE_API_BASE_URL if configured
  const apiBase = (import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '').trim();
  if (apiBase && /^https?:\/\//i.test(apiBase)) {
    try {
      const parsed = new URL(apiBase);
      const wsProto = parsed.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${wsProto}//${parsed.host}/ws-raw`;
    } catch (e) {
      // ignore
    }
  }

  // 3. Fallback to same-origin host (e.g. localhost with Vite dev proxy)
  const isSecure = typeof window !== 'undefined' && window.location.protocol === 'https:';
  const proto = isSecure ? 'wss://' : 'ws://';
  const host = typeof window !== 'undefined' && window.location.host ? window.location.host : 'localhost:8080';
  return `${proto}${host}/ws-raw`;
}

export function connectSessionWebSocket(pin, onEvent, onStats) {
  const brokerURL = resolveWsBrokerUrl();

  const stompClient = new Client({
    brokerURL: brokerURL,
    reconnectDelay: 3000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    debug: () => {},
  });

  stompClient.onConnect = () => {
    // General session topic for question changes, reveal, finish, joins
    stompClient.subscribe(`/topic/session/${pin}`, (message) => {
      try {
        const data = JSON.parse(message.body);
        if (onEvent) onEvent(data);
      } catch (e) {
        console.error('Error parsing session message:', e);
      }
    });

    // High frequency live stats topic for host bar chart
    if (onStats) {
      stompClient.subscribe(`/topic/session/${pin}/stats`, (message) => {
        try {
          const data = JSON.parse(message.body);
          if (onStats) onStats(data);
        } catch (e) {
          console.error('Error parsing stats message:', e);
        }
      });
    }
  };

  stompClient.activate();

  return () => {
    try {
      stompClient.deactivate();
    } catch (e) {
      console.warn('Error closing STOMP connection', e);
    }
  };
}
