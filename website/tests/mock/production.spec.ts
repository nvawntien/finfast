import { expect, test } from '@playwright/test';

test('production does not register mock worker or serve its executable script', async ({ page }) => {
  await page.goto('/customer');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('status')).toHaveCount(0);
  expect(await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length)).toBe(0);
  const response = await page.request.get('/mockServiceWorker.js');
  expect(response.headers()['content-type']).not.toContain('javascript');
  expect(await response.text()).not.toContain('Mock Service Worker');
});
