import { test, expect, type BrowserContext, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockAccount, mockIdentity } from '../../src/mocks/fixtures';
const gate = () => { let resolve!: () => void; const promise = new Promise<void>(yes => { resolve = yes; }); return { promise, resolve }; };
test('missing Web Locks fails closed before creating an intent', async ({ page, context }) => {
  const api = await harness(context);
  await page.addInitScript(() => Object.defineProperty(navigator, 'locks', { value: undefined, configurable: true }));
  await page.goto('/customer/transfer');
  await expect(page.getByText('Không thể đọc dữ liệu khôi phục an toàn.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(0); expect(api.lookups).toHaveLength(0);
});
async function harness(context: BrowserContext) {
  const commands: { key: string; body: unknown }[] = []; const lookups: unknown[] = [];
  let response: { status: number; body: unknown } = { status: 202, body: { outcome: 'UNKNOWN' } };
  let wait: Promise<void> | undefined; let transferWait: Promise<void> | undefined; let userId = mockIdentity.userId;
  await context.route('**/api/v1/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/session')) return route.fulfill({ json: { ...mockIdentity, userId } });
    if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { token: 'csrf' } });
    if (path.endsWith('/accounts/me')) return route.fulfill({ json: mockAccount });
    if (path.endsWith('/transfers')) { commands.push({ key: request.headers()['idempotency-key']!, body: request.postDataJSON() }); if (transferWait) await transferWait; return route.fulfill({ status: 202, json: { outcome: 'UNKNOWN' } }); }
    if (path.endsWith('/request-outcomes/resolve')) {
      expect(request.method()).toBe('POST'); expect(request.headers()['x-csrf-token']).toBe('csrf');
      lookups.push(request.postDataJSON()); const reply = response; if (wait) await wait;
      return route.fulfill({ status: reply.status, json: reply.body });
    }
    throw new Error('Unexpected operation ' + path);
  });
  return { commands, lookups, result: (status: number, body: unknown) => { response = { status, body }; }, pause: (promise?: Promise<void>) => { wait = promise; }, pauseTransfer: (promise?: Promise<void>) => { transferWait = promise; }, owner: (id: string) => { userId = id; } };
}
async function review(page: Page) {
  await page.goto('/customer/transfer');
  await page.getByLabel('Số tài khoản nhận (bắt buộc)').fill('PRIVATE-DEST');
  await page.getByLabel('Số tiền VND (bắt buộc)').fill('200000');
  await page.getByRole('checkbox', { name: /Kèm ghi chú/ }).check();
  await page.getByLabel('Ghi chú', { exact: true }).fill('private exact note');
  await page.getByRole('button', { name: 'Kiểm tra thông tin', exact: true }).click();
}
async function send(page: Page) {
  await review(page);
  await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
}
test('reload restores encrypted intent, explicit resolve recovers original reference without reposting', async ({ page, context }) => {
  const api = await harness(context); await send(page);
  expect(api.commands).toHaveLength(1);
  const storage = await page.evaluate(async () => {
    const db = await new Promise<IDBDatabase>((resolve, reject) => { const request = indexedDB.open('finfast-transfer-recovery-v1:live'); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); });
    const records = await new Promise<unknown[]>((resolve, reject) => { const request = db.transaction('records').objectStore('records').getAll(); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }); db.close();
    return { text: JSON.stringify(records), keys: records.map(record => (record as { key: CryptoKey }).key.extractable), local: JSON.stringify(localStorage), session: JSON.stringify(sessionStorage) };
  });
  expect(storage.text).not.toContain('PRIVATE-DEST'); expect(storage.text).not.toContain('private exact note'); expect(storage.keys).toEqual([false]);
  expect(storage.local + storage.session).not.toContain(api.commands[0]!.key);
  await page.reload(); await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible(); expect(api.lookups).toHaveLength(0);
  api.result(200, { outcome: 'COMPLETED', reference: 'ORIGINAL-REF', amountVnd: '200000' });
  await page.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('link', { name: 'Xem biên nhận', exact: true })).toHaveAttribute('href', '/customer/receipts/ORIGINAL-REF');
  expect(api.lookups).toEqual([{ operationType: 'INTERNAL_TRANSFER', idempotencyKey: api.commands[0]!.key }]); expect(api.commands).toHaveLength(1);
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('page closes with transfer response pending: another page restores original key without reposting', async ({ page, context }) => {
  const api = await harness(context); const pending = gate(); api.pauseTransfer(pending.promise);
  try {
    await review(page); await page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).click();
    await expect.poll(() => api.commands.length).toBe(1);
    await page.close(); pending.resolve();
    const restored = await context.newPage(); await restored.goto('/customer/transfer');
    await expect(restored.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
    expect(api.lookups).toHaveLength(0); api.result(200, { outcome: 'COMPLETED', reference: 'RECOVERED-AFTER-CLOSE' });
    await restored.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).click();
    await expect(restored.getByRole('heading', { name: 'Chuyển tiền đã hoàn tất' })).toBeVisible();
    expect(api.lookups).toEqual([{ operationType: 'INTERNAL_TRANSFER', idempotencyKey: api.commands[0]!.key }]); expect(api.commands).toHaveLength(1);
  } finally { pending.resolve(); }
});
test('two prepared tabs cannot create competing transfers', async ({ page, context }) => {
  const api = await harness(context); await review(page);
  const other = await context.newPage(); await review(other);
  await Promise.all([page, other].map(tab => tab.getByRole('button', { name: 'Gửi chuyển tiền', exact: true }).evaluate(button => (button as HTMLButtonElement).click())));
  await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
  await expect(other.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
  expect(api.commands).toHaveLength(1);
});
for (const [status, body, outcome] of [
  [200, { outcome: 'REJECTED', code: 'DECLINED', message: 'Rejected' }, 'REJECTED'],
  [200, { outcome: 'UNKNOWN' }, 'UNKNOWN'], [200, { outcome: 'PROCESSING' }, 'PROCESSING'],
  [200, { outcome: 'COMPLETED' }, 'UNKNOWN'], [409, { code: 'CONFLICT', message: 'Conflict' }, 'UNKNOWN'],
  [422, { outcome: 'REJECTED', code: 'INVALID', message: 'Unexpected status' }, 'UNKNOWN'],
  [503, { code: 'UNAVAILABLE', message: 'Unavailable' }, 'UNKNOWN'],
] as const) test(`resolve ${status}/${JSON.stringify(body)} never infers success from HTTP`, async ({ page, context }) => {
  const api = await harness(context); await send(page); api.result(status, body);
  await page.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText(`Trạng thái: ${outcome}`);
  expect(api.commands).toHaveLength(1); expect(api.lookups).toHaveLength(1);
  if (outcome !== 'REJECTED') await expect(page.getByRole('button', { name: 'Tạo yêu cầu mới' })).toHaveCount(0);
});
test('two tabs serialize resolve, double click creates one lookup and never repeats transfer', async ({ page, context }) => {
  const api = await harness(context); await send(page);
  const other = await context.newPage(); await other.goto('/customer/transfer');
  await expect(other.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible();
  const pending = gate(); api.pause(pending.promise);
  try {
    await page.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    await expect.poll(() => api.lookups.length).toBe(1);
    await other.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).evaluate(button => (button as HTMLButtonElement).click());
    await expect(other.getByText('Một tab khác đang xử lý yêu cầu.', { exact: false })).toBeVisible(); expect(api.lookups).toHaveLength(1);
    pending.resolve(); await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible(); expect(api.commands).toHaveLength(1);
  } finally { pending.resolve(); }
});
test('principal change during resolve hides A and discards late completion; A can recover after reload', async ({ page, context }) => {
  const api = await harness(context); await send(page); const pending = gate(); api.pause(pending.promise); api.result(200, { outcome: 'COMPLETED', reference: 'PRIVATE-A-REF' });
  try {
    await page.getByRole('button', { name: 'Tra cứu kết quả', exact: true }).click(); await expect.poll(() => api.lookups.length).toBe(1);
    api.owner('CUSTOMER-B');
    await page.evaluate(() => { const channel = new BroadcastChannel('finfast-session-invalidation-v1'); channel.postMessage('invalidate'); channel.close(); });
    await expect(page.getByLabel('Số tài khoản nhận (bắt buộc)')).toBeVisible();
    pending.resolve(); await expect(page.getByText('PRIVATE-A-REF')).toHaveCount(0); await expect(page.getByText('PRIVATE-DEST')).toHaveCount(0);
    api.owner(mockIdentity.userId); api.pause(); await page.reload();
    await expect(page.getByRole('heading', { name: 'Chưa xác định kết quả' })).toBeVisible(); expect(api.commands).toHaveLength(1);
  } finally { pending.resolve(); }
});
test('corrupted encrypted record blocks replacement instead of clearing storage', async ({ page, context }) => {
  const api = await harness(context); await send(page);
  await page.evaluate(async () => {
    const db = await new Promise<IDBDatabase>(resolve => { const request = indexedDB.open('finfast-transfer-recovery-v1:live'); request.onsuccess = () => resolve(request.result); });
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('records', 'readwrite'); const store = tx.objectStore('records'); const cursor = store.openCursor();
      cursor.onsuccess = () => { if (cursor.result) cursor.result.update({ version: 1, ciphertext: new ArrayBuffer(1) }); };
      tx.oncomplete = () => resolve(); tx.onabort = () => reject(tx.error);
    }); db.close();
  });
  await page.reload(); await expect(page.getByText('Không thể đọc dữ liệu khôi phục an toàn.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gửi chuyển tiền', exact: true })).toHaveCount(0); expect(api.commands).toHaveLength(1);
});
