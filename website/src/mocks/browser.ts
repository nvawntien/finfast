import { setupWorker } from 'msw/browser';
import { createMockApi } from './handlers';

// Exported for browser test scenarios; this module is loaded only in dev mock mode.
export const worker = setupWorker(...createMockApi(window.location.origin).handlers);

export async function startMockApi() {
  if (!import.meta.env.DEV || import.meta.env.MODE !== 'mock') throw new Error('Mock API is development-only.');
  await worker.start({ quiet: true, onUnhandledFrame: 'bypass', serviceWorker: { url: '/mockServiceWorker.js' } });
  import.meta.hot?.dispose(() => { void worker.stop(); });
}
