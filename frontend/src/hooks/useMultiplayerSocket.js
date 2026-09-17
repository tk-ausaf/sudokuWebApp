import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getGuestId } from '../utils/guestIdentity.js';

// Empty in local dev (Vite's proxy forwards /ws to localhost:8080). In production this must
// point directly at the backend's own origin - unlike the REST calls in client.js, a WebSocket
// upgrade can't be forwarded through a Vercel serverless function (those are plain HTTP
// request/response, not long-lived connections), so this one path stays genuinely cross-origin.
const BACKEND_ORIGIN = import.meta.env.VITE_BACKEND_ORIGIN || '';

/**
 * Opens a STOMP-over-SockJS connection to `${BACKEND_ORIGIN}/ws`, subscribes to
 * `/topic/games/{gameId}` for MultiplayerGameEvent broadcasts, and exposes a `sendMove` delta
 * publisher. A logged-in user's JWT is sent as a STOMP CONNECT header. A guest's id instead rides
 * as a `guestId` query parameter on the connection URL, not a header - a browser's raw WebSocket
 * handshake can't carry custom headers at all, so a header (the way REST calls in client.js do
 * it) isn't an option here; a query parameter is, and it's read the same way server-side (see
 * GuestHandshakeInterceptor).
 */
export function useMultiplayerSocket(gameId, token) {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!gameId) return undefined;

    const wsUrl = `${BACKEND_ORIGIN}/ws?guestId=${encodeURIComponent(getGuestId())}`;
    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/games/${gameId}`, (message) => {
          try {
            setLastEvent(JSON.parse(message.body));
          } catch {
            // ignore malformed frame
          }
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      setConnected(false);
      client.deactivate();
      clientRef.current = null;
    };
  }, [gameId, token]);

  function sendMove(row, col, value) {
    const client = clientRef.current;
    if (!client || !client.connected) return;
    client.publish({
      destination: `/app/games/${gameId}/move`,
      body: JSON.stringify({ row, col, value }),
    });
  }

  return { connected, lastEvent, sendMove };
}