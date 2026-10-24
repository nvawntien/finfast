import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockIdentity, mockHistoryItem, mockReceipt } from '../../src/mocks/fixtures';
const deferred = () => { let resolve!: () => void; const promise = new Promise<void>(yes => { resolve = yes; }); return { promise, resolve }; };
async function setup(page: Page) {
  let signedIn = true;
  await page.route('**/api/v1/auth/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/csrf')) return route.fulfill({ json: { token: 'synthetic' } });
    if (path.endsWith('/logout')) { expect(route.request().method()).toBe('POST'); signedIn = false; return route.fulfill({ json: { message: 'Signed out' } }); }
    return route.fulfill({ status: signedIn ? 200 : 401, json: signedIn ? mockIdentity : { code: 'UNAUTHENTICATED', message: 'Expired' } });
  });
  await page.route('**/api/v1/accounts/me/history?*', route => route.fulfill({ json: { items: [], nextCursor: null } }));
  await page.route('**/api/v1/transactions/*/receipt', route => route.fulfill({ json: mockReceipt }));
}
test('opaque paging, keyboard navigation, exact money, receipt, axe and reflow', async ({ page }, info) => {
  await setup(page); const cursor = 'opaque + /?&=%'; const requests: string[] = [];
  await page.route('**/api/v1/accounts/me/history?*', route => {
    const token = new URL(route.request().url()).searchParams.get('cursor'); requests.push(token ?? 'FIRST');
    expect(route.request().method()).toBe('GET');
    expect(token === null || token === cursor).toBe(true);
    return route.fulfill({ json: { items: [{ ...mockHistoryItem, reference: token ? 'PAGE-TWO' : mockReceipt.reference, amountVnd: '9007199254740993' }], nextCursor: token ? null : cursor } });
  });
  await page.goto('/customer/history'); await expect(page.getByText('−9.007.199.254.740.993 ₫', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Trang tiếp' }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('link', { name: /PAGE-TWO/ })).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toBeFocused(); await expect(page.getByRole('button', { name: 'Trang tiếp' })).toBeDisabled();
  await page.getByRole('button', { name: 'Trang trước' }).click();
  await page.getByRole('link', { name: /Xem biên nhận MOCK-TX-001/ }).click();
  await expect(page.getByRole('article', { name: 'Biên nhận đã cam kết' })).toBeVisible();
  await expect(page.getByText('200.000 ₫', { exact: true })).toBeVisible(); await expect(page.getByText('Đã hoàn tất · COMPLETED')).toBeVisible();
  expect(requests).toContain(cursor);
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: info.outputPath('receipt.png'), fullPage: true });
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('link', { name: 'Về lịch sử giao dịch' }).click();
  await expect(page.getByRole('list', { name: 'Giao dịch đã cam kết' })).toBeVisible();
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('loading, empty history and retry after network failure', async ({ page }) => {
  await setup(page); const gate = deferred(); let broken = false;
  await page.route('**/api/v1/accounts/me/history?*', async route => { await gate.promise; return broken ? route.abort() : route.fulfill({ json: { items: [], nextCursor: null } }); });
  try {
    await page.goto('/customer/history'); await expect(page.getByRole('status')).toContainText('Đang tải dữ liệu');
    gate.resolve(); await expect(page.getByRole('heading', { name: 'Chưa có giao dịch' })).toBeVisible();
    broken = true; await page.getByRole('button', { name: 'Tải lại từ đầu' }).click(); await expect(page.getByRole('alert')).toContainText('Không thể kết nối');
    broken = false; await page.getByRole('button', { name: 'Thử lại' }).click(); await expect(page.getByRole('heading', { name: 'Chưa có giao dịch' })).toBeVisible();
  } finally { gate.resolve(); }
});
for (const status of [403, 422]) test(`history ${status} is safe and restart is available`, async ({ page }) => {
  await setup(page); await page.route('**/api/v1/accounts/me/history?*', route => route.fulfill({ status, json: { code: 'DENIED', message: 'Synthetic' } }));
  await page.goto('/customer/history'); await expect(page.getByRole('alert')).toContainText(status === 403 ? 'không có quyền' : 'trang đầu');
  await expect(page.getByRole('list', { name: 'Giao dịch đã cam kết' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tải lại từ đầu' })).toBeEnabled();
});
for (const status of [404, 403, 401]) test(`receipt ${status} never renders private facts`, async ({ page }) => {
  await setup(page); await page.route('**/api/v1/transactions/*/receipt', route => route.fulfill({ status, json: { code: 'UNAVAILABLE', message: 'Private server detail must not be shown' } }));
  await page.goto('/customer/receipts/PRIVATE');
  if (status === 401) await expect(page).toHaveURL(/\/login$/);
  else await expect(page.getByRole('alert')).toContainText(status === 404 ? 'Không tìm thấy biên nhận' : 'Phản hồi không hợp lệ');
  await expect(page.getByRole('article')).toHaveCount(0); await expect(page.getByText('Private server detail must not be shown')).toHaveCount(0);
});
for (const screen of ['history', 'receipts/MOCK-TX-001']) test(`${screen}: late GET cannot render during revalidation or after logout`, async ({ page }) => {
  await setup(page); const readGate = deferred(); const sessionGate = deferred(); let completed = false;
  const endpoint = screen === 'history' ? '**/api/v1/accounts/me/history?*' : '**/api/v1/transactions/*/receipt';
  await page.route(endpoint, async route => { await readGate.promise; await route.fulfill({ json: screen === 'history' ? { items: [mockHistoryItem], nextCursor: null } : mockReceipt }); completed = true; });
  try {
    await page.goto('/customer/' + screen); await expect(page.getByRole('status')).toContainText('Đang tải dữ liệu');
    await page.route('**/api/v1/auth/session', async route => { await sessionGate.promise; await route.fulfill({ json: { ...mockIdentity, userId: 'B' } }); });
    await page.evaluate(() => window.dispatchEvent(new Event('focus'))); await expect(page.getByRole('status')).toHaveText('Đang kiểm tra phiên…');
    readGate.resolve(); await expect.poll(() => completed).toBe(true);
    await expect(page.getByText(mockReceipt.reference, { exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(page).toHaveURL(/\/login$/);
    sessionGate.resolve(); await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await expect(page.getByRole('article')).toHaveCount(0); await expect(page.getByRole('list', { name: 'Giao dịch đã cam kết' })).toHaveCount(0);
  } finally { readGate.resolve(); sessionGate.resolve(); }
});
