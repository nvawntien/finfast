import { expect, test } from '@playwright/test';

test('mock worker intercepts client requests before render and supports the CSRF flow', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/customer');
  await expect(page.getByRole('status')).toContainText('Chế độ Mock API');
  const result = await page.evaluate(async () => {
    const modulePath = '/src/api/index.ts';
    const { api } = await import(/* @vite-ignore */ modulePath);
    const login = await api.request('/auth/login', { method: 'POST', body: { email: 'customer@example.test', password: 'mock-only-password' } });
    const account = await api.request('/accounts/me');
    const transfer = await api.request('/transfers', { method: 'POST', body: { recipientAccountNumber: 'MOCK-ACCOUNT-002', amountVnd: '1' }, idempotencyKey: 'original-key' });
    await api.request('/auth/logout', { method: 'POST' });
    let logoutStatus;
    try { await api.request('/auth/session'); } catch (error) { logoutStatus = (error as { status: number }).status; }
    return { login, account, transfer, logoutStatus, storage: { ...localStorage } };
  });
  expect(result.login.data.role).toBe('CUSTOMER');
  expect(result.account.data.balanceVnd).toBe('9007199254740993');
  expect(result.transfer).toMatchObject({ status: 202, data: { outcome: 'UNKNOWN' } });
  expect(result.logoutStatus).toBe(401);
  expect(result.storage).toEqual({});
  expect(errors).toEqual([]);
});

test('unknown API cannot fall through to a real backend', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('status')).toBeVisible();
  const blocked = await page.evaluate(async () => {
    try { await fetch('/api/v1/not-mocked'); return false; } catch { return true; }
  });
  expect(blocked).toBe(true);
});
