import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../src/api/client';
import { createTransferService, type TransferResult } from '../../src/features/transfers/service';
import { createTransferStore, type TransferIntent } from '../../src/features/transfers/transferStore';
import { recoveryCanonical, recoveryFingerprint, RecoveryBusy, type RecoveryVault } from '../../src/features/transfers/recoveryVault';
import { createSessionStore } from '../../src/features/auth/sessionStore';
import { parsePositiveMoneyVnd } from '../../src/lib/money';
const payload = { recipientAccountNumber: 'DEST', amountVnd: parsePositiveMoneyVnd('9007199254740993'), note: ' exact ' };
const intent: TransferIntent = { owner: 'A', key: 'original-key', payload };
const deferred = <T>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(yes => { resolve = yes; }); return { promise, resolve }; };
const tick = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function vault(initial?: TransferIntent) {
  const records = new Map<string, TransferIntent>(initial ? [[initial.owner, initial]] : []);
  const locks = new Set<string>();
  const listeners = new Set<() => void>();
  const port: RecoveryVault = {
    async lock(owner, work) { if (locks.has(owner)) throw new RecoveryBusy(); locks.add(owner); try { return await work(); } finally { locks.delete(owner); } },
    read: vi.fn(async owner => records.get(owner)),
    write: vi.fn(async value => { records.set(value.owner, value); }),
    remove: vi.fn(async owner => { records.delete(owner); }), subscribe: listener => { listeners.add(listener); return () => { listeners.delete(listener); }; },
  };
  return { port, records, changed: () => listeners.forEach(listener => listener()) };
}
async function setup(port?: RecoveryVault) {
  let owner = 'A';
  const identity = { session: async () => ({ userId: owner, role: 'CUSTOMER' as const }), login: async () => ({ userId: owner, role: 'CUSTOMER' as const }), logout: async () => undefined, register: async () => undefined };
  const session = createSessionStore(identity); await session.refresh();
  const submit = vi.fn(async (): Promise<TransferResult> => ({ outcome: 'UNKNOWN' }));
  const resolve = vi.fn(async (): Promise<TransferResult> => ({ outcome: 'UNKNOWN' }));
  const key = vi.fn(() => 'new-key'); const store = createTransferStore(session, { submit, resolve }, key, port);
  const stop = store.connect(); await tick();
  return { store, stop, session, submit, resolve, key, change: (value: string) => { owner = value; } };
}
describe('resolve contract via real HTTP client', () => {
  it.each([
    [200, { outcome: 'COMPLETED', reference: 'REF' }, 'COMPLETED'],
    [200, { outcome: 'REJECTED' }, 'REJECTED'], [200, { outcome: 'PROCESSING' }, 'PROCESSING'], [200, { outcome: 'UNKNOWN' }, 'UNKNOWN'],
    [202, { outcome: 'PROCESSING' }, 'PROCESSING'], [202, { outcome: 'UNKNOWN' }, 'UNKNOWN'],
    [200, { outcome: 'COMPLETED' }, 'UNKNOWN'], [200, { outcome: ['COMPLETED'], reference: 'REF' }, 'UNKNOWN'],
    [200, { outcome: 'REJECTED', reference: 'REF' }, 'UNKNOWN'], [202, { outcome: 'REJECTED' }, 'UNKNOWN'],
    [200, { outcome: 'COMPLETED', reference: 'REF', amountVnd: '1' }, 'UNKNOWN'],
    [200, { outcome: 'COMPLETED', reference: 'REF', amountVnd: 1 }, 'UNKNOWN'],
    ...[401, 403, 404, 409, 422, 503].map(status => [status, { code: 'ERROR', message: 'Not final', outcome: 'REJECTED' }, 'UNKNOWN'] as [number, unknown, string]),
  ])('%s/%j returns only the validated body outcome', async (status, body, outcome) => {
    const fetchImpl = vi.fn<typeof fetch>(async input => String(input).endsWith('/auth/csrf') ? Response.json({ token: 'csrf' }) : Response.json(body, { status: status as number }));
    const service = createTransferService(createHttpClient({ baseUrl: 'https://unit.test/api/v1', fetchImpl }));
    expect((await service.resolve(payload, intent.key, new AbortController().signal)).outcome).toBe(outcome);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    const [url, init] = fetchImpl.mock.calls[1]!;
    expect(String(url)).toBe('https://unit.test/api/v1/request-outcomes/resolve');
    expect(init?.credentials).toBe('include'); expect(init?.method).toBe('POST');
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('csrf');
    expect(JSON.parse(init?.body as string)).toEqual({ operationType: 'INTERNAL_TRANSFER', idempotencyKey: 'original-key' });
    expect(new Headers(init?.headers).has('Idempotency-Key')).toBe(false);
  });
  it.each(['timeout', 'network'])('%s remains UNKNOWN without retry', async kind => {
    vi.useFakeTimers();
    try {
      const fetchImpl = vi.fn<typeof fetch>(async (input, init) => {
        if (String(input).endsWith('/auth/csrf')) return Response.json({ token: 'csrf' });
        if (kind === 'network') throw new TypeError('offline');
        return new Promise((_resolve, reject) => init?.signal?.addEventListener('abort', () => reject(new Error('aborted')), { once: true }));
      });
      const service = createTransferService(createHttpClient({ baseUrl: 'https://unit.test/api/v1', fetchImpl }));
      const result = service.resolve(payload, intent.key, new AbortController().signal);
      await vi.advanceTimersByTimeAsync(15001);
      expect(await result).toEqual({ outcome: 'UNKNOWN', reason: kind }); expect(fetchImpl).toHaveBeenCalledTimes(2);
    } finally { vi.useRealTimers(); }
  });
});
describe('durable intent and recovery races', () => {
  it('reconciles a storage event received while busy read is pending', async () => {
    const v = vault(); const h = await setup(v.port);
    const stale = deferred<TransferIntent | undefined>();
    vi.mocked(v.port.read).mockImplementationOnce(() => stale.promise);
    v.port.lock = async () => { throw new RecoveryBusy(); };
    const submission = h.store.submit(payload, h.session.getScope()); await tick();
    v.records.set('A', intent); v.changed();
    stale.resolve(undefined); await submission; await tick();
    expect(h.store.getSnapshot()).toMatchObject({ intent: { key: intent.key } });
    expect(h.submit).not.toHaveBeenCalled(); h.stop();
  });
  it('late hydration cannot resurrect an intent after final-result cleanup', async () => {
    const v = vault(intent); const h = await setup(v.port);
    h.resolve.mockResolvedValue({ outcome: 'COMPLETED', reference: 'REF' }); await h.store.resolve();
    const cleanupRead = deferred<TransferIntent | undefined>(); const hydrationRead = deferred<TransferIntent | undefined>();
    vi.mocked(v.port.read).mockImplementationOnce(() => cleanupRead.promise).mockImplementationOnce(() => hydrationRead.promise);
    const cleanup = h.store.newIntent(); await tick(); v.changed(); await tick();
    cleanupRead.resolve(intent); await cleanup;
    expect(v.records.has('A')).toBe(false); expect(h.store.getSnapshot()).toEqual({ phase: 'idle' });
    hydrationRead.resolve(intent); await tick();
    expect(h.store.getSnapshot()).toEqual({ phase: 'idle' }); h.stop();
  });
  it('persists before POST and reloads UNKNOWN without creating a key or submitting', async () => {
    const v = vault(); const h = await setup(v.port);
    h.submit.mockImplementation(async () => { expect(v.records.get('A')?.key).toBe('new-key'); return { outcome: 'UNKNOWN' }; });
    await h.store.submit(payload, h.session.getScope()); h.stop();
    const restored = await setup(v.port);
    expect(restored.store.getSnapshot()).toMatchObject({ phase: 'settled', intent: { key: 'new-key', payload }, result: { outcome: 'UNKNOWN' } });
    expect(restored.key).not.toHaveBeenCalled(); expect(restored.submit).not.toHaveBeenCalled(); expect(restored.resolve).not.toHaveBeenCalled(); restored.stop();
  });
  it('double resolve preserves key, no transfer, no new key', async () => {
    const h = await setup(vault(intent).port); const pending = deferred<TransferResult>(); h.resolve.mockImplementation(() => pending.promise);
    const first = h.store.resolve(); expect(h.store.resolve()).toBe(first); await tick(); expect(h.resolve).toHaveBeenCalledTimes(1);
    expect(h.resolve.mock.calls[0]).toEqual([payload, intent.key, expect.any(AbortSignal)]);
    pending.resolve({ outcome: 'COMPLETED', reference: 'REF' }); await first;
    expect(h.store.getSnapshot()).toMatchObject({ result: { outcome: 'COMPLETED' } }); expect(h.submit).not.toHaveBeenCalled(); expect(h.key).not.toHaveBeenCalled(); h.stop();
  });
  it('two tabs cannot resolve concurrently; busy tab retains uncertain intent', async () => {
    const v = vault(intent); const a = await setup(v.port); const b = await setup(v.port);
    const pending = deferred<TransferResult>(); a.resolve.mockImplementation(() => pending.promise);
    const first = a.store.resolve(); await tick(); await b.store.resolve();
    expect(b.resolve).not.toHaveBeenCalled(); expect(b.store.getSnapshot()).toMatchObject({ result: { outcome: 'UNKNOWN', reason: 'busy' } });
    pending.resolve({ outcome: 'PROCESSING' }); await first; a.stop(); b.stop();
  });
  it.each(['logout', 'refresh', 'B'])('%s invalidates pending resolution and late completion', async change => {
    const h = await setup(vault(intent).port); const pending = deferred<TransferResult>(); h.resolve.mockImplementation(() => pending.promise);
    const resolving = h.store.resolve(); await tick();
    if (change === 'logout') await h.session.logout(); else { if (change === 'B') h.change('B'); await h.session.refresh(); }
    await tick(); pending.resolve({ outcome: 'COMPLETED', reference: 'PRIVATE-A' }); await resolving;
    expect(JSON.stringify(h.store.getSnapshot())).not.toContain('PRIVATE-A');
    if (change !== 'refresh') expect(JSON.stringify(h.store.getSnapshot())).not.toContain('original-key'); h.stop();
  });
  it('corrupt storage fails closed, never starts transfer', async () => {
    const v = vault(intent); vi.mocked(v.port.read).mockRejectedValue(new Error('tampered')); const h = await setup(v.port);
    expect(h.store.getSnapshot()).toEqual({ phase: 'blocked' }); await h.store.submit(payload, h.session.getScope()); expect(h.submit).not.toHaveBeenCalled(); h.stop();
  });
  it('quota/write failure prevents POST', async () => {
    const v = vault(); vi.mocked(v.port.write).mockRejectedValue(new Error('quota')); const h = await setup(v.port);
    await h.store.submit(payload, h.session.getScope()); expect(h.submit).not.toHaveBeenCalled(); expect(h.store.getSnapshot()).toMatchObject({ result: { outcome: 'UNKNOWN', reason: 'storage' } }); h.stop();
  });
  it('stale tab final cannot delete a newer intent', async () => {
    const v = vault(intent); const h = await setup(v.port); h.resolve.mockResolvedValue({ outcome: 'COMPLETED', reference: 'REF' }); await h.store.resolve();
    v.records.set('A', { ...intent, key: 'newer' }); await h.store.newIntent();
    expect(v.records.get('A')?.key).toBe('newer'); expect(h.store.getSnapshot()).toMatchObject({ intent: { key: 'newer' }, result: { outcome: 'UNKNOWN' } }); h.stop();
  });
  it('401 expires only current session and retains durable key', async () => {
    const v = vault(intent); const h = await setup(v.port); h.resolve.mockResolvedValue({ outcome: 'UNKNOWN', reason: 'session' }); await h.store.resolve();
    expect(h.session.getSnapshot().status).toBe('anonymous'); expect(v.records.get('A')?.key).toBe(intent.key); h.stop();
  });
});
describe('local fingerprint format', () => {
  it('fixed field order, exact text and omission tags; never uses Number', async () => {
    expect(recoveryCanonical(intent)).toBe('["A","INTERNAL_TRANSFER","original-key","DEST","9007199254740993",["present"," exact "]]');
    const fingerprint = await recoveryFingerprint(intent);
    expect(fingerprint).toBe('ee3b0989cc64e27cc34bff2475c0f55936911540e7b34df1394ffb3f0eac36b4');
    expect(await recoveryFingerprint({ key: intent.key, payload: { note: payload.note, amountVnd: payload.amountVnd, recipientAccountNumber: 'DEST' }, owner: 'A' })).toBe(fingerprint);
    for (const other of [{ ...intent, owner: 'B' }, { ...intent, key: 'other' }, { ...intent, payload: { ...payload, note: '' } }, { ...intent, payload: { recipientAccountNumber: 'DEST', amountVnd: payload.amountVnd } }]) expect(await recoveryFingerprint(other)).not.toBe(fingerprint);
  });
});
