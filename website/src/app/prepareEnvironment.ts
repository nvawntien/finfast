/** Complete mock startup before any future screen can issue an API request. */
export async function prepareEnvironment(): Promise<boolean> {
  if (import.meta.env.DEV && import.meta.env.MODE === 'mock') {
    const { startMockApi } = await import('../mocks/browser');
    await startMockApi();
    return true;
  }
  // Remove only this app's old mock worker when switching away from mock mode.
  if ('serviceWorker' in navigator) {
    for (const registration of await navigator.serviceWorker.getRegistrations()) {
      const workers = [registration.active, registration.waiting, registration.installing];
      if (workers.some(worker => worker && new URL(worker.scriptURL).origin === location.origin
        && new URL(worker.scriptURL).pathname === '/mockServiceWorker.js')) await registration.unregister();
    }
  }
  return false;
}
