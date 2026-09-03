import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * Opens a STOMP-over-SockJS connection to `/ws`, subscribes to `/topic/games/{gameId}`
 * for MultiplayerGameEvent broadcasts, and exposes a `sendMove` delta publisher.
 * A logged-in user's JWT is sent as a STOMP CONNECT header; guests rely on the
 * guest cookie riding along on the SockJS handshake's HTTP requests, same as the
 * `credentials: 'include'` fetches used elsewhere in the app.
 */
export function useMultiplayerSocket(gameId, token) {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!gameId) return undefined;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
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