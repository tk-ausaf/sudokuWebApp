import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local dev only: proxies API calls to the Spring Boot backend on 8080 so the guest
// cookie and JWT bearer flow behave identically to production (same-origin browser view).
export default defineConfig({
  plugins: [react()],
  // sockjs-client (used by the multiplayer STOMP client) references Node's `global`,
  // which doesn't exist in the browser - map it to globalThis so the bundle loads.
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    proxy: {
      '/sudoku': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
      // Scoped to the API path (plural "games") so it doesn't also swallow the frontend's
      // own client-side route /multiplayer/game/:gameId (singular) - a prefix of just
      // '/multiplayer' would proxy that route straight to the backend's static build too,
      // serving hashed asset paths that don't exist on the dev server and leaving a blank page.
      '/multiplayer/games': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
});
