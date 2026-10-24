import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockIdentity } from '../../src/mocks/fixtures';

async function backend(page: Page, options: { role?: 'ADMIN' | 'CUSTOMER'; failLogout?: boolean; unavailable?: boolean; loginGate?: Promise<void> } = {}) {
  let signedIn = false;
  const mutations: string[] = [];
  const loginEmails: string[] = [];
  let csrf = 0;
  await page.route('**/api/v1/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    const error = (status: number) => route.fulfill({ status, json: { code: 'TEST_ERROR', message: 'Synthetic error' } });
    if (options.unavailable) return route.abort();
    if (path.endsWith('/csrf')) { csrf++; return route.fulfill({ json: { token: `csrf-${csrf}` } }); }
    if (request.method() === 'POST') {
      expect(request.headers()['x-csrf-token']).toBe(`csrf-${csrf}`);
      expect(csrf).toBeGreaterThan(0); mutations.push(path);
    }
    if (path.endsWith('/session')) return signedIn ? route.fulfill({ json: { ...mockIdentity, role: options.role || 'CUSTOMER' } }) : error(401);
    if (path.endsWith('/register')) {
      const body = request.postDataJSON(); expect(Object.keys(body).sort()).toEqual(['displayName', 'email', 'password']);
      if (body.email === 'used@example.test') return error(409);
      return route.fulfill({ status: 201, json: { customerId: 'test', accountNumber: 'test', status: 'ACTIVE', currency: 'VND', balanceVnd: '0' } });
    }
    if (path.endsWith('/login')) {
      loginEmails.push(request.postDataJSON().email);
      if (options.loginGate) await options.loginGate;
      if (request.postDataJSON().password !== 'correct-password') return error(401);
      signedIn = true; return route.fulfill({ json: { ...mockIdentity, role: options.role || 'CUSTOMER' } });
    }
    if (path.endsWith('/logout')) {
      if (options.failLogout) return route.abort();
      signedIn = false; return route.fulfill({ json: { message: 'Signed out' } });
    }
    throw new Error(`Unexpected API: ${path}`);
  });
  return { mutations, loginEmails, expire: () => { signedIn = false; } };
}
async function credentials(page: Page, password = 'correct-password', email = 'user@example.test') {
  await page.getByLabel('Email', { exact: false }).fill(email);
  await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill(password);
}

test('registration validation, duplicate email, success without automatic login', async ({ page }, info) => {
  const api = await backend(page); await page.goto('/register');
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('alert')).toBeFocused(); expect(api.mutations).toEqual([]);
  await page.getByRole('alert').getByRole('link', { name: 'Nhập tên hiển thị.' }).click();
  await expect(page.getByLabel('Tên hiển thị', { exact: false })).toBeFocused();
  await page.getByLabel('Tên hiển thị', { exact: false }).fill('Test Customer');
  await credentials(page, 'correct-password', 'used@example.test');
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Email này đã được sử dụng');
  await credentials(page);
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng ký thành công' })).toBeVisible();
  expect(api.mutations.every(path => path.endsWith('/register'))).toBe(true);
  await page.getByRole('link', { name: 'Đến trang đăng nhập' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Đăng nhập');
  await page.screenshot({ path: info.outputPath('identity-login.png'), fullPage: true });
});

test('guard, wrong credentials, login, expiry, and POST logout with fresh CSRF', async ({ page }) => {
  const api = await backend(page); await page.goto('/customer/history');
  await expect(page).toHaveURL(/\/login$/);
  await credentials(page, 'wrong'); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu không đúng');
  await expect(page.getByLabel('Mật khẩu (bắt buộc)', { exact: true })).toHaveValue('');
  await credentials(page); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page).toHaveURL(/\/customer\/history$/);
  api.expire(); await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page).toHaveURL(/\/login$/); await expect(page.getByRole('status')).toContainText('Phiên đã hết hạn');
  await credentials(page); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(api.mutations.filter(path => path.endsWith('/logout'))).toHaveLength(1);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
});

test('ADMIN cannot access Customer pages', async ({ page }) => {
  await backend(page, { role: 'ADMIN' }); await page.goto('/customer');
  await credentials(page); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Không có quyền truy cập Customer Web');
  await expect(page.getByRole('navigation', { name: 'Điều hướng khách hàng' })).toHaveCount(0);
});

test('logout network failure never claims the server session was revoked', async ({ page }) => {
  await backend(page, { failLogout: true }); await page.goto('/login');
  await credentials(page); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Phiên trên máy chủ có thể vẫn còn hiệu lực');
  await expect(page.getByRole('heading', { name: 'Tổng quan tài khoản' })).toHaveCount(0);
});

test('keyboard, password visibility, a11y and 200% text reflow', async ({ page }) => {
  await backend(page); await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await page.keyboard.press('Tab'); await expect(page.getByRole('link', { name: 'Bỏ qua điều hướng' })).toBeFocused();
  await page.keyboard.press('Enter'); await expect(page.getByRole('main')).toBeFocused();
  await page.getByLabel('Email', { exact: false }).focus(); await page.keyboard.type('user@example.test');
  await page.keyboard.press('Tab'); await expect(page.getByLabel('Mật khẩu (bắt buộc)', { exact: true })).toBeFocused();
  await page.keyboard.type('password'); await page.keyboard.press('Tab'); await page.keyboard.press('Enter');
  await expect(page.getByLabel('Mật khẩu (bắt buộc)', { exact: true })).toHaveAttribute('type', 'text');
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('real API outage fails closed without enabling mock', async ({ page }) => {
  const api = await backend(page, { unavailable: true }); await page.goto('/customer');
  await expect(page.getByRole('heading', { name: 'Không thể xác minh phiên' })).toBeVisible();
  await expect(page.getByText('Chế độ Mock API', { exact: false })).toHaveCount(0);
  expect(api.mutations).toEqual([]);
  expect(await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length)).toBe(0);
});

test('pending submit is announced, prevents duplicate POST, and handles registration 422', async ({ page }) => {
  await backend(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  let calls = 0;
  await page.route('**/api/v1/auth/register', async route => {
    calls++; await pending;
    await route.fulfill({ status: 422, json: { code: 'INVALID_REQUEST', message: 'Synthetic invalid registration' } });
  });
  await page.goto('/register');
  await page.getByLabel('Tên hiển thị', { exact: false }).fill('Synthetic'); await credentials(page);
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đang xử lý…' })).toBeDisabled();
  await expect(page.getByRole('status')).toContainText('Đang gửi yêu cầu');
  await page.getByLabel('Email', { exact: false }).press('Enter');
  await expect.poll(() => calls).toBe(1); release();
  await expect(page.getByRole('alert')).toContainText('Thông tin chưa hợp lệ');
  expect(calls).toBe(1);
});

test('pending login survives register/login navigation and blocks a competing identity intent', async ({ page }) => {
  let release!: () => void;
  const api = await backend(page, { loginGate: new Promise<void>(resolve => { release = resolve; }) });
  try {
    await page.goto('/login'); await credentials(page, 'correct-password', 'a@example.test');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect.poll(() => api.loginEmails.length).toBe(1);
    for (const path of ['register', 'login']) {
      await page.locator(`header a[href="/${path}"]`).click(); await expect(page).toHaveURL(new RegExp(`/${path}$`));
      await expect(page.getByRole('status')).toContainText('Yêu cầu đăng nhập trước vẫn đang xử lý');
      await expect(page.locator('form')).toHaveCount(0);
      await expect(page.locator('#email')).toHaveCount(0); // Cannot enter/login B or register while A is in flight.
      await page.keyboard.press('Enter');
      await expect(page.getByRole('alert')).toHaveCount(0);
    }
    expect(api.loginEmails).toEqual(['a@example.test']);
    release(); await expect(page).toHaveURL(/\/customer$/);
    expect(api.mutations).toEqual(['/api/v1/auth/login']);
  } finally { release(); }
});

test('explicit logout waits for pending login, then sends exactly one POST logout', async ({ page }) => {
  let release!: () => void;
  const api = await backend(page, { loginGate: new Promise<void>(resolve => { release = resolve; }) });
  try {
    await page.goto('/login'); await credentials(page);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect.poll(() => api.loginEmails.length).toBe(1);
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Đang chờ yêu cầu đăng nhập hoàn tất để đăng xuất');
    await expect(page.getByRole('button', { name: 'Đang đăng xuất…' })).toBeDisabled();
    expect(api.mutations).toEqual(['/api/v1/auth/login']);
    release();
    await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
    await expect(page).toHaveURL(/\/login$/);
    expect(api.mutations).toEqual(['/api/v1/auth/login', '/api/v1/auth/logout']);
    await page.goto('/customer'); await expect(page).toHaveURL(/\/login$/);
  } finally { release(); }
});

test('UTF-8 byte validation blocks oversized passwords in both Identity forms', async ({ page }) => {
  const api = await backend(page);
  for (const path of ['register', 'login']) {
    await page.goto(`/${path}`);
    if (path === 'register') await page.getByLabel('Tên hiển thị', { exact: false }).fill('Synthetic');
    await credentials(page, `${'é'.repeat(36)}a`);
    await page.locator('button[type=submit]').click();
    await expect(page.getByRole('alert')).toContainText('72 byte UTF-8');
    await expect(page.getByRole('alert')).toBeFocused();
    await expect(page.locator('#password')).toHaveValue(`${'é'.repeat(36)}a`);
  }
  expect(api.mutations).toEqual([]);
  await page.goto('/register'); await page.getByLabel('Tên hiển thị', { exact: false }).fill('Synthetic');
  await credentials(page, 'é'.repeat(36)); await page.locator('button[type=submit]').click();
  await expect(page.getByRole('heading', { name: 'Đăng ký thành công' })).toBeVisible();
  expect(api.mutations).toEqual(['/api/v1/auth/register']);
});
