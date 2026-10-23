import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { createHttpClient } from '../../src/api/client';
import { createMockApi } from '../../src/mocks/handlers';
import { mockCredentials } from '../../src/mocks/fixtures';

const origin = 'https://finfast.test';
const baseUrl = `${origin}/api/v1`;
const mock = createMockApi(origin);
const server = setupServer(...mock.handlers);
const client = () => createHttpClient({ baseUrl });
const transfer = { recipientAccountNumber: 'MOCK-ACCOUNT-002', amountVnd: '9007199254740993' };
const transferOptions = { method: 'POST' as const, body: transfer, idempotencyKey: 'original-key' };

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => { server.resetHandlers(); mock.reset(); });
afterAll(() => server.close());

describe('fetch boundary and CSRF', () => {
  it('includes cookies, disables redirects/cache, does not fetch CSRF for GET', async () => {
    const spy = vi.fn(globalThis.fetch);
    const api = createHttpClient({ baseUrl, fetchImpl: spy });
    await expect(api.request('/auth/session')).rejects.toMatchObject({ kind: 'http', status: 401 });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy.mock.calls[0][1]).toMatchObject({ credentials: 'include', redirect: 'error', cache: 'no-store' });
  });

  it('fetches fresh CSRF for register, login and logout; handles rotation', async () => {
    const spy = vi.fn(globalThis.fetch);
    const api = createHttpClient({ baseUrl, fetchImpl: spy });
    await api.request('/auth/register', { method: 'POST', body: { ...mockCredentials, displayName: 'Synthetic' } });
    await api.request('/auth/login', { method: 'POST', body: mockCredentials });
    await api.request('/auth/logout', { method: 'POST' });
    expect(spy).toHaveBeenCalledTimes(6);
    expect(spy.mock.calls.filter(([url]) => String(url).endsWith('/csrf'))).toHaveLength(3);
    expect(new Headers(spy.mock.calls[1][1]?.headers).get('X-CSRF-TOKEN')).toBe('mock-csrf-0');
    expect(new Headers(spy.mock.calls[5][1]?.headers).get('X-CSRF-TOKEN')).toBe('mock-csrf-1');
    for (const [, init] of spy.mock.calls) expect(init?.credentials).toBe('include');
    await expect(api.request('/auth/session')).rejects.toMatchObject({ status: 401 });
  });

  it('blocks mutation when CSRF payload is invalid', async () => {
    server.use(http.get(`${baseUrl}/auth/csrf`, () => HttpResponse.json({ token: '' })));
    const spy = vi.fn(globalThis.fetch);
    const api = createHttpClient({ baseUrl, fetchImpl: spy });
    await expect(api.request('/transfers', transferOptions)).rejects.toMatchObject({ kind: 'protocol', requestMayHaveBeenSent: false });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('does not retry CSRF/network failure or send the mutation', async () => {
    server.use(http.get(`${baseUrl}/auth/csrf`, () => HttpResponse.error()));
    const spy = vi.fn(globalThis.fetch);
    await expect(createHttpClient({ baseUrl, fetchImpl: spy }).request('/transfers', transferOptions))
      .rejects.toMatchObject({ kind: 'network', financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: false });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('snapshots exact payload including note and original key before awaiting CSRF', async () => {
    let observed: { data: unknown; key: string | null; type: string | null } | undefined;
    server.use(http.post(`${baseUrl}/transfers`, async ({ request }) => {
      observed = { data: await request.json(), key: request.headers.get('Idempotency-Key'), type: request.headers.get('Content-Type') };
      return HttpResponse.json({ outcome: 'UNKNOWN' }, { status: 202 });
    }));
    const body = { ...transfer, note: '' };
    const response = client().request('/transfers', { ...transferOptions, body });
    body.note = 'changed by caller';
    await response;
    expect(observed).toEqual({ data: { ...transfer, note: '' }, key: 'original-key', type: 'application/json' });
    expect(body.note).toBe('changed by caller');
  });

  it.each(['//attacker.test/auth/session', '/../../auth/session', '/auth/session#secret', '/auth\\session'])('rejects escaping URL %s before fetch', async path => {
    const fetchImpl = vi.fn();
    await expect(createHttpClient({ baseUrl, fetchImpl }).request(path)).rejects.toBeInstanceOf(TypeError);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('requires caller-provided key; rejects numeric money without rounding', async () => {
    await expect(client().request('/transfers', { method: 'POST', body: transfer })).rejects.toBeInstanceOf(TypeError);
    await expect(client().request('/transfers', { ...transferOptions, body: { ...transfer, amountVnd: 100 } })).rejects.toBeInstanceOf(TypeError);
  });
});

describe('response semantics', () => {
  it('preserves a documented committed result without rounding its amount', async () => {
    const result = { outcome: 'COMPLETED', reference: 'MOCK-COMMITTED', amountVnd: '9007199254740993', currency: 'VND', committedAt: '2026-10-23T00:00:00Z' };
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json(result)));
    expect(await client().request('/transfers', transferOptions)).toEqual({ status: 200, data: result });
  });

  it.each([401, 403, 409, 503])('maps transfer HTTP %i without claiming final failure', async status => {
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json({ code: `TEST_${status}`, message: 'Synthetic failure' }, { status })));
    await expect(client().request('/transfers', transferOptions)).rejects.toMatchObject({ kind: 'http', status, code: `TEST_${status}`, financialOutcome: 'UNKNOWN' });
  });

  it('distinguishes final financial 422 from ordinary validation 422', async () => {
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json({ outcome: 'REJECTED', code: 'INSUFFICIENT_FUNDS', message: 'Synthetic rejection' }, { status: 422 })));
    await expect(client().request('/transfers', transferOptions)).rejects.toMatchObject({ financialOutcome: 'REJECTED', code: 'INSUFFICIENT_FUNDS' });
    await expect(client().request('/auth/register', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 422, financialOutcome: undefined });
  });

  it('rejects financial 422 missing outcome as a protocol error', async () => {
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json({ code: 'BAD', message: 'Missing outcome' }, { status: 422 })));
    await expect(client().request('/transfers', transferOptions)).rejects.toMatchObject({ kind: 'protocol', financialOutcome: 'UNKNOWN' });
  });

  it.each([400, 500])('keeps undocumented HTTP %i as API-ERR-01 protocol gap', async status => {
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json({ code: 'INVALID_REQUEST', message: 'Synthetic' }, { status })));
    await expect(client().request('/transfers', transferOptions)).rejects.toMatchObject({ kind: 'protocol', status, financialOutcome: 'UNKNOWN' });
  });

  it.each(['PROCESSING', 'UNKNOWN'])('preserves 202 %s instead of claiming success', async outcome => {
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json({ outcome }, { status: 202 })));
    expect(await client().request('/transfers', transferOptions)).toEqual({ status: 202, data: { outcome } });
  });

  it('preserves resolved REJECTED under HTTP 200', async () => {
    server.use(http.post(`${baseUrl}/request-outcomes/resolve`, () => HttpResponse.json({ outcome: 'REJECTED', code: 'INSUFFICIENT_FUNDS' })));
    expect(await client().request('/request-outcomes/resolve', { method: 'POST', body: { operationType: 'INTERNAL_TRANSFER', idempotencyKey: 'original-key' } }))
      .toMatchObject({ status: 200, data: { outcome: 'REJECTED' } });
  });

  it('does not treat missing outcome lookup as proof of financial failure', async () => {
    server.use(http.post(`${baseUrl}/request-outcomes/resolve`, () => HttpResponse.json({ code: 'NOT_FOUND', message: 'Unavailable' }, { status: 404 })));
    await expect(client().request('/request-outcomes/resolve', { method: 'POST', body: {} }))
      .rejects.toMatchObject({ status: 404, financialOutcome: 'UNKNOWN' });
  });

  it('rejects a COMPLETED response under 202', async () => {
    server.use(http.post(`${baseUrl}/transfers`, () => HttpResponse.json({ outcome: 'COMPLETED' }, { status: 202 })));
    await expect(client().request('/transfers', transferOptions)).rejects.toMatchObject({ kind: 'protocol', financialOutcome: 'UNKNOWN' });
  });

  it('preserves exact large VND strings in responses', async () => {
    const api = client();
    await api.request('/auth/login', { method: 'POST', body: mockCredentials });
    expect((await api.request('/accounts/me')).data).toMatchObject({ balanceVnd: '9007199254740993' });
  });

  it.each([HttpResponse.html('<h1>Gateway failure</h1>', { status: 503 }), HttpResponse.json({ balanceVnd: 9007199254740992 })])('handles non-JSON and numeric money safely', async response => {
    server.use(http.get(`${baseUrl}/accounts/me`, () => response));
    await expect(client().request('/accounts/me')).rejects.toMatchObject({ kind: 'protocol' });
  });
});

describe('uncertainty and cancellation', () => {
  it('network failure after dispatch retains uncertainty and never retries', async () => {
    let count = 0;
    server.use(http.post(`${baseUrl}/transfers`, () => { count++; return HttpResponse.error(); }));
    await expect(client().request('/transfers', transferOptions)).rejects.toMatchObject({ kind: 'network', financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: true });
    expect(count).toBe(1);
  });

  it('times out while fetching CSRF without sending a mutation', async () => {
    server.use(http.get(`${baseUrl}/auth/csrf`, async () => { await delay(50); return HttpResponse.json({ token: 'late' }); }));
    await expect(client().request('/transfers', { ...transferOptions, timeoutMs: 10 })).rejects.toMatchObject({ kind: 'timeout', requestMayHaveBeenSent: false });
  });

  it('times out after mutation dispatch without implying rollback', async () => {
    server.use(http.post(`${baseUrl}/transfers`, async () => { await delay(100); return HttpResponse.json({ outcome: 'COMPLETED' }); }));
    await expect(client().request('/transfers', { ...transferOptions, timeoutMs: 50 })).rejects.toMatchObject({ kind: 'timeout', financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: true });
  });

  it('supports an already-aborted caller signal without a request', async () => {
    const controller = new AbortController(); controller.abort();
    const fetchImpl = vi.fn();
    await expect(createHttpClient({ baseUrl, fetchImpl }).request('/auth/session', { signal: controller.signal })).rejects.toMatchObject({ kind: 'aborted', requestMayHaveBeenSent: false });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('caller cancellation after dispatch remains uncertain', async () => {
    const controller = new AbortController();
    server.use(http.post(`${baseUrl}/transfers`, async () => {
      controller.abort();
      await delay(10);
      return HttpResponse.json({ outcome: 'COMPLETED' });
    }));
    await expect(client().request('/transfers', { ...transferOptions, signal: controller.signal }))
      .rejects.toMatchObject({ kind: 'aborted', financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: true });
  });
});
