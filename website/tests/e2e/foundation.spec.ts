import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

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
    page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()); });
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
    await expect(page).toHaveTitle(`${title} | FinFast`);
    await expect(page.getByRole('complementary', { name: 'Thông báo mô phỏng' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Màn hình chưa được triển khai' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
    expect(apiRequests).toEqual([]);
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
