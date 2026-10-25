import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../src/api/client';
import { createTransferService, decodeTransferResult, type TransferResult } from '../../src/features/transfers/service';
import { createTransferStore } from '../../src/features/transfers/transferStore';
import { createSessionStore } from '../../src/features/auth/sessionStore';
import type { Session } from '../../src/features/auth/service';
import { parsePositiveMoneyVnd } from '../../src/lib/money';
const draft = { recipientAccountNumber: 'DEST', amountVnd: parsePositiveMoneyVnd('9007199254740993'), note: ' exact\n ' };
const deferred = <T>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(yes => { resolve = yes; }); return { promise, resolve }; };

describe('transfer response decoder', () => {
  it('accepts committed reference and exact optional amount', () => expect(decodeTransferResult(200, { outcome: 'COMPLETED', reference: 'REF', amountVnd: draft.amountVnd, currency: 'VND', committedAt: '2026-10-25T00:00:00Z' }, draft)).toEqual({ outcome: 'COMPLETED', reference: 'REF' }));
  it.each(['PROCESSING', 'UNKNOWN'])('preserves 202 %s', outcome => expect(decodeTransferResult(202, { outcome }, draft)).toEqual({ outcome }));
  it.each([
    {}, null, { outcome: ['COMPLETED'], reference: 'REF' }, { outcome: 'COMPLETED' }, { outcome: 'COMPLETED', reference: '' },
    { outcome: 'REJECTED' }, { outcome: 'PROCESSING' }, { outcome: 'UNKNOWN' },
    { outcome: 'COMPLETED', reference: 'REF', amountVnd: '1' }, { outcome: 'COMPLETED', reference: 'REF', amountVnd: 1 },
    { outcome: 'COMPLETED', reference: 'REF', currency: 'USD' }, { outcome: 'COMPLETED', reference: 'REF', code: [] },
    { outcome: 'COMPLETED', reference: 'REF', committedAt: '2026-02-30T00:00:00Z' }, { outcome: 'COMPLETED', reference: 'REF', extra: true },
  ])('rejects invalid 200 instead of claiming commit %j', body => expect(() => decodeTransferResult(200, body, draft)).toThrow());
  it.each([{ outcome: 'COMPLETED', reference: 'REF' }, { outcome: 'REJECTED' }, { outcome: 'PROCESSING', reference: 'REF' }, { outcome: 'UNKNOWN', committedAt: '2026-10-25T00:00:00Z' }])('rejects contradictory 202 %j', body => expect(() => decodeTransferResult(202, body, draft)).toThrow());
});
describe('actual HTTP client/service mapping and immutable intent', () => {
  function harness(status: number, body: unknown) {
    const fetchImpl = vi.fn<typeof fetch>(async input => String(input).endsWith('/auth/csrf') ? Response.json({ token: 'csrf' }) : Response.json(body, { status }));
    return { fetchImpl, service: createTransferService(createHttpClient({ baseUrl: 'https://unit.test/api/v1', fetchImpl })) };
  }
  it.each([
    [200, { outcome: 'COMPLETED', reference: 'REF' }, 'COMPLETED'],
    [200, { outcome: 'COMPLETED' }, 'UNKNOWN'],
    [202, { outcome: 'PROCESSING' }, 'PROCESSING'], [202, { outcome: 'UNKNOWN' }, 'UNKNOWN'],
    [409, { code: 'IDEMPOTENCY_CONFLICT', message: 'Conflict' }, 'UNKNOWN'],
    [422, { outcome: 'REJECTED', code: 'INSUFFICIENT_FUNDS', message: 'No funds' }, 'REJECTED'],
    [422, { code: 'ERROR', message: 'No outcome' }, 'UNKNOWN'],
    [422, { outcome: 'REJECTED', code: 'ERROR', message: 'Extra property', extra: true }, 'UNKNOWN'],
    [422, { outcome: ['REJECTED'], code: 'ERROR', message: 'Bad type' }, 'UNKNOWN'],
    [503, { outcome: 'REJECTED', code: 'UNAVAILABLE', message: 'Uncertain' }, 'UNKNOWN'],
    [401, { code: 'UNAUTHENTICATED', message: 'Expired' }, 'UNKNOWN'], [403, { code: 'FORBIDDEN', message: 'Denied' }, 'UNKNOWN'],
    [500, { code: 'ERROR', message: 'Undocumented' }, 'UNKNOWN'],
  ] as const)('maps %s body=%j to %s without retry', async (status, body, outcome) => {
    const h = harness(status, body); expect((await h.service.submit(draft, 'original', new AbortController().signal)).outcome).toBe(outcome);
    expect(h.fetchImpl).toHaveBeenCalledTimes(2);
    expect(h.fetchImpl.mock.calls[1]?.[1]).toMatchObject({ method: 'POST', credentials: 'include', body: JSON.stringify(draft) });
    const headers = new Headers(h.fetchImpl.mock.calls[1]?.[1]?.headers); expect(headers.get('Idempotency-Key')).toBe('original'); expect(headers.get('X-CSRF-TOKEN')).toBe('csrf');
  });
  it.each(['network', 'stream', 'timeout', 'abort'])('keeps %s unknown and never retries', async failure => {
    const abort = new AbortController();
    vi.useFakeTimers();
    try {
      const fetchImpl = vi.fn<typeof fetch>(async (input, init) => {
        if (String(input).endsWith('/auth/csrf')) return Response.json({ token: 'csrf' });
        if (failure === 'network') throw new TypeError('offline');
        if (failure === 'stream') return new Response(new ReadableStream({ start(controller) { controller.error(new TypeError('interrupted')); } }), { headers: { 'Content-Type': 'application/json' } });
        return new Promise<Response>((_, reject) => init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError'))));
      });
      const service = createTransferService(createHttpClient({ baseUrl: 'https://unit.test/api/v1', fetchImpl }));
      const promise = service.submit(draft, 'original', abort.signal); await vi.advanceTimersByTimeAsync(0);
      if (failure === 'abort') abort.abort(); else if (failure === 'timeout') await vi.advanceTimersByTimeAsync(15001);
      expect((await promise).outcome).toBe('UNKNOWN'); expect(fetchImpl).toHaveBeenCalledTimes(2);
    } finally { vi.useRealTimers(); }
  });
  it('CSRF failure never dispatches mutation or becomes financial rejection', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () => Response.json({ code: 'ERROR', message: 'CSRF failed', outcome: 'REJECTED' }, { status: 422 }));
    const service = createTransferService(createHttpClient({ baseUrl: 'https://unit.test/api/v1', fetchImpl }));
    expect((await service.submit(draft, 'key', new AbortController().signal)).outcome).toBe('UNKNOWN'); expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
});
describe('transfer store ownership and lifecycle invariants', () => {
  async function harness() {
    let principal: Session = { userId: 'A', role: 'CUSTOMER' };
    const api = { session: vi.fn(async () => principal), login: async () => principal, logout: async () => undefined, register: async () => undefined };
    const session = createSessionStore(api); await session.login({ email: 'a', password: 'synthetic' });
    const pending = deferred<TransferResult>(); const submit = vi.fn((_payload: typeof draft | import('../../src/features/transfers/draft').TransferDraft, _key: string, _signal: AbortSignal) => pending.promise);
    let number = 0; const key = vi.fn(() => `key-${++number}`);
    const store = createTransferStore(session, { submit }, key); const stop = store.connect();
    return { api, session, pending, submit, key, store, stop, principal: (value: Session) => { principal = value; } };
  }
  it('locks synchronously, snapshots before first await and keeps one key/payload', async () => {
    const h = await harness(); try {
      const mutable = { ...draft }; const first = h.store.submit(mutable, h.session.getScope()); const second = h.store.submit(draft, h.session.getScope());
      expect(second).toBe(first); mutable.note = 'mutated'; mutable.recipientAccountNumber = 'OTHER';
      await Promise.resolve(); expect(h.submit).toHaveBeenCalledTimes(1); expect(h.key).toHaveBeenCalledTimes(1);
      expect(h.submit.mock.calls[0]?.[0]).toEqual(draft);
      h.pending.resolve({ outcome: 'UNKNOWN' }); await first;
      h.store.newIntent(); await h.store.submit(draft, h.session.getScope()); expect(h.submit).toHaveBeenCalledTimes(1);
      const state = h.store.getSnapshot(); expect(state.phase).toBe('settled'); if ('intent' in state) { expect(state.intent.key).toBe('key-1'); expect(Object.isFrozen(state.intent.payload)).toBe(true); }
    } finally { h.stop(); }
  });
  it.each(['COMPLETED', 'REJECTED'] as const)('only explicit new intent after %s creates a fresh key', async outcome => {
    const h = await harness(); try {
      const first = h.store.submit(draft, h.session.getScope()); h.pending.resolve(outcome === 'COMPLETED' ? { outcome, reference: 'REF' } : { outcome, code: 'ERROR', message: 'Rejected' }); await first;
      await h.store.submit(draft, h.session.getScope()); expect(h.key).toHaveBeenCalledTimes(1);
      h.store.newIntent(); await h.store.submit(draft, h.session.getScope()); expect(h.key).toHaveBeenCalledTimes(2);
    } finally { h.stop(); }
  });
  it.each(['logout', 'revalidation', 'B', 'ADMIN'])('hides intent on %s, ignores late completion, retains A unknown', async change => {
    const h = await harness(); try {
      const first = h.store.submit(draft, h.session.getScope()); await Promise.resolve();
      const check = deferred<Session>(); h.api.session.mockImplementation(() => check.promise);
      if (change === 'logout') await h.session.logout();
      else { void h.session.refresh(); if (change !== 'revalidation') { check.resolve({ userId: change === 'B' ? 'B' : 'A', role: change === 'ADMIN' ? 'ADMIN' : 'CUSTOMER' }); await Promise.resolve(); await Promise.resolve(); } }
      expect(h.store.getSnapshot()).toEqual({ phase: 'idle' });
      h.pending.resolve({ outcome: 'COMPLETED', reference: 'PRIVATE-A' }); await first;
      if (change === 'revalidation') { check.resolve({ userId: 'A', role: 'CUSTOMER' }); await Promise.resolve(); await Promise.resolve(); }
      else { h.principal({ userId: 'A', role: 'CUSTOMER' }); await h.session.login({ email: 'a', password: 'synthetic' }); }
      expect(h.store.getSnapshot()).toMatchObject({ phase: 'settled', result: { outcome: 'UNKNOWN' }, intent: { key: 'key-1' } });
      h.store.newIntent(); await h.store.submit(draft, h.session.getScope()); expect(h.key).toHaveBeenCalledTimes(1);
    } finally { h.stop(); }
  });
  it('rejects stale review scope and cannot dispatch during revalidation', async () => {
    const h = await harness(); try {
      const scope = h.session.getScope(); const check = h.session.refresh(); await h.store.submit(draft, scope); await check;
      await h.store.submit(draft, scope); expect(h.submit).not.toHaveBeenCalled(); expect(h.key).not.toHaveBeenCalled();
    } finally { h.stop(); }
  });
  it('PROCESSING cannot be reset or replaced with a new key', async () => {
    const h = await harness(); try {
      const first = h.store.submit(draft, h.session.getScope()); h.pending.resolve({ outcome: 'PROCESSING' }); await first;
      h.store.newIntent(); await h.store.submit(draft, h.session.getScope());
      expect(h.store.getSnapshot()).toMatchObject({ result: { outcome: 'PROCESSING' }, intent: { key: 'key-1' } }); expect(h.key).toHaveBeenCalledTimes(1);
    } finally { h.stop(); }
  });
  it('late A session-denial cannot expire or overwrite a B command', async () => {
    const h = await harness(); const secondResponse = deferred<TransferResult>();
    try {
      const first = h.store.submit(draft, h.session.getScope()); await Promise.resolve();
      h.principal({ userId: 'B', role: 'CUSTOMER' }); await h.session.refresh();
      expect(h.store.getSnapshot()).toEqual({ phase: 'idle' });
      h.submit.mockImplementationOnce(() => secondResponse.promise);
      const second = h.store.submit({ ...draft, recipientAccountNumber: 'B-DEST' }, h.session.getScope()); await Promise.resolve();
      h.pending.resolve({ outcome: 'UNKNOWN', reason: 'session' }); await first;
      expect(h.session.getSnapshot()).toMatchObject({ status: 'authenticated', session: { userId: 'B' } });
      expect(h.store.getSnapshot()).toMatchObject({ phase: 'submitting', intent: { owner: 'B', key: 'key-2', payload: { recipientAccountNumber: 'B-DEST' } } });
      secondResponse.resolve({ outcome: 'COMPLETED', reference: 'B-REF' }); await second;
      expect(h.store.getSnapshot()).toMatchObject({ result: { outcome: 'COMPLETED', reference: 'B-REF' } });
    } finally { h.stop(); }
  });
  it('session denial expires current scope and keeps UNKNOWN for reauthentication', async () => {
    const h = await harness(); try {
      const first = h.store.submit(draft, h.session.getScope()); h.pending.resolve({ outcome: 'UNKNOWN', reason: 'session' }); await first;
      expect(h.session.getSnapshot()).toMatchObject({ status: 'anonymous', expired: true });
      await h.session.login({ email: 'a', password: 'synthetic' }); expect(h.store.getSnapshot()).toMatchObject({ result: { outcome: 'UNKNOWN', reason: 'session' } });
    } finally { h.stop(); }
  });
});
