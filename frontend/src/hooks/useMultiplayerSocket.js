import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Empty in local dev (Vite's proxy forwards /ws to localhost:8080). In production this must
// point directly at the backend's own origin - unlike the REST calls in client.js, a WebSocket
// upgrade can't be forwarded through a Vercel serverless function (those are plain HTTP
// request/response, not long-lived connections), so this one path stays genuinely cross-origin.
const BACKEND_ORIGIN = import.meta.env.VITE_BACKEND_ORIGIN || '';

/**
 * Opens a STOMP-over-SockJS connection to `${BACKEND_ORIGIN}/ws`, subscribes to
 * `/topic/games/{gameId}` for MultiplayerGameEvent broadcasts, and exposes a `sendMove` delta
 * publisher. A logged-in user's JWT is sent as a STOMP CONNECT header, so login is unaffected by
 * this connection being cross-origin. A GUEST's identity, however, is not - it still relies on
 * the guest cookie riding along on the SockJS handshake's HTTP requests, and this connection
 * bypasses the same-origin proxy that makes that cookie first-party for REST calls. A guest's
 * multiplayer session may therefore resolve to a different anonymous identity than their
 * single-player one in browsers that block third-party cookies (Safari today; others trending
 * that way) - a known gap, not yet fixed, tracked separately from the REST proxy work.
 */
export function useMultiplayerSocket(gameId, token) {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!gameId) return undefined;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${BACKEND_ORIGIN}/ws`),
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