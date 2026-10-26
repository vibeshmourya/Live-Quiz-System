import { Client } from '@stomp/stompjs';

export function connectSessionWebSocket(pin, onEvent, onStats) {
  const isSecure = window.location.protocol === 'https:';
  const proto = isSecure ? 'wss://' : 'ws://';
  const brokerURL = `${proto}${window.location.host}/ws-raw`;

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
          onStats(data);
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
