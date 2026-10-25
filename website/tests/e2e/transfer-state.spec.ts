import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockAccount, mockIdentity } from '../../src/mocks/fixtures';
const gate = () => { let resolve!: () => void; const promise = new Promise<void>(yes => { resolve = yes; }); return { promise, resolve }; };
async function setup(page: Page, status: number, response: unknown, wait?: Promise<void>) {
  const commands: { key: string; body: unknown }[] = []; let reads = 0;
  await page.route('**/api/v1/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/session')) return route.fulfill({ json: mockIdentity });
    if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { token: 'csrf' } });
    if (path.endsWith('/accounts/me')) { reads++; return route.fulfill({ json: mockAccount }); }
    if (path.endsWith('/transfers')) {
      expect(request.method()).toBe('POST'); expect(request.headers()['x-csrf-token']).toBe('csrf');
      commands.push({ key: request.headers()['idempotency-key']!, body: request.postDataJSON() });
      if (wait) await wait;
      return status === 0 ? route.abort() : route.fulfill({ status, json: response });
    }
    throw new Error('Unexpected operation ' + path);
  });
  return { commands, reads: () => reads };
}
async function review(page: Page) {
  await page.goto('/customer/transfer');
  await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill('DESTINATION');
  await page.getByLabel('Số tiền VND (bắt buộc)').fill('200000');
  await page.getByRole('checkbox', { name: /Kèm ghi chú/ }).check();
  await page.getByLabel('Ghi chú', { exact: true }).fill('  exact note  ');
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
}
for (const [status, body, outcome] of [
  [200, { outcome: 'COMPLETED', reference: 'CONFIRMED-REF' }, 'COMPLETED'],
  [200, { outcome: 'COMPLETED' }, 'UNKNOWN'],
  [202, { outcome: 'PROCESSING' }, 'PROCESSING'], [202, { outcome: 'UNKNOWN' }, 'UNKNOWN'],
  [409, { code: 'IDEMPOTENCY_CONFLICT', message: 'Conflict' }, 'UNKNOWN'],
  [422, { outcome: 'REJECTED', code: 'INSUFFICIENT_FUNDS', message: 'Không đủ số dư' }, 'REJECTED'],
  [503, { code: 'UNAVAILABLE', message: 'Uncertain' }, 'UNKNOWN'], [0, {}, 'UNKNOWN'],
] as const) test(`${status}/${JSON.stringify(body)}: only body-confirmed outcome, no automatic retry`, async ({ page }) => {
  const api = await setup(page, status, body); await review(page);
  await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText(`Trạng thái: ${outcome}`);
  expect(api.commands).toHaveLength(1); expect(api.commands[0]?.key).toBeTruthy();
  expect(api.commands[0]?.body).toEqual({ recipientAccountNumber: 'DESTINATION', amountVnd: '200000', note: '  exact note  ' });
  if (outcome === 'COMPLETED') {
    await expect(page.getByRole('link', { name: 'Xem biên nhận', exact: true })).toHaveAttribute('href', '/customer/receipts/CONFIRMED-REF');
    await expect.poll(api.reads).toBeGreaterThan(1);
  } else await expect(page.getByRole('link', { name: 'Xem biên nhận', exact: true })).toHaveCount(0);
  if (outcome === 'COMPLETED' || outcome === 'REJECTED') await expect(page.getByRole('button', { name: 'Tạo yêu cầu mới' })).toBeVisible();
  else {
    await expect(page.getByRole('button', { name: 'Tạo yêu cầu mới' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true })).toHaveCount(0);
  }
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(api.commands).toHaveLength(1);
});
test('double submit locks, navigation/revalidation retains UNKNOWN, late completion never renders', async ({ page }) => {
  const pending = gate(); const api = await setup(page, 200, { outcome: 'COMPLETED', reference: 'LATE-REF' }, pending.promise);
  try {
    await review(page);
    await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    await expect(page.getByRole('heading', { name: 'Đang gửi yêu cầu' })).toBeVisible(); await expect.poll(() => api.commands.length).toBe(1);
    await page.getByRole('navigation', { name: 'Điều hướng khách hàng' }).getByRole('link', { name: 'Tổng quan' }).click();
    await expect(page.getByRole('heading', { name: 'Tổng quan tài khoản', exact: true })).toBeVisible();
    await page.getByRole('navigation', { name: 'Điều hướng khách hàng' }).getByRole('link', { name: 'Chuyển tiền', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
    pending.resolve(); await expect(page.getByText('LATE-REF', { exact: false })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true })).toHaveCount(0); expect(api.commands).toHaveLength(1);
  } finally { pending.resolve(); }
});
test('explicit new intent after final rejection gets another key', async ({ page }) => {
  const api = await setup(page, 422, { outcome: 'REJECTED', code: 'INSUFFICIENT_FUNDS', message: 'No funds' });
  await review(page); await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo yêu cầu mới' }).click();
  await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill('DESTINATION'); await page.getByLabel('Số tiền VND (bắt buộc)').fill('1');
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click(); await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Trạng thái: REJECTED'); expect(api.commands).toHaveLength(2);
  expect(api.commands[1]?.key).not.toBe(api.commands[0]?.key);
});
