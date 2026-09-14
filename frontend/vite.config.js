import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The build writes straight into the Spring Boot static resources, so the WAR
// serves the SPA from the same origin as the API and no CORS is involved in
// production. The Dockerfile builds this in its own Node stage and copies the
// output in; a plain `mvn package` outside Docker produces a backend-only WAR.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 3000,
    // Proxying keeps dev requests same-origin, so CORS never enters the picture
    // locally either. Point this at the EC2 host instead of localhost to
    // develop against the deployed API.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
