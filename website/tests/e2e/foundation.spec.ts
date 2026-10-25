import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockAccount, mockIdentity } from '../../src/mocks/fixtures';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/accounts/me/history?*', route => route.fulfill({ json: { items: [], nextCursor: null } }));
  await page.route('**/api/v1/transactions/*/receipt', route => route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: 'Unavailable' } }));
  await page.route('**/api/v1/accounts/me', route => route.fulfill({ json: mockAccount }));
  await page.route('**/api/v1/auth/session', route => {
    const publicIdentity = /\/(login|register)$/.test(new URL(route.request().headers().referer || 'http://test/').pathname);
    return route.fulfill({ status: publicIdentity ? 401 : 200, json: publicIdentity ? { code: 'UNAUTHENTICATED', message: 'Test session required' } : mockIdentity });
  });
});

const routes = [
  ['/register', 'Tạo tài khoản'],
  ['/login', 'Đăng nhập'],
  ['/customer', 'Tổng quan tài khoản'],
  ['/customer/transfer', 'Chuyển tiền nội bộ'],
  ['/customer/history', 'Lịch sử giao dịch'],
  ['/customer/receipts/phase-1-placeholder', 'Biên nhận giao dịch'],
  ['/unknown-route', '404 — Không tìm thấy trang'],
] as const;

for (const [path, title] of routes) {
  test(`direct route ${path}: placeholder, semantics and reflow`, async ({ page }, testInfo) => {
    const apiRequests: string[] = [];
    const errors: string[] = [];
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url()); });
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
    await expect(page).toHaveTitle(`${title} | FinFast`);
    await expect(page.getByRole('complementary', { name: 'Thông báo mô phỏng' })).toBeVisible();
    if (path === '/unknown-route') await expect(page.getByRole('heading', { name: 'Màn hình chưa được triển khai' })).toBeVisible();
    if (path === '/customer') await expect(page.getByTestId('account-number')).toHaveText(mockAccount.accountNumber);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
    expect(apiRequests.every(url => url.endsWith('/api/v1/auth/session') || ((path === '/customer' || path === '/customer/transfer') && url.endsWith('/api/v1/accounts/me'))
      || (path === '/customer/history' && url.includes('/api/v1/accounts/me/history?'))
      || (path.startsWith('/customer/receipts/') && url.endsWith('/receipt')))).toBe(true);
    expect(errors).toEqual([]);
    if (path === '/customer') {
      await page.screenshot({ path: testInfo.outputPath('overview.png'), fullPage: true });
    }
  });
}

test('keyboard skip link, active route, focus and browser back', async ({ page }) => {
  await page.goto('/customer');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Bỏ qua điều hướng' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  const transfer = page.getByRole('navigation', { name: 'Điều hướng khách hàng' }).getByRole('link', { name: 'Chuyển tiền' });
  await transfer.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/customer\/transfer$/);
  await expect(transfer).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('main')).toBeFocused();
  await page.goBack();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tổng quan tài khoản');
});

test('root redirect and reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page).toHaveURL(/\/customer$/);
  const link = page.getByRole('navigation', { name: 'Điều hướng khách hàng' }).getByRole('link').first();
  expect(await link.evaluate(element => getComputedStyle(element).transitionDuration)).toBe('0s');
});

test('200% text scaling reflows without horizontal overflow', async ({ page }) => {
  await page.goto('/customer');
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
