import { expect, test, type Page, type Route } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockAccount, mockIdentity } from '../../src/mocks/fixtures';

const deferred = () => { let resolve!: () => void; const promise = new Promise<void>(yes => { resolve = yes; }); return { promise, resolve }; };
async function setup(page: Page, data: unknown = mockAccount, role = 'CUSTOMER') {
  let session: typeof mockIdentity | undefined = { ...mockIdentity, role };
  let account = data;
  let calls = 0;
  let read: ((route: Route, snapshot: unknown) => Promise<void>) | undefined;
  const mutations: string[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    if (request.method() === 'POST') mutations.push(path);
    if (path.endsWith('/auth/session')) return route.fulfill({ status: session ? 200 : 401, json: session || { code: 'UNAUTHENTICATED', message: 'Synthetic expired session' } });
    if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { token: 'synthetic-csrf' } });
    if (path.endsWith('/accounts/me/history')) return route.fulfill({ json: { items: [], nextCursor: null } });
    if (path.endsWith('/auth/logout')) {
      expect(request.method()).toBe('POST'); expect(request.headers()['x-csrf-token']).toBe('synthetic-csrf');
      session = undefined; return route.fulfill({ json: { message: 'Synthetic sign-out' } });
    }
    if (path.endsWith('/accounts/me')) {
      calls++;
      expect(request.method()).toBe('GET'); expect(request.postData()).toBeNull();
      if (read) return read(route, account);
      return route.fulfill({ json: account });
    }
    throw new Error(`Unexpected Phase 4 endpoint ${path}`);
  });
  return { mutations, calls: () => calls, setRead: (handler?: typeof read) => { read = handler; }, setAccount: (value: unknown) => { account = value; }, setSession: (value: typeof session) => { session = value; } };
}

for (const [state, amount, status] of [['zero', '0', 'ACTIVE'], ['funded', '9007199254740993', 'ACTIVE'], ['frozen', '250000', 'FROZEN']] as const) {
  test(`${state}: precise balances, semantics, accessibility and responsive layout`, async ({ page }, info) => {
    const api = await setup(page, { ...mockAccount, balanceVnd: amount, availableBalanceVnd: amount, status });
    await page.goto('/customer');
    await expect(page.getByTestId('account-number')).toHaveText(mockAccount.accountNumber);
    const formatted = `${amount.replace(/\B(?=(\d{3})+(?!\d))/g, '.')} ₫`;
    await expect(page.getByTestId('available-balance')).toHaveText(formatted);
    await expect(page.getByTestId('account-balance')).toHaveText(formatted);
    await expect(page.getByText(status === 'ACTIVE' ? 'Đang hoạt động · ACTIVE' : 'Bị phong tỏa · FROZEN', { exact: true })).toBeVisible();
    if (state === 'zero') await expect(page.getByText('Tài khoản hiện có số dư 0 VND.', { exact: true })).toBeVisible();
    if (state === 'frozen') {
      await expect(page.getByRole('complementary', { name: 'Tài khoản bị phong tỏa' })).toBeVisible();
      await expect(page.getByRole('link', { name: /Mở trang chuyển tiền/ })).toHaveCount(0);
    }
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath(`dashboard-${state}.png`), fullPage: true });
    await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.getByRole('link', { name: /Mở trang lịch sử/ }).click();
    await expect(page.getByRole('heading', { name: 'Chưa có giao dịch' })).toBeVisible();
    expect(api.mutations).toEqual([]);
  });
}

test('loading and keyboard refresh clear previous balances until GET completes', async ({ page }) => {
  const api = await setup(page); const gate = deferred();
  try {
    api.setRead(async (route, data) => { await gate.promise; await route.fulfill({ json: data }); });
    await page.goto('/customer');
    await expect(page.getByRole('status')).toContainText('Đang tải thông tin tài khoản');
    await expect(page.getByRole('button', { name: 'Đang tải…', exact: true })).toBeDisabled();
    await expect(page.getByTestId('account-number')).toHaveCount(0);
    gate.resolve(); await expect(page.getByTestId('account-number')).toBeVisible();
    await page.keyboard.press('Tab'); await expect(page.getByRole('link', { name: 'Bỏ qua điều hướng' })).toBeFocused();
    await page.keyboard.press('Enter'); await expect(page.getByRole('main')).toBeFocused();
    api.setAccount({ ...mockAccount, balanceVnd: '0', availableBalanceVnd: '0' }); api.setRead();
    await page.getByRole('button', { name: 'Làm mới', exact: true }).focus(); await page.keyboard.press('Enter');
    await expect(page.getByTestId('available-balance')).toHaveText('0 ₫');
    expect(api.mutations).toEqual([]);
  } finally { gate.resolve(); }
});

for (const scenario of ['empty', 'invalid', 'forbidden', 'network'] as const) {
  test(`${scenario}: explicit failure, no fake zero balance, manual GET retry`, async ({ page }) => {
    const api = await setup(page);
    api.setRead(route => scenario === 'network' ? route.abort()
      : scenario === 'forbidden' ? route.fulfill({ status: 403, json: { code: 'FORBIDDEN', message: 'Synthetic denial' } })
      : scenario === 'empty' ? route.fulfill({ body: 'null', contentType: 'application/json' })
      : route.fulfill({ json: { ...mockAccount, type: 'TREASURY' } }));
    await page.goto('/customer'); await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByTestId('account-number')).toHaveCount(0);
    await expect(page.getByTestId('available-balance')).toHaveCount(0);
    if (scenario === 'empty') await expect(page.getByRole('alert')).toContainText('phản hồi rỗng');
    if (scenario === 'forbidden') await expect(page.getByRole('alert')).toContainText('không có quyền');
    await expect(page.getByText('Chế độ Mock API', { exact: false })).toHaveCount(0);
    const before = api.calls(); api.setRead();
    await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
    await expect(page.getByTestId('account-number')).toHaveText(mockAccount.accountNumber);
    expect(api.calls()).toBe(before + 1); expect(api.mutations).toEqual([]);
  });
}

test('account 401 expires the session and redirects without retaining account data', async ({ page }) => {
  const api = await setup(page); await page.goto('/customer'); await expect(page.getByTestId('account-number')).toBeVisible();
  api.setRead(route => route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED', message: 'Synthetic session expired' } }));
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/); await expect(page.getByRole('status')).toContainText('Phiên đã hết hạn');
  await expect(page.getByTestId('account-number')).toHaveCount(0);
  await expect(page.getByText(mockAccount.accountNumber, { exact: true })).toHaveCount(0);
});

test('logout while account GET is pending discards its late response', async ({ page }) => {
  const gate = deferred(); const api = await setup(page, { ...mockAccount, accountNumber: 'PRIVATE-OLD-ACCOUNT' });
  try {
    api.setRead(async (route, data) => { await gate.promise; await route.fulfill({ json: data }); });
    await page.goto('/customer'); await expect.poll(api.calls).toBeGreaterThan(0);
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(page).toHaveURL(/\/login$/);
    gate.resolve(); await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await expect(page.getByText('PRIVATE-OLD-ACCOUNT', { exact: true })).toHaveCount(0);
    expect(api.mutations).toEqual(['/api/v1/auth/logout']);
  } finally { gate.resolve(); }
});

test('principal switch during GET clears A and renders only B even after A completes', async ({ page }) => {
  const api = await setup(page, { ...mockAccount, accountNumber: 'PRIVATE-A' }); const gate = deferred();
  let oldCompleted = false;
  try {
    await page.goto('/customer'); await expect(page.getByTestId('account-number')).toHaveText('PRIVATE-A');
    api.setRead(async (route, data) => { await gate.promise; await route.fulfill({ json: data }); oldCompleted = true; });
    const before = api.calls(); await page.getByRole('button', { name: 'Làm mới', exact: true }).click(); await expect.poll(api.calls).toBeGreaterThan(before);
    api.setSession({ ...mockIdentity, userId: 'B', displayName: 'Synthetic B' });
    api.setAccount({ ...mockAccount, accountNumber: 'PRIVATE-B', balanceVnd: '0', availableBalanceVnd: '0' }); api.setRead();
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(page.getByTestId('account-number')).toHaveText('PRIVATE-B');
    gate.resolve(); await expect.poll(() => oldCompleted).toBe(true);
    await expect(page.getByTestId('account-number')).toHaveText('PRIVATE-B');
    await expect(page.getByText('PRIVATE-A', { exact: true })).toHaveCount(0);
    await expect(page.getByTestId('available-balance')).toHaveText('0 ₫');
  } finally { gate.resolve(); }
});

test('ADMIN guard prevents account GET and private account display', async ({ page }) => {
  const api = await setup(page, mockAccount, 'ADMIN'); await page.goto('/customer');
  await expect(page.getByRole('heading', { name: 'Không có quyền truy cập Customer Web' })).toBeVisible();
  expect(api.calls()).toBe(0); await expect(page.getByTestId('account-number')).toHaveCount(0);
});

for (const outcome of ['A', 'B', '401', 'network'] as const) {
  test(`deferred session ${outcome}: hides rendered account and rejects late GET before verification`, async ({ page }) => {
    const api = await setup(page, { ...mockAccount, accountNumber: 'PRIVATE-A' });
    const sessionGate = deferred(); const accountGate = deferred(); let oldCompleted = false;
    try {
      await page.goto('/customer'); await expect(page.getByTestId('account-number')).toHaveText('PRIVATE-A');
      await page.route('**/api/v1/auth/session', async route => {
        await sessionGate.promise;
        if (outcome === 'network') return route.abort();
        if (outcome === '401') return route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED', message: 'Synthetic expiry' } });
        return route.fulfill({ json: { ...mockIdentity, userId: outcome, displayName: 'Confirmed ' + outcome } });
      });
      await page.evaluate(() => { window.dispatchEvent(new Event('focus')); window.dispatchEvent(new Event('focus')); });
      await expect(page.getByRole('status')).toHaveText('Đang kiểm tra phiên…');
      await expect(page.getByTestId('account-number')).toHaveCount(0);
      await expect(page.getByText('PRIVATE-A', { exact: true })).toHaveCount(0);
      const before = api.calls();
      api.setAccount({ ...mockAccount, accountNumber: 'CONFIRMED-' + outcome });
      sessionGate.resolve();
      if (outcome === '401') { await expect(page).toHaveURL(/\/login$/); await expect(page.getByRole('status')).toContainText('Phiên đã hết hạn'); }
      else if (outcome === 'network') await expect(page.getByRole('heading', { name: 'Không thể xác minh phiên' })).toBeVisible();
      else {
        await expect(page.getByTestId('account-number')).toHaveText('CONFIRMED-' + outcome);
        // Start an old account GET, then revalidate again with a deliberately deferred session.
        const secondGate = deferred();
        await page.route('**/api/v1/auth/session', async route => { await secondGate.promise; await route.fulfill({ json: { ...mockIdentity, userId: outcome } }); });
        api.setRead(async (route, snapshot) => { await accountGate.promise; await route.fulfill({ json: snapshot }); oldCompleted = true; });
        const reads = api.calls(); await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
        await expect.poll(api.calls).toBeGreaterThan(reads);
        await page.evaluate(() => window.dispatchEvent(new Event('focus')));
        await expect(page.getByRole('status')).toHaveText('Đang kiểm tra phiên…');
        accountGate.resolve(); await expect.poll(() => oldCompleted).toBe(true);
        await expect(page.getByTestId('account-number')).toHaveCount(0);
        api.setRead(); secondGate.resolve(); await expect(page.getByTestId('account-number')).toHaveText('CONFIRMED-' + outcome);
      }
      if (outcome === '401' || outcome === 'network') expect(api.calls()).toBe(before);
      expect(api.mutations).toEqual([]);
    } finally { sessionGate.resolve(); accountGate.resolve(); }
  });
}
