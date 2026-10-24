import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { createHttpClient } from '../../src/api/client';
import { ApiError } from '../../src/api/errors';
import { createHistoryService, decodeHistory, decodeReceipt } from '../../src/features/history/service';
import { createReadStore } from '../../src/features/history/readStore';
import { createSessionStore } from '../../src/features/auth/sessionStore';
import { createIdentityService, type Session } from '../../src/features/auth/service';
import { createMockApi } from '../../src/mocks/handlers';
import { mockCredentials, mockFrozenCredentials, mockFrozenHistory, mockHistoryItem, mockReceipt } from '../../src/mocks/fixtures';

const origin = 'http://history.test'; const mock = createMockApi(origin); const server = setupServer(...mock.handlers);
const client = createHttpClient({ baseUrl: `${origin}/api/v1` }); const service = createHistoryService(client); const identity = createIdentityService(client);
const signal = () => new AbortController().signal;
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => { server.resetHandlers(); mock.reset(); }); afterAll(() => server.close());
const deferred = <T>() => { let resolve!: (value: T) => void; let reject!: (reason: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

describe('history and receipt contract', () => {
  it('preserves exact positive VND and empty history', () => {
    expect(decodeHistory({ items: [], nextCursor: null })).toEqual({ items: [], nextCursor: null });
    const receipt = decodeReceipt({ ...mockReceipt, amountVnd: '9223372036854775807' }, mockReceipt.reference);
    expect(receipt.amountVnd).toBe('9223372036854775807');
  });
  it.each([null, {}, { items: [], nextCursor: 2 }, { items: [], nextCursor: null, total: 0 }, { items: [mockHistoryItem, mockHistoryItem], nextCursor: null }])('rejects invalid page %j', value => expect(() => decodeHistory(value)).toThrow(ApiError));
  it.each([{ amountVnd: 1000 }, { amountVnd: '0' }, { amountVnd: '-1' }, { amountVnd: '1.5' }, { amountVnd: '9223372036854775808' }, { type: 'GENESIS' }, { direction: ['INCOMING'] }, { currency: 'USD' }, { committedAt: 'bad' }, { status: 'PROCESSING' }, { type: 'DEMO_ALLOCATION', direction: 'OUTGOING' }])('rejects invalid movement %j', change => {
    expect(() => decodeHistory({ items: [{ ...mockHistoryItem, ...change }], nextCursor: null })).toThrow(ApiError);
  });
  it.each([{ status: 'PROCESSING' }, { reference: 'WRONG' }, { sourceLabel: null }, { amountVnd: '0' }, { extra: true }])('rejects invalid or wrong-reference receipt %j', change => expect(() => decodeReceipt({ ...mockReceipt, ...change }, mockReceipt.reference)).toThrow(ApiError));
  it('round-trips opaque cursor and encodes reference as one segment, GET only', async () => {
    const token = 'opaque + /?=&% Unicode'; const reference = 'R /?#%';
    const fetchImpl = vi.fn<typeof fetch>(async input => {
      const url = new URL(String(input));
      if (url.pathname.endsWith('/history')) { expect(url.searchParams.get('cursor')).toBe(token); expect(url.searchParams.get('limit')).toBe('20'); return Response.json({ items: [], nextCursor: null }); }
      expect(url.pathname).toBe(`/api/v1/transactions/${encodeURIComponent(reference)}/receipt`);
      return Response.json({ ...mockReceipt, reference });
    });
    const api = createHistoryService(createHttpClient({ baseUrl: `${origin}/api/v1`, fetchImpl }));
    await api.history(token, signal()); await api.receipt(reference, signal());
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    for (const call of fetchImpl.mock.calls) expect(call[1]).toMatchObject({ method: 'GET', credentials: 'include', cache: 'no-store' });
  });
  it.each(['2026-02-30T00:00:00Z', '2026-02-29T00:00:00Z', '2026-10-23T24:00:00Z'])('rejects normalized invalid date %s', committedAt => {
    expect(() => decodeReceipt({ ...mockReceipt, committedAt }, mockReceipt.reference)).toThrow(ApiError);
  });
  it('rejects trailing line terminators in monetary strings', () => {
    expect(() => decodeReceipt({ ...mockReceipt, amountVnd: '1000\n' }, mockReceipt.reference)).toThrow(ApiError);
  });
  it('rejects a repeating cursor and receipt 403 as protocol, preserves 404', async () => {
    server.use(http.get(`${origin}/api/v1/accounts/me/history`, () => HttpResponse.json({ items: [], nextCursor: 'same' })));
    await expect(service.history('same', signal())).rejects.toMatchObject({ kind: 'protocol' });
    for (const status of [403, 404]) {
      server.use(http.get(`${origin}/api/v1/transactions/:reference/receipt`, () => HttpResponse.json({ code: 'NOT_FOUND', message: 'Unavailable' }, { status })));
      await expect(service.receipt('REF', signal())).rejects.toMatchObject({ status, kind: status === 404 ? 'http' : 'protocol' });
    }
  });
});
describe('MSW ownership and snapshot pagination', () => {
  it('traverses >100 frozen-principal records with ties, stable replay, correct receipts and no duplicates', async () => {
    await identity.login(mockFrozenCredentials);
    let cursor: string | undefined; const refs: string[] = [];
    do {
      const page = await service.history(cursor, signal());
      expect(page.items.length).toBeLessThanOrEqual(20);
      refs.push(...page.items.map(item => item.reference));
      if (cursor) expect((await service.history(cursor, signal())).items).toEqual(page.items);
      cursor = page.nextCursor ?? undefined;
    } while (cursor !== undefined);
    expect(refs).toEqual(mockFrozenHistory.map(item => item.reference)); expect(new Set(refs).size).toBe(105);
    expect((await service.receipt(refs[0]!, signal())).destinationLabel).toBe('MOCK-FROZEN-ACCOUNT');
    await expect(service.receipt(mockReceipt.reference, signal())).rejects.toMatchObject({ status: 404 });
  });
  it('binds cursors to principal/session and keeps new customer history empty and receipts private', async () => {
    await identity.login(mockFrozenCredentials); const page = await service.history(undefined, signal());
    await identity.logout(); await identity.login(mockCredentials);
    await expect(service.history(page.nextCursor!, signal())).rejects.toMatchObject({ status: 422 });
    await expect(service.receipt(mockFrozenHistory[0]!.reference, signal())).rejects.toMatchObject({ status: 404 });
    expect((await service.receipt(mockReceipt.reference, signal())).reference).toBe(mockReceipt.reference);
    await identity.logout(); await identity.register({ email: 'new@example.test', password: 'synthetic', displayName: 'New' });
    await identity.login({ email: 'new@example.test', password: 'synthetic' });
    expect(await service.history(undefined, signal())).toEqual({ items: [], nextCursor: null });
    await expect(service.receipt(mockReceipt.reference, signal())).rejects.toMatchObject({ status: 404 });
    await identity.logout(); await expect(service.history(undefined, signal())).rejects.toMatchObject({ status: 401 });
  });
  it.each(['0', '101', 'abc'])('rejects invalid limit %s', async limit => { await identity.login(mockCredentials); await expect(client.request(`/accounts/me/history?limit=${limit}`)).rejects.toMatchObject({ status: 422 }); });
});
describe('history/receipt session-scoped read store', () => {
  async function harness() {
    const api = { session: vi.fn(async (): Promise<Session> => ({ userId: 'A', role: 'CUSTOMER' })), login: async (): Promise<Session> => ({ userId: 'A', role: 'CUSTOMER' }), logout: async () => undefined, register: async () => undefined };
    const session = createSessionStore(api); await session.login(mockCredentials);
    const requests: (ReturnType<typeof deferred<string>> & { input: string | undefined; signal: AbortSignal })[] = [];
    const read = vi.fn((input: string | undefined, signal: AbortSignal) => { const request = { ...deferred<string>(), input, signal }; requests.push(request); return request.promise; });
    const store = createReadStore<string, string | undefined>(session, read, undefined); const stop = store.connect();
    return { api, session, requests, read, store, stop };
  }
  it.each(['logout', 'A', 'B', '401', 'network'])('clears immediately and rejects late account-scoped results during %s', async outcome => {
    const h = await harness();
    try {
      h.requests[0]!.resolve('PRIVATE-A'); await vi.waitFor(() => expect(h.store.getSnapshot().status).toBe('ready'));
      const old = h.store.load('opaque-page-2'); const pending = deferred<Session>(); h.api.session.mockImplementation(() => pending.promise);
      const verification = outcome === 'logout' ? h.session.logout() : h.session.refresh();
      expect(h.store.getSnapshot()).toEqual({ status: 'loading' }); expect(h.requests[1]!.signal.aborted).toBe(true);
      h.requests[1]!.resolve('LATE-PRIVATE'); await old; await h.store.retry(); expect(h.read).toHaveBeenCalledTimes(2);
      if (outcome === '401' || outcome === 'network') pending.reject(new ApiError(outcome === '401' ? 'http' : 'network', { status: outcome === '401' ? 401 : undefined }));
      else pending.resolve({ userId: outcome, role: 'CUSTOMER' });
      await verification;
      if (outcome === 'A' || outcome === 'B') { expect(h.requests[2]!.input).toBeUndefined(); h.requests[2]!.resolve('CONFIRMED'); await vi.waitFor(() => expect(h.store.getSnapshot()).toEqual({ status: 'ready', data: 'CONFIRMED' })); }
      else expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
    } finally { h.stop(); }
  });
  it('latest input wins, retries same opaque input, unmount cleanup ignores late success', async () => {
    const h = await harness();
    const newer = h.store.load('opaque'); h.requests[1]!.reject(new ApiError('network')); await newer;
    expect(h.store.getSnapshot()).toEqual({ status: 'error', reason: 'network' });
    const retry = h.store.retry(); expect(h.requests[2]!.input).toBe('opaque'); h.requests[2]!.resolve('NEW'); await retry;
    h.requests[0]!.resolve('OLD'); await Promise.resolve(); expect(h.store.getSnapshot()).toEqual({ status: 'ready', data: 'NEW' });
    const last = h.store.load('last'); h.stop(); h.requests[3]!.resolve('UNMOUNTED'); await last;
    expect(h.store.getSnapshot()).toEqual({ status: 'loading' });
  });
  it.each([[401, 'loading'], [403, 'forbidden'], [404, 'not-found'], [422, 'cursor']] as const)('maps HTTP %s without retry', async (status, reason) => {
    const h = await harness(); try { h.requests[0]!.reject(new ApiError('http', { status }));
      await vi.waitFor(() => expect(status === 401 ? h.session.getSnapshot().status : h.store.getSnapshot().status).toBe(status === 401 ? 'anonymous' : 'error'));
      expect(h.store.getSnapshot()).toMatchObject(status === 401 ? { status: 'loading' } : { status: 'error', reason }); expect(h.read).toHaveBeenCalledTimes(1);
    } finally { h.stop(); }
  });
});
