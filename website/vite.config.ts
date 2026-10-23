import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

export default defineConfig(({ mode }) => ({
  plugins: [react(), {
    name: 'finfast-dev-mock-worker',
    apply: 'serve',
    configureServer(server) {
      if (mode !== 'mock') return;
      // Serve the installed official worker in dev only. No public/ copy enters dist/.
      const worker = readFileSync(createRequire(import.meta.url).resolve('msw/mockServiceWorker.js'));
      server.middlewares.use('/mockServiceWorker.js', (_request, response) => {
        response.setHeader('Content-Type', 'application/javascript');
        response.setHeader('Cache-Control', 'no-store');
        response.end(worker);
      });
    },
  }],
  server: { host: '127.0.0.1', port: 3000, strictPort: true },
  preview: { host: '127.0.0.1', port: 3000, strictPort: true },
}));
