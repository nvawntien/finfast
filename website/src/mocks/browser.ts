import { setupWorker } from 'msw/browser';
import { createMockApi } from './handlers';

export async function startMockApi() {
  if (!import.meta.env.DEV || import.meta.env.MODE !== 'mock') throw new Error('Mock API is development-only.');
  const mock = createMockApi(window.location.origin);
  const worker = setupWorker(...mock.handlers);
  await worker.start({ quiet: true, onUnhandledFrame: 'bypass', serviceWorker: { url: '/mockServiceWorker.js' } });
  import.meta.hot?.dispose(() => { void worker.stop(); });
}
