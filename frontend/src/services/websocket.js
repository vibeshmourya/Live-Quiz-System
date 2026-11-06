import { Client } from '@stomp/stompjs';

function resolveWsBrokerUrl() {
  // 1. Explicit WS broker URL from environment
  const explicitWs = (import.meta.env.VITE_WS_BASE_URL || import.meta.env.VITE_WS_URL || '').trim();
  if (explicitWs) {
    const clean = explicitWs.replace(/\/+$/, '');
    const base = clean.endsWith('/ws-raw') ? clean : `${clean}/ws-raw`;
    // Attach ngrok bypass only if accessing via an ngrok tunnel
    if (base.includes('ngrok')) {
      const queryParam = 'ngrok-skip-browser-warning=true';
      return base.includes('?') ? `${base}&${queryParam}` : `${base}?${queryParam}`;
    }
    return base;
  }

  // 2. Derive WebSocket URL from VITE_API_BASE_URL if configured
  const apiBase = (import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '').trim();
  if (apiBase && /^https?:\/\//i.test(apiBase)) {
    try {
      const parsed = new URL(apiBase);
      const wsProto = parsed.protocol === 'https:' ? 'wss:' : 'ws:';
      const base = `${wsProto}//${parsed.host}/ws-raw`;
      if (parsed.host.includes('ngrok')) {
        return `${base}?ngrok-skip-browser-warning=true`;
      }
      return base;
    } catch (e) {
      // ignore
    }
  }

  // 3. Fallback to same-origin host (e.g. localhost with Vite dev proxy)
  const isSecure = typeof window !== 'undefined' && window.location.protocol === 'https:';
  const proto = isSecure ? 'wss://' : 'ws://';
  const host = typeof window !== 'undefined' && window.location.host ? window.location.host : 'localhost:8080';
  const base = `${proto}${host}/ws-raw`;
  if (host.includes('ngrok')) {
    return `${base}?ngrok-skip-browser-warning=true`;
  }
  return base;
}

export function connectSessionWebSocket(pin, onEvent, onStats, onHostEvent, onReconnect, candidateId, onCandidateEvent) {
  const brokerURL = resolveWsBrokerUrl();
  const isNgrok = brokerURL.includes('ngrok');
  let hasConnectedOnce = false;

  const stompClient = new Client({
    brokerURL: brokerURL,
    connectHeaders: isNgrok ? { 'ngrok-skip-browser-warning': 'true' } : {},
    reconnectDelay: 2500,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
  });

  stompClient.onConnect = () => {
    // If this is a re-connection, invoke onReconnect callback to sync state
    if (hasConnectedOnce) {
      if (onReconnect) {
        try {
          onReconnect();
        } catch (e) {
          console.warn('onReconnect error:', e);
        }
      }
    }
    hasConnectedOnce = true;

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

    // Authoritative host session state topic
    if (onHostEvent) {
      stompClient.subscribe(`/topic/session/${pin}/host`, (message) => {
        try {
          const data = JSON.parse(message.body);
          if (onHostEvent) onHostEvent(data);
        } catch (e) {
          console.error('Error parsing host state message:', e);
        }
      });
    }

    // Participant-specific verification event topic
    if (candidateId && onCandidateEvent) {
      stompClient.subscribe(`/topic/session/${pin}/candidate/${candidateId}`, (message) => {
        try {
          const data = JSON.parse(message.body);
          if (onCandidateEvent) onCandidateEvent(data);
        } catch (e) {
          console.error('Error parsing candidate event message:', e);
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
