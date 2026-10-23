import { afterEach, expect, it, vi } from 'vitest';
import { prepareEnvironment } from '../../src/app/prepareEnvironment';

const { startMockApi } = vi.hoisted(() => ({ startMockApi: vi.fn() }));
vi.mock('../../src/mocks/browser', () => ({ startMockApi }));
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.clearAllMocks(); });

it('production ignores mock mode and unregisters only its own old mock worker', async () => {
  vi.stubEnv('DEV', false);
  vi.stubEnv('MODE', 'mock');
  const own = vi.fn(); const unrelated = vi.fn();
  vi.stubGlobal('location', { origin: 'https://finfast.test' });
  vi.stubGlobal('navigator', { serviceWorker: { getRegistrations: async () => [
    { active: { scriptURL: 'https://finfast.test/mockServiceWorker.js' }, unregister: own },
    { active: { scriptURL: 'https://finfast.test/offline.js' }, unregister: unrelated },
  ] } });
  expect(await prepareEnvironment()).toBe(false);
  expect(startMockApi).not.toHaveBeenCalled();
  expect(own).toHaveBeenCalledTimes(1);
  expect(unrelated).not.toHaveBeenCalled();
});

it('ordinary development does not start mocks', async () => {
  vi.stubEnv('DEV', true); vi.stubEnv('MODE', 'development');
  vi.stubGlobal('navigator', {});
  expect(await prepareEnvironment()).toBe(false);
  expect(startMockApi).not.toHaveBeenCalled();
});

it('explicit dev mock mode awaits startup and propagates failure without real fallback', async () => {
  vi.stubEnv('DEV', true); vi.stubEnv('MODE', 'mock');
  startMockApi.mockResolvedValueOnce(undefined);
  expect(await prepareEnvironment()).toBe(true);
  startMockApi.mockRejectedValueOnce(new Error('worker failed'));
  await expect(prepareEnvironment()).rejects.toThrow('worker failed');
});
