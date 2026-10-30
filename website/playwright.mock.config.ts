import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/mock',
  workers: 1,
  forbidOnly: !!process.env.CI,
  use: { browserName: 'chromium', channel: process.env.PLAYWRIGHT_CHANNEL || undefined },
  projects: [
    { name: 'mock-browser', testMatch: ['mock.spec.ts', 'ux.spec.ts'], use: { baseURL: 'http://127.0.0.1:3001' } },
    { name: 'production', testMatch: 'production.spec.ts', use: { baseURL: 'http://127.0.0.1:3002' } },
  ],
  webServer: [
    { command: 'npm run dev:mock -- --port 3001', url: 'http://127.0.0.1:3001', reuseExistingServer: false },
    { command: 'npm run preview -- --port 3002', url: 'http://127.0.0.1:3002', reuseExistingServer: false },
  ],
});
