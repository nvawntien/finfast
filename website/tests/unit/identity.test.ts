import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { createHttpClient } from '../../src/api/client';
import { ApiError } from '../../src/api/errors';
import { createIdentityService, decodeSession, type Session } from '../../src/features/auth/service';
import { createSessionStore, IdentityBusyError } from '../../src/features/auth/sessionStore';
import { exceedsPasswordByteLimit, validateIdentity } from '../../src/features/auth/validation';
import { createMockApi } from '../../src/mocks/handlers';
import { mockAccount, mockCredentials, mockIdentity, mockReceipt } from '../../src/mocks/fixtures';

const origin = 'http://identity.test';
const mock = createMockApi(origin);
const server = setupServer(...mock.handlers);
const client = createHttpClient({ baseUrl: `${origin}/api/v1` });
const service = createIdentityService(client);
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => { server.resetHandlers(); mock.reset(); });
afterAll(() => server.close());
const deferred = <T>() => { let resolve!: (value: T) => void; let reject!: (reason: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

describe('identity validation and decoding', () => {
  it('requires registration name, email and password; no invented password policy', () => {
    expect(Object.keys(validateIdentity({ displayName: ' ', email: '', password: '' }, true))).toHaveLength(3);
    expect(validateIdentity({ displayName: '', email: 'a@example.test', password: 'x' }, false)).toEqual({});
    expect(validateIdentity({ displayName: 'A', email: 'not-email', password: 'x' }, true)).toHaveProperty('email');
  });
  it.each([null, [], { userId: 1, role: 'CUSTOMER' }, { userId: '1', role: 'customer' }, { userId: '1', role: 'ADMIN', displayName: null }, { ...mockIdentity, token: 'not-allowed' }])('rejects malformed session %j', value => {
    expect(() => decodeSession(value)).toThrow(ApiError);
  });
  it('takes ADMIN from the server without changing it', () => expect(decodeSession({ userId: '1', role: 'ADMIN' }).role).toBe('ADMIN'));
});

describe('identity integration over the existing HTTP client and MSW', () => {
  it('registers without logging in, rejects duplicate email and then logs in/out with rotating CSRF', async () => {
    const values = { displayName: 'New Customer', email: 'new@example.test', password: ' x ' };
    await service.register(values);
    await expect(service.session()).rejects.toMatchObject({ status: 401 });
    await expect(service.register({ ...values, email: 'NEW@example.test' })).rejects.toMatchObject({ status: 409 });
    await expect(service.login({ ...values, password: 'x' })).rejects.toMatchObject({ status: 401 });
    expect(await service.login(values)).toMatchObject({ role: 'CUSTOMER', displayName: 'New Customer' });
    await service.logout();
    await expect(service.session()).rejects.toMatchObject({ status: 401 });
  });
  it('strips untrusted role and keeps passwords unchanged in the request', async () => {
    server.use(http.post(`${origin}/api/v1/auth/login`, async ({ request }) => {
      expect(await request.json()).toEqual({ email: mockCredentials.email, password: ' secret ' });
      expect(request.headers.get('X-CSRF-TOKEN')).toBe('mock-csrf-0');
      return HttpResponse.json(mockIdentity);
    }));
    await service.login({ email: ` ${mockCredentials.email} `, password: ' secret ', role: 'ADMIN' } as typeof mockCredentials);
  });
  it('passes cookie credentials to fetch for session and CSRF/login', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async input => new Response(JSON.stringify(String(input).endsWith('/csrf') ? { token: 'csrf' } : mockIdentity), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const identity = createIdentityService(createHttpClient({ baseUrl: `${origin}/api/v1`, fetchImpl }));
    await identity.session(); await identity.login(mockCredentials);
    expect(fetchImpl).toHaveBeenCalledTimes(3);
    for (const [, init] of fetchImpl.mock.calls) expect(init?.credentials).toBe('include');
  });
  it('rejects an invalid registration response instead of reporting success', async () => {
    server.use(http.post(`${origin}/api/v1/auth/register`, () => HttpResponse.json({ balanceVnd: 0 }, { status: 201 })));
    await expect(service.register({ ...mockCredentials, displayName: 'Mock' })).rejects.toMatchObject({ kind: 'protocol' });
  });
  it('does not retry a failed login', async () => {
    let calls = 0;
    server.use(http.post(`${origin}/api/v1/auth/login`, () => { calls++; return HttpResponse.error(); }));
    await expect(service.login(mockCredentials)).rejects.toMatchObject({ kind: 'network' });
    expect(calls).toBe(1);
  });
});

describe('memory-only session lifecycle', () => {
  it('shares signing-in state and rejects a competing login without sending it', async () => {
    const pending = deferred<Session>();
    const login = vi.fn(() => pending.promise);
    const session = vi.fn(async () => decodeSession(mockIdentity));
    const store = createSessionStore({ ...service, login, session });
    const first = store.login(mockCredentials);
    expect(store.getSnapshot().status).toBe('signing-in');
    await expect(store.login({ email: 'other@example.test', password: 'different' })).rejects.toBeInstanceOf(IdentityBusyError);
    await store.refresh(); expect(session).not.toHaveBeenCalled(); expect(login).toHaveBeenCalledTimes(1);
    pending.resolve(decodeSession(mockIdentity)); await first;
    expect(store.getSnapshot().session?.userId).toBe(mockIdentity.userId);
  });
  it('queues and deduplicates logout behind login, never publishing the superseded principal', async () => {
    const login = deferred<Session>(); const logout = deferred<void>(); const logoutCall = vi.fn(() => logout.promise);
    const store = createSessionStore({ ...service, login: () => login.promise, logout: logoutCall });
    const states: string[] = []; store.subscribe(() => states.push(store.getSnapshot().status));
    const first = store.login(mockCredentials); const signout = store.logout();
    expect(store.logout()).toBe(signout);
    expect(store.getSnapshot()).toEqual({ status: 'signing-out', waitingForLogin: true });
    expect(logoutCall).not.toHaveBeenCalled();
    await expect(store.login(mockCredentials)).rejects.toBeInstanceOf(IdentityBusyError);
    login.resolve(decodeSession(mockIdentity)); await first;
    await vi.waitFor(() => expect(logoutCall).toHaveBeenCalledTimes(1));
    expect(states).not.toContain('authenticated');
    logout.resolve(); await signout;
    expect(store.getSnapshot().status).toBe('anonymous');
  });
  it.each([401, 'timeout'] as const)('handles queued logout after login fails with %s', async failure => {
    const pending = deferred<Session>(); const logout = vi.fn(async () => undefined);
    const store = createSessionStore({ ...service, login: () => pending.promise, logout });
    const first = store.login(mockCredentials); const signout = store.logout();
    pending.reject(failure === 401 ? new ApiError('http', { status: 401 }) : new ApiError('timeout', { requestMayHaveBeenSent: true }));
    await first; await signout;
    expect(logout).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot()).toEqual(failure === 401 ? { status: 'anonymous' } : { status: 'error', logoutUncertain: true });
  });
  it('ignores a stale successful session refresh after queued logout', async () => {
    const old = deferred<Session>(); const login = deferred<Session>();
    const store = createSessionStore({ ...service, session: () => old.promise, login: () => login.promise, logout: async () => undefined });
    const refresh = store.refresh(); const first = store.login(mockCredentials); const signout = store.logout();
    login.resolve(decodeSession(mockIdentity)); await first; await signout;
    old.resolve(decodeSession(mockIdentity)); await refresh;
    expect(store.getSnapshot().status).toBe('anonymous');
  });
  it('does not treat malformed 401 responses as verified logout or expiry', async () => {
    const malformed = async () => { throw new ApiError('protocol', { status: 401 }); };
    const store = createSessionStore({ ...service, session: malformed, logout: malformed });
    await store.refresh(); expect(store.getSnapshot().status).toBe('error');
    await store.logout(); expect(store.getSnapshot()).toEqual({ status: 'error', logoutUncertain: true });
  });
  it('expires a known session after 401 and fails closed on connection errors', async () => {
    const store = createSessionStore(service);
    await store.login(mockCredentials);
    server.use(http.get(`${origin}/api/v1/auth/session`, () => HttpResponse.json({ code: 'EXPIRED', message: 'Expired' }, { status: 401 })));
    await store.refresh(); expect(store.getSnapshot()).toEqual({ status: 'anonymous', expired: true });
    server.use(http.get(`${origin}/api/v1/auth/session`, () => HttpResponse.error()));
    await store.refresh(); expect(store.getSnapshot().status).toBe('error');
  });
  it('ignores stale session responses after login', async () => {
    const old = deferred<Session>();
    const store = createSessionStore({ ...service, session: () => old.promise });
    const pending = store.refresh();
    await store.login(mockCredentials);
    old.reject(new ApiError('http', { status: 401 })); await pending;
    expect(store.getSnapshot().status).toBe('authenticated');
  });
  it('deduplicates refresh, cleans subscriptions, and preserves logout uncertainty', async () => {
    const old = deferred<Session>(); const session = vi.fn(() => old.promise);
    const store = createSessionStore({ ...service, session, logout: async () => { throw new ApiError('network'); } });
    const listener = vi.fn(); const unsubscribe = store.subscribe(listener);
    const pending = store.refresh(); void store.refresh(); await Promise.resolve(); expect(session).toHaveBeenCalledTimes(1);
    old.resolve(decodeSession(mockIdentity)); await pending; expect(listener).toHaveBeenCalledTimes(2);
    unsubscribe(); await store.logout(); expect(listener).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot()).toEqual({ status: 'error', logoutUncertain: true });
  });
  it('accepts logout 401 as already anonymous', async () => {
    const store = createSessionStore(service); await store.logout(); expect(store.getSnapshot().status).toBe('anonymous');
  });
  it('does not let background refresh override an in-flight logout', async () => {
    const logout = deferred<void>(); const session = vi.fn(async () => decodeSession(mockIdentity));
    const store = createSessionStore({ ...service, session, logout: () => logout.promise });
    await store.login(mockCredentials); const pending = store.logout(); await store.refresh();
    expect(session).not.toHaveBeenCalled(); expect(store.getSnapshot().status).toBe('signing-out');
    logout.resolve(); await pending; expect(store.getSnapshot().status).toBe('anonymous');
  });
});

describe('per-customer mock ownership', () => {
  it('isolates two registrations, accounts, sessions, history and receipts from the seeded customer', async () => {
    const customers = [{ displayName: 'A', email: 'a@example.test', password: 'synthetic-A' }, { displayName: 'B', email: 'b@example.test', password: 'synthetic-B' }];
    const registrations: { customerId: string; accountNumber: string }[] = [];
    for (const customer of customers) registrations.push((await client.request('/auth/register', { method: 'POST', body: customer })).data as typeof registrations[number]);
    expect(registrations[0]!.customerId).not.toBe(registrations[1]!.customerId);
    expect(registrations[0]!.accountNumber).not.toBe(registrations[1]!.accountNumber);
    const users: string[] = [];
    for (const [index, customer] of customers.entries()) {
      const session = await service.login(customer); users.push(session.userId);
      expect(session.userId).not.toBe(registrations[index]!.customerId);
      expect(await service.session()).toEqual(session);
      expect((await client.request('/accounts/me')).data).toEqual({ accountNumber: registrations[index]!.accountNumber, type: 'CUSTOMER', status: 'ACTIVE', currency: 'VND', balanceVnd: '0', availableBalanceVnd: '0' });
      expect((await client.request('/accounts/me/history')).data).toEqual({ items: [], nextCursor: null });
      await expect(client.request(`/transactions/${mockReceipt.reference}/receipt`)).rejects.toMatchObject({ kind: 'http', status: 404, code: 'NOT_FOUND' });
      await service.logout();
      for (const endpoint of ['/auth/session', '/accounts/me', '/accounts/me/history', `/transactions/${mockReceipt.reference}/receipt`]) {
        await expect(client.request(endpoint)).rejects.toMatchObject({ kind: 'http', status: 401 });
      }
    }
    expect(new Set(users).size).toBe(2);
    expect(users).not.toContain(mockIdentity.userId);
    await service.login(mockCredentials);
    expect((await client.request('/accounts/me')).data).toEqual(mockAccount);
    expect((await client.request(`/transactions/${mockReceipt.reference}/receipt`)).data).toEqual(mockReceipt);
    await service.logout();
    await service.login(customers[0]!);
    expect((await client.request('/accounts/me')).data).toMatchObject({ accountNumber: registrations[0]!.accountNumber, balanceVnd: '0' });
  });
  it('keeps concurrent duplicate registration atomic', async () => {
    const values = { displayName: 'Synthetic', email: 'same@example.test', password: 'synthetic' };
    const results = await Promise.allSettled([service.register(values), service.register(values)]);
    expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1);
    expect(results.find(result => result.status === 'rejected')).toMatchObject({ reason: { kind: 'http', status: 409 } });
  });
});

describe('TD-007 UTF-8 byte capacity', () => {
  it.each(['a'.repeat(72), 'é'.repeat(36), '😀'.repeat(18)])('accepts exactly 72 bytes without truncation or normalization', async password => {
    expect(exceedsPasswordByteLimit(password)).toBe(false);
    const values = { displayName: 'Synthetic', email: 'boundary@example.test', password };
    expect(validateIdentity(values, true)).toEqual({});
    await service.register(values); await service.login(values); await service.logout();
    await expect(service.login({ ...values, password: `${password}a` })).rejects.toMatchObject({ kind: 'http', status: 401 });
    await expect(service.login({ ...values, password: password.slice(0, -1) })).rejects.toMatchObject({ kind: 'http', status: 401 });
  });
  it.each(['a'.repeat(73), `${'é'.repeat(36)}a`, `${'😀'.repeat(18)}a`])('rejects 73 bytes before saving a registration', async password => {
    const values = { displayName: 'Synthetic', email: 'rejected@example.test', password };
    expect(exceedsPasswordByteLimit(password)).toBe(true);
    expect(validateIdentity(values, true)).toHaveProperty('password');
    expect(validateIdentity(values, false)).toHaveProperty('password');
    await expect(service.register(values)).rejects.toMatchObject({ kind: 'http', status: 422, code: 'INVALID_REQUEST', serverMessage: 'Password exceeds 72 UTF-8 bytes.' });
    await expect(service.login(values)).rejects.toMatchObject({ kind: 'http', status: 401 });
    await service.register({ ...values, password: 'valid-synthetic' });
  });
});
