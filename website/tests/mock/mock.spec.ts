import { expect, test } from '@playwright/test';

test('mock worker intercepts client requests before render and supports the CSRF flow', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/customer');
  await expect(page.getByRole('status').filter({ hasText: 'Chế độ Mock API' })).toBeVisible();
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
  await expect(page.getByRole('status').filter({ hasText: 'Chế độ Mock API' })).toBeVisible();
  const blocked = await page.evaluate(async () => {
    try { await fetch('/api/v1/not-mocked'); return false; } catch { return true; }
  });
  expect(blocked).toBe(true);
});

test('Identity UI registers, separately logs in and logs out through the real mock worker', async ({ page }) => {
  await page.goto('/register');
  await expect(page.getByRole('status').filter({ hasText: 'Chế độ Mock API' })).toBeVisible();
  await page.getByLabel('Tên hiển thị', { exact: false }).fill('Synthetic Customer');
  await page.getByLabel('Email', { exact: false }).fill('new-browser@example.test');
  await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill('synthetic-password');
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng ký thành công' })).toBeVisible();
  await page.getByRole('link', { name: 'Đến trang đăng nhập' }).click();
  await page.getByLabel('Email', { exact: false }).fill('new-browser@example.test');
  await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill('synthetic-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tổng quan tài khoản' })).toBeVisible();
  await expect(page.getByTestId('available-balance')).toHaveText('0 ₫');
  await expect(page.getByTestId('account-balance')).toHaveText('0 ₫');
  await expect(page.getByTestId('account-number')).toHaveText(/^MOCK-REGISTERED-ACCOUNT-/);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
});

for (const [email, status, balance] of [
  ['customer@example.test', 'Đang hoạt động · ACTIVE', '9.007.199.254.740.993 ₫'],
  ['frozen@example.test', 'Bị phong tỏa · FROZEN', '250.000 ₫'],
]) {
  test(`Dashboard uses the mock principal fixture for ${email}`, async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email', { exact: false }).fill(email!);
    await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill('mock-only-password');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.getByTestId('available-balance')).toHaveText(balance!);
    await expect(page.getByText(status!, { exact: true })).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: 'Chế độ Mock API' })).toBeVisible();
    await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
    await expect(page.getByTestId('available-balance')).toHaveText(balance!);
  });
}
