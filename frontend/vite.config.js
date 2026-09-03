import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local dev only: proxies API calls to the Spring Boot backend on 8080 so the guest
// cookie and JWT bearer flow behave identically to production (same-origin browser view).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/sudoku': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
    },
  },
});
