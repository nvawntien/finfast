import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockAccount, mockIdentity } from '../../src/mocks/fixtures';
const deferred = () => { let resolve!: () => void; const promise = new Promise<void>(yes => { resolve = yes; }); return { promise, resolve }; };
async function setup(page: Page, account = mockAccount) {
  const forbidden: string[] = []; let current = { ...mockIdentity };
  await page.route('**/api/v1/**', route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    if (request.method() !== 'GET' || !['/api/v1/auth/session', '/api/v1/accounts/me'].includes(path)) {
      forbidden.push(request.method() + ' ' + path); return route.abort();
    }
    return route.fulfill({ json: path.endsWith('/session') ? current : account });
  });
  return { forbidden, setPrincipal: () => { current = { ...mockIdentity, userId: 'B' }; } };
}
async function fill(page: Page, amount = '200000') {
  await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill('DESTINATION');
  await page.getByLabel('Số tiền VND (bắt buộc)').fill(amount);
}
test('validation, keyboard, exact confirmation, editing, axe/reflow and no command', async ({ page }, info) => {
  const api = await setup(page); await page.goto('/customer/transfer');
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
  await expect(page.getByRole('alert')).toBeFocused();
  await page.getByRole('alert').getByRole('link', { name: 'Nhập số tài khoản nhận.' }).click();
  await expect(page.getByLabel('Số tài khoản nhận (bắt buộc)')).toBeFocused();
  await fill(page, '1.5'); await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
  await expect(page.getByLabel('Số tiền VND (bắt buộc)')).toHaveAttribute('aria-invalid', 'true');
  await fill(page, '9007199254740993');
  await page.getByRole('checkbox', { name: /Kèm ghi chú/ }).check(); await page.getByLabel('Ghi chú', { exact: true }).fill('  Nội dung\n💚  ');
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Kiểm tra thông tin chuyển tiền' })).toBeFocused();
  await expect(page.getByRole('definition').filter({ hasText: '9.007.199.254.740.993 ₫' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true })).toBeEnabled();
  await expect(page.getByText('Chỉ chuyển tiền mô phỏng.', { exact: true })).toBeVisible();
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: info.outputPath('transfer-confirmation.png'), fullPage: true });
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Chỉnh sửa thông tin' }).click();
  await expect(page.getByLabel('Số tài khoản nhận (bắt buộc)')).toBeFocused();
  await expect(page.getByLabel('Ghi chú', { exact: true })).toHaveValue('  Nội dung\n💚  ');
  await page.getByRole('checkbox', { name: /Kèm ghi chú/ }).uncheck();
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click(); await expect(page.getByText('Không kèm ghi chú', { exact: true })).toBeVisible();
  expect(api.forbidden).toEqual([]); expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
});
test('loading and network error block preview, GET retry restores form', async ({ page }) => {
  const api = await setup(page); const gate = deferred(); let failed = true;
  await page.route('**/api/v1/accounts/me', async route => { await gate.promise; return failed ? route.abort() : route.fulfill({ json: mockAccount }); });
  try {
    await page.goto('/customer/transfer'); await expect(page.getByRole('status')).toContainText('Đang tải tài khoản nguồn');
    await expect(page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true })).toHaveCount(0);
    gate.resolve(); await expect(page.getByRole('alert')).toContainText('Không thể kết nối');
    failed = false; await page.getByRole('button', { name: 'Thử tải lại' }).click(); await expect(page.getByLabel('Số tài khoản nhận (bắt buộc)')).toBeVisible();
    expect(api.forbidden).toEqual([]);
  } finally { gate.resolve(); }
});
test('frozen blocks form but keeps read links', async ({ page }) => {
  const api = await setup(page, { ...mockAccount, status: 'FROZEN' }); await page.goto('/customer/transfer');
  await expect(page.getByRole('heading', { name: 'Tài khoản đang bị phong tỏa' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Xem lịch sử', exact: true })).toHaveAttribute('href', '/customer/history');
  await expect(page.getByLabel('Số tiền VND (bắt buộc)')).toHaveCount(0); expect(api.forbidden).toEqual([]);
});
test('self-transfer and insufficient balance remain validation errors, not financial outcomes', async ({ page }) => {
  await setup(page, { ...mockAccount, balanceVnd: '9', availableBalanceVnd: '9' }); await page.goto('/customer/transfer');
  await fill(page, '10'); await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill(mockAccount.accountNumber);
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('chính tài khoản'); await expect(page.getByRole('alert')).toContainText('vượt số dư');
  await expect(page.getByRole('heading', { name: 'Kiểm tra thông tin chuyển tiền' })).toHaveCount(0);
});
test('revalidation immediately hides confirmation and clears draft for another principal', async ({ page }) => {
  const api = await setup(page); await page.goto('/customer/transfer'); await fill(page);
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Kiểm tra thông tin chuyển tiền' })).toBeVisible();
  const gate = deferred();
  await page.route('**/api/v1/auth/session', async route => { await gate.promise; await route.fulfill({ json: { ...mockIdentity, userId: 'B' } }); });
  try {
    await page.evaluate(() => window.dispatchEvent(new Event('focus'))); await expect(page.getByRole('status')).toHaveText('Đang kiểm tra phiên…');
    await expect(page.getByText('DESTINATION', { exact: true })).toHaveCount(0);
    gate.resolve(); await expect(page.getByLabel('Số tài khoản nhận (bắt buộc)')).toHaveValue('');
    await expect(page.getByLabel('Số tiền VND (bắt buộc)')).toHaveValue(''); expect(api.forbidden).toEqual([]);
  } finally { gate.resolve(); }
});
