import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { createHttpClient } from '../../src/api/client';
import { ApiError } from '../../src/api/errors';
import { createAccountService, decodeAccount, AccountDataError, type AccountOverview } from '../../src/features/account/service';
import { createAccountStore } from '../../src/features/account/accountStore';
import { createSessionStore } from '../../src/features/auth/sessionStore';
import { createIdentityService, type Session } from '../../src/features/auth/service';
import { formatMoneyVnd, MAX_VND } from '../../src/lib/money';
import { createMockApi } from '../../src/mocks/handlers';
import { mockAccount, mockCredentials, mockFrozenCredentials } from '../../src/mocks/fixtures';

const origin = 'http://dashboard.test';
const mock = createMockApi(origin);
const server = setupServer(...mock.handlers);
const client = createHttpClient({ baseUrl: `${origin}/api/v1` });
const identity = createIdentityService(client);
const service = createAccountService(client);
const cleanup: (() => void)[] = [];
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => { cleanup.splice(0).forEach(stop => stop()); server.resetHandlers(); mock.reset(); });
afterAll(() => server.close());
const deferred = <T>() => { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const account = (number: string, balance = '0') => decodeAccount({ ...mockAccount, accountNumber: number, balanceVnd: balance, availableBalanceVnd: balance });

describe('AccountOverview contract and VND', () => {
  it.each(['0', '250000', '9007199254740993', MAX_VND])('preserves exact VND strings %s', value => {
    const data = account('SYNTHETIC', value);
    expect(data.balanceVnd).toBe(value); expect(data.availableBalanceVnd).toBe(value);
    expect(formatMoneyVnd(data.balanceVnd)).toBe(`${value.replace(/\B(?=(\d{3})+(?!\d))/g, '.')} ₫`);
  });
  it('supports FROZEN read access', () => expect(decodeAccount({ ...mockAccount, status: 'FROZEN' }).status).toBe('FROZEN'));
  it.each([null, {}])('classifies empty data as protocol failure, never zero balance', value => {
    try { decodeAccount(value); throw new Error('Accepted empty data'); }
    catch (error) { expect(error).toBeInstanceOf(AccountDataError); expect(error).toMatchObject({ kind: 'protocol', empty: true }); }
  });
  it.each([
    [], { ...mockAccount, accountNumber: '' }, { ...mockAccount, type: 'TREASURY' },
    { ...mockAccount, status: ['ACTIVE'] }, { ...mockAccount, status: null }, { ...mockAccount, status: 'CLOSED' },
    { ...mockAccount, currency: 'USD' }, { ...mockAccount, accountId: 'invented' },
    { ...mockAccount, availableBalanceVnd: undefined }, { ...mockAccount, balanceVnd: 0 },
    { ...mockAccount, balanceVnd: '01' }, { ...mockAccount, balanceVnd: '-1' },
    { ...mockAccount, balanceVnd: '1.5' }, { ...mockAccount, balanceVnd: '9223372036854775808' },
    { ...mockAccount, availableBalanceVnd: '0' },
  ])('rejects malformed/private/inconsistent account %j', value => expect(() => decodeAccount(value)).toThrow(AccountDataError));
});

describe('account integration with the existing client and per-principal MSW', () => {
  it('keeps a JSON null response distinct from an empty HTTP body', async () => {
    server.use(http.get(`${origin}/api/v1/accounts/me`, () => HttpResponse.json(null)));
    await expect(service.read(new AbortController().signal)).rejects.toMatchObject({ kind: 'protocol', empty: true });
    server.use(http.get(`${origin}/api/v1/accounts/me`, () => new HttpResponse('', { headers: { 'Content-Type': 'application/json' } })));
    await expect(service.read(new AbortController().signal)).rejects.toMatchObject({ kind: 'protocol' });
  });
  it('reads zero/new, funded and frozen fixtures through the documented endpoint', async () => {
    await expect(service.read(new AbortController().signal)).rejects.toMatchObject({ kind: 'http', status: 401 });
    await identity.register({ displayName: 'New', email: 'new@example.test', password: 'synthetic' });
    await identity.login({ email: 'new@example.test', password: 'synthetic' });
    expect(await service.read(new AbortController().signal)).toMatchObject({ status: 'ACTIVE', balanceVnd: '0', availableBalanceVnd: '0' });
    await identity.logout(); await identity.login(mockCredentials);
    expect(await service.read(new AbortController().signal)).toEqual(mockAccount);
    await identity.logout(); await identity.login(mockFrozenCredentials);
    expect(await service.read(new AbortController().signal)).toMatchObject({ status: 'FROZEN', balanceVnd: '250000' });
  });
  it('uses cookie GET without CSRF or mutations', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () => Response.json(mockAccount));
    await createAccountService(createHttpClient({ baseUrl: `${origin}/api/v1`, fetchImpl })).read(new AbortController().signal);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(fetchImpl.mock.calls[0]?.[0]).toBe(`${origin}/api/v1/accounts/me`);
    expect(fetchImpl.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include', cache: 'no-store' });
    expect(new Headers(fetchImpl.mock.calls[0]?.[1]?.headers).has('X-CSRF-TOKEN')).toBe(false);
  });
  it.each([204, 404, 500])('does not invent an empty success or new status mapping for %s', async status => {
    server.use(http.get(`${origin}/api/v1/accounts/me`, () => status === 204 ? new HttpResponse(null, { status }) : HttpResponse.json({ code: 'ERROR', message: 'Synthetic' }, { status })));
    await expect(service.read(new AbortController().signal)).rejects.toMatchObject({ kind: 'protocol', status });
  });
});

async function harness(role: Session['role'] = 'CUSTOMER') {
  const principal: Session = { userId: 'A', role };
  const sessionApi = { login: vi.fn(async () => principal), session: vi.fn(async () => principal), logout: vi.fn(async () => undefined), register: vi.fn(async () => undefined) };
  const session = createSessionStore(sessionApi); await session.login(mockCredentials);
  const requests: (ReturnType<typeof deferred<AccountOverview>> & { signal: AbortSignal })[] = [];
  const read = vi.fn((signal: AbortSignal) => { const pending = { ...deferred<AccountOverview>(), signal }; requests.push(pending); return pending.promise; });
  const store = createAccountStore(session, { read });
  const stop = store.connect(); cleanup.push(stop);
  return { store, session, sessionApi, requests, read, stop };
}
describe('Dashboard request ownership and session boundaries', () => {
  it('reloads account after same-principal session verification', async () => {
    const h = await harness(); expect(h.store.getSnapshot().status).toBe('loading');
    h.requests[0]!.resolve(account('A'));
    await vi.waitFor(() => expect(h.store.getSnapshot()).toMatchObject({ status: 'ready', account: { accountNumber: 'A' } }));
    await h.session.refresh(); expect(h.read).toHaveBeenCalledTimes(2);
    expect(h.store.getSnapshot().status).toBe('loading');
  });
  it('aborts/clears on logout and ignores a late successful response', async () => {
    const h = await harness(); await h.session.logout();
    expect(h.requests[0]!.signal.aborted).toBe(true);
    h.requests[0]!.resolve(account('PRIVATE-A', '999'));
    await Promise.resolve(); expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
  });
  it('clears rendered A immediately on principal change and ignores late A responses', async () => {
    const h = await harness(); h.requests[0]!.resolve(account('PRIVATE-A'));
    await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
    const oldRead = h.store.reload();
    h.sessionApi.session.mockResolvedValue({ userId: 'B', role: 'CUSTOMER' }); await h.session.refresh();
    expect(h.store.getSnapshot().status).toBe('loading'); expect(h.requests[1]!.signal.aborted).toBe(true);
    h.requests[2]!.resolve(account('PRIVATE-B', '250000'));
    await vi.waitFor(() => expect(h.store.getSnapshot()).toMatchObject({ account: { accountNumber: 'PRIVATE-B' } }));
    h.requests[1]!.resolve(account('PRIVATE-A')); await oldRead;
    expect(h.store.getSnapshot()).toMatchObject({ account: { accountNumber: 'PRIVATE-B' } });
  });
  it('a late A 401 cannot expire B or a new login of the same user', async () => {
    const h = await harness(); const oldScope = h.session.getScope();
    await h.session.logout(); await h.session.login(mockCredentials);
    expect(h.session.getScope()).not.toBe(oldScope);
    h.requests[0]!.reject(new ApiError('http', { status: 401 }));
    h.requests[1]!.resolve(account('NEW-SESSION-A'));
    await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
    h.session.expireSession(oldScope);
    expect(h.session.getSnapshot().status).toBe('authenticated');
  });
  it('ignores old account 401 while session revalidation is pending', async () => {
    const h = await harness(); const pending = deferred<Session>();
    h.sessionApi.session.mockImplementation(() => pending.promise); const refresh = h.session.refresh();
    h.requests[0]!.reject(new ApiError('http', { status: 401 }));
    await Promise.resolve(); expect(h.session.getSnapshot().status).toBe('revalidating');
    pending.resolve({ userId: 'B', role: 'CUSTOMER' }); await refresh;
    expect(h.session.getSnapshot()).toMatchObject({ status: 'authenticated', session: { userId: 'B' } });
    h.requests[1]!.reject(new ApiError('http', { status: 401 }));
    await vi.waitFor(() => expect(h.session.getSnapshot()).toEqual({ status: 'anonymous', expired: true }));
  });
  it.each(['A', 'B', '401', 'network'])('blocks account reads throughout deferred revalidation: %s', async outcome => {
    const h = await harness(); h.requests[0]!.resolve(account('PRIVATE-A'));
    await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
    const pending = deferred<Session>(); h.sessionApi.session.mockImplementation(() => pending.promise);
    const oldScope = h.session.getScope(); const refresh = h.session.refresh();
    expect(h.session.getSnapshot().status).toBe('revalidating');
    expect(h.session.getSnapshot().session).toBeUndefined();
    expect(h.session.getScope()).not.toBe(oldScope);
    expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
    expect(h.requests[0]!.signal.aborted).toBe(true);
    expect(h.session.refresh()).toBe(refresh);
    await h.store.reload(); expect(h.read).toHaveBeenCalledTimes(1);
    expect(h.sessionApi.session).toHaveBeenCalledTimes(1);
    if (outcome === '401' || outcome === 'network') pending.reject(new ApiError(outcome === '401' ? 'http' : 'network', { status: outcome === '401' ? 401 : undefined }));
    else pending.resolve({ userId: outcome, role: 'CUSTOMER' });
    await refresh;
    if (outcome === '401' || outcome === 'network') {
      expect(h.session.getSnapshot().status).toBe(outcome === '401' ? 'anonymous' : 'error');
      expect(h.store.getSnapshot()).toEqual({ status: 'loading' }); expect(h.read).toHaveBeenCalledTimes(1);
    } else {
      expect(h.read).toHaveBeenCalledTimes(2); expect(h.store.getSnapshot().status).toBe('loading');
      h.requests[1]!.resolve(account('CONFIRMED-' + outcome));
      await vi.waitFor(() => expect(h.store.getSnapshot()).toMatchObject({ account: { accountNumber: 'CONFIRMED-' + outcome } }));
    }
  });
  it('rejects account response arriving before deferred session response, even if abort is ignored', async () => {
    const h = await harness(); const pending = deferred<Session>();
    h.sessionApi.session.mockImplementation(() => pending.promise);
    const refresh = h.session.refresh();
    expect(h.requests[0]!.signal.aborted).toBe(true);
    h.requests[0]!.resolve(account('PRIVATE-B')); await Promise.resolve();
    expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
    await h.store.reload(); expect(h.read).toHaveBeenCalledTimes(1);
    pending.resolve({ userId: 'B', role: 'CUSTOMER' }); await refresh;
    expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
    h.requests[1]!.resolve(account('VERIFIED-B'));
    await vi.waitFor(() => expect(h.store.getSnapshot()).toMatchObject({ account: { accountNumber: 'VERIFIED-B' } }));
  });
  it('logout supersedes pending revalidation and late success cannot restore account access', async () => {
    const h = await harness(); const pending = deferred<Session>();
    h.sessionApi.session.mockImplementation(() => pending.promise);
    const refresh = h.session.refresh(); await h.session.logout();
    pending.resolve({ userId: 'A', role: 'CUSTOMER' }); await refresh;
    expect(h.session.getSnapshot().status).toBe('anonymous');
    expect(h.store.getSnapshot()).toEqual({ status: 'loading' }); expect(h.read).toHaveBeenCalledTimes(1);
  });
  it('new login gets its own verification; old refresh cleanup cannot drop the new pending request', async () => {
    const h = await harness(); const old = deferred<Session>(); const next = deferred<Session>();
    h.sessionApi.session.mockImplementationOnce(() => old.promise).mockImplementationOnce(() => next.promise);
    const first = h.session.refresh(); await Promise.resolve();
    await h.session.logout(); h.sessionApi.login.mockResolvedValue({ userId: 'B', role: 'CUSTOMER' });
    await h.session.login(mockCredentials);
    const second = h.session.refresh(); expect(second).not.toBe(first);
    old.resolve({ userId: 'A', role: 'CUSTOMER' }); await first;
    expect(h.session.getSnapshot().status).toBe('revalidating');
    expect(h.session.refresh()).toBe(second);
    next.resolve({ userId: 'B', role: 'CUSTOMER' }); await second;
    expect(h.session.getSnapshot()).toMatchObject({ status: 'authenticated', session: { userId: 'B' } });
    expect(h.sessionApi.session).toHaveBeenCalledTimes(2);
  });
  it('latest refresh wins even if transport ignores abort', async () => {
    const h = await harness(); const latest = h.store.reload();
    expect(h.requests[0]!.signal.aborted).toBe(true);
    h.requests[1]!.resolve(account('LATEST')); await latest;
    h.requests[0]!.resolve(account('STALE')); await Promise.resolve();
    expect(h.store.getSnapshot()).toMatchObject({ account: { accountNumber: 'LATEST' } });
  });
  it.each([
    [new ApiError('http', { status: 403 }), 'forbidden'], [new ApiError('network'), 'network'],
    [new ApiError('timeout'), 'timeout'], [new ApiError('protocol', { status: 401 }), 'protocol'], [new AccountDataError(true), 'empty'],
  ] as const)('maps failure without retrying or showing stale balances', async (error, reason) => {
    const h = await harness(); h.requests[0]!.resolve(account('A', '10'));
    await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
    const retry = h.store.reload(); expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
    h.requests[1]!.reject(error); await retry;
    expect(h.store.getSnapshot()).toEqual({ status: 'error', reason }); expect(h.read).toHaveBeenCalledTimes(2);
    expect(h.session.getSnapshot().status).toBe('authenticated');
  });
  it('does not fetch for ADMIN and clears data on role change', async () => {
    const admin = await harness('ADMIN'); expect(admin.read).not.toHaveBeenCalled();
    const h = await harness(); h.requests[0]!.resolve(account('A')); await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
    h.sessionApi.session.mockResolvedValue({ userId: 'A', role: 'ADMIN' }); await h.session.refresh();
    expect(h.store.getSnapshot()).toEqual({ status: 'loading' }); expect(h.read).toHaveBeenCalledTimes(1);
  });
  it('cleans up on unmount and safely reconnects under StrictMode', async () => {
    const h = await harness(); h.stop(); expect(h.requests[0]!.signal.aborted).toBe(true);
    cleanup.push(h.store.connect()); h.requests[1]!.resolve(account('CURRENT'));
    await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
    h.requests[0]!.resolve(account('OLD')); await Promise.resolve();
    expect(h.store.getSnapshot()).toMatchObject({ account: { accountNumber: 'CURRENT' } });
  });
});
