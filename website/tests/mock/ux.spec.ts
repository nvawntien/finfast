import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function login(page: Page, email = 'customer@example.test') {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: false }).fill(email);
  await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill('mock-only-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByTestId('account-number')).toBeVisible();
}
async function change(page: Page, action: 'reset' | 'releaseResponse' | 'confirmSession' | Parameters<typeof import('./scenarios').scenario>[0]) {
  await page.evaluate(async action => {
    const path = '/tests/mock/scenarios.ts';
    const scenarios = await import(/* @vite-ignore */ path);
    if (action === 'reset' || action === 'releaseResponse' || action === 'confirmSession') scenarios[action]();
    else scenarios.scenario(action);
  }, action);
}
async function accessible(page: Page) {
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await expect(page.getByRole('status').filter({ hasText: 'Chế độ Mock API' })).toBeVisible();
}
for (const [name, width, height] of [
  ['mobile', 360, 800], ['tablet', 768, 1024], ['desktop', 1440, 900],
  ['200% equivalent reflow from 1440x900', 720, 450], ['400% equivalent reflow from 1280x1024', 320, 256],
] as const) {
  test(`${name}: MSW Customer journey, reflow, keyboard and accessibility`, async ({ page }, info) => {
    await page.setViewportSize({ width, height }); await login(page);
    // Effective CSS viewport at browser zoom; native browser chrome zoom remains a manual check.
    await accessible(page);
    const navigation = page.getByRole('navigation', { name: 'Điều hướng khách hàng' });
    await navigation.getByRole('link', { name: 'Lịch sử' }).focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('link', { name: /Xem biên nhận MOCK-TX-001/ })).toBeVisible();
    await accessible(page);
    await page.getByRole('link', { name: /Xem biên nhận MOCK-TX-001/ }).focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('article')).toBeVisible(); await accessible(page);
    await navigation.getByRole('link', { name: 'Chuyển tiền' }).focus(); await page.keyboard.press('Enter');
    await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill('MOCK-ACCOUNT-002');
    await page.getByLabel('Số tiền VND (bắt buộc)').fill('200000');
    await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Kiểm tra thông tin chuyển tiền' })).toBeFocused();
    await accessible(page);
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Chỉnh sửa thông tin', exact: true })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeFocused(); await accessible(page);
    await page.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeFocused();
    await expect(page.getByRole('button', { name: 'Tạo yêu cầu mới' })).toHaveCount(0);
    await accessible(page); await page.screenshot({ path: info.outputPath('recovery.png'), fullPage: true });
  });
}

for (const fault of ['account500', 'account503'] as const) test(`MSW ${fault}: error and deferred manual retry preserve session`, async ({ page }) => {
  await login(page); await change(page, fault);
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Dịch vụ tài khoản đang gặp sự cố');
  await expect(page.getByTestId('account-number')).toHaveCount(0); await accessible(page);
  await change(page, 'accountPending');
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đang tải…', exact: true })).toBeDisabled();
  await change(page, 'releaseResponse'); await expect(page.getByTestId('account-number')).toBeVisible();
  await expect(page).toHaveURL(/\/customer$/);
  await change(page, fault);
  await page.getByRole('navigation', { name: 'Điều hướng khách hàng' }).getByRole('link', { name: 'Chuyển tiền' }).click();
  await expect(page.getByRole('alert')).toContainText('Dịch vụ tài khoản đang gặp sự cố');
  await expect(page.getByLabel('Số tiền VND (bắt buộc)')).toHaveCount(0);
  await change(page, 'reset'); await page.getByRole('button', { name: 'Thử tải lại', exact: true }).click();
  await expect(page.getByLabel('Số tiền VND (bắt buộc)')).toBeVisible();
});

test('MSW pending account cannot reappear during session revalidation; logout removes it', async ({ page }) => {
  await login(page); await change(page, 'accountPending');
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đang tải…', exact: true })).toBeDisabled();
  await change(page, 'sessionPending'); await page.evaluate(() => dispatchEvent(new Event('focus')));
  await expect(page.getByRole('status').filter({ hasText: 'Đang kiểm tra phiên' })).toBeVisible();
  await change(page, 'releaseResponse');
  await expect(page.getByTestId('account-number')).toHaveCount(0);
  await change(page, 'confirmSession'); await expect(page.getByTestId('account-number')).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/); await expect(page.getByTestId('account-number')).toHaveCount(0);
});

test('MSW reload requires authentication before restoring UNKNOWN recovery without a second transfer', async ({ page }) => {
  await login(page);
  await page.getByRole('navigation').getByRole('link', { name: 'Chuyển tiền' }).click();
  await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill('MOCK-ACCOUNT-002');
  await page.getByLabel('Số tiền VND (bắt buộc)').fill('200000');
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
  let commands = 0;
  page.on('request', request => { if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/transfers') commands++; });
  await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
  await page.reload(); await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText('MOCK-ACCOUNT-002', { exact: false })).toHaveCount(0);
  await page.getByLabel('Email', { exact: false }).fill('customer@example.test');
  await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill('mock-only-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('navigation').getByRole('link', { name: 'Chuyển tiền' }).click();
  await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
  await page.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
  expect(commands).toBe(1);
});

test('MSW history network/empty, receipt ownership and expired session fail closed', async ({ page }) => {
  await login(page); await change(page, 'historyNetwork');
  await page.getByRole('navigation').getByRole('link', { name: 'Lịch sử' }).click();
  await expect(page.getByRole('alert')).toContainText('Không thể kết nối');
  await change(page, 'historyEmpty'); await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa có giao dịch' })).toBeVisible(); await accessible(page);
  // A cannot see frozen B's receipt, even with a known reference.
  await page.goto('/customer/receipts/MOCK-FROZEN-TX-085');
  // Full reload resets this in-memory MSW session: route guard requires login again.
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email', { exact: false }).fill('customer@example.test');
  await page.getByLabel('Mật khẩu (bắt buộc)', { exact: true }).fill('mock-only-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.evaluate(() => { history.pushState(null, '', '/customer/receipts/MOCK-FROZEN-TX-085'); dispatchEvent(new PopStateEvent('popstate')); });
  await expect(page.getByRole('alert')).toContainText('Không tìm thấy biên nhận');
  await expect(page.getByRole('article')).toHaveCount(0);
  await change(page, 'sessionExpired'); await page.evaluate(() => dispatchEvent(new Event('focus')));
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('status').filter({ hasText: 'Phiên đã hết hạn' })).toBeVisible();
});
