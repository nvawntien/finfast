import type { createSessionStore } from '../auth/sessionStore';
import { parsePositiveMoneyVnd } from '../../lib/money';
import type { TransferDraft } from './draft';
import type { createTransferService, TransferResult } from './service';
import { RecoveryBusy, recoveryCanonical, type RecoveryVault } from './recoveryVault';

export type TransferIntent = Readonly<{ owner: string; key: string; payload: TransferDraft }>;
export type TransferState = { phase: 'idle' } | { phase: 'loading' } | { phase: 'blocked' }
  | { phase: 'submitting' | 'resolving'; intent: TransferIntent }
  | { phase: 'settled'; intent: TransferIntent; result: TransferResult };
const idle: TransferState = Object.freeze({ phase: 'idle' });
const loading: TransferState = Object.freeze({ phase: 'loading' });
type Service = Pick<ReturnType<typeof createTransferService>, 'submit'> & Partial<Pick<ReturnType<typeof createTransferService>, 'resolve'>>;
export function createTransferStore(session: ReturnType<typeof createSessionStore>, service: Service,
  newKey: () => string = () => crypto.randomUUID(), vault?: RecoveryVault) {
  const states = new Map<string, TransferState>();
  const listeners = new Set<() => void>();
  let active: { owner: string; scope: number; controller: AbortController; promise?: Promise<void> } | undefined;
  let hydration = 0;
  let storageChangedWhileActive = false;
  const principal = () => { const value = session.getSnapshot(); return value.status === 'authenticated' && value.session?.role === 'CUSTOMER' ? value.session.userId : undefined; };
  const notify = () => listeners.forEach(listener => listener());
  const uncertain = (intent: TransferIntent, reason?: 'interrupted' | 'busy' | 'storage'): TransferState => ({ phase: 'settled', intent, result: { outcome: 'UNKNOWN', ...(reason ? { reason } : {}) } });
  const same = (a: TransferIntent, b: TransferIntent) => recoveryCanonical(a) === recoveryCanonical(b);
  function interrupt() {
    if (!active) return;
    const pending = active; active = undefined;
    const old = states.get(pending.owner);
    if (old && 'intent' in old) states.set(pending.owner, uncertain(old.intent, 'interrupted'));
    pending.controller.abort(); // Cancellation cannot prove the server did not commit.
  }
  async function hydrate() {
    const owner = principal(); const scope = session.getScope(); const generation = ++hydration;
    if (!vault || !owner || active) return;
    const previous = states.get(owner);
    states.set(owner, { phase: 'loading' }); notify();
    try {
      const intent = await vault.read(owner);
      if (generation !== hydration || scope !== session.getScope() || owner !== principal() || active) return;
      states.set(owner, intent ? previous?.phase === 'settled' && same(previous.intent, intent) ? previous : uncertain(intent) : idle);
    } catch {
      if (generation !== hydration || scope !== session.getScope() || owner !== principal() || active) return;
      states.set(owner, { phase: 'blocked' }); // Never erase corruption and silently offer a replacement.
    }
    notify();
  }
  function run(intent: TransferIntent, expectedScope: number, resolving: boolean): Promise<void> {
    const owner = intent.owner;
    const pending = { owner, scope: expectedScope, controller: new AbortController(), promise: undefined as Promise<void> | undefined };
    active = pending; hydration++;
    states.set(owner, { phase: resolving ? 'resolving' : 'submitting', intent });
    const current = () => active === pending && principal() === owner && session.getScope() === expectedScope && !pending.controller.signal.aborted;
    const work = async () => {
      if (!current()) return;
      if (vault) {
        const stored = await vault.read(owner);
        if (!current()) return;
        if (!resolving && stored) { states.set(owner, uncertain(stored)); return; }
        if (resolving && (!stored || !same(stored, intent))) { states.set(owner, stored ? uncertain(stored) : { phase: 'blocked' }); return; }
        if (!resolving) await vault.write(intent);
      }
      if (!current()) return;
      const result = resolving && service.resolve
        ? await service.resolve(intent.payload, intent.key, pending.controller.signal)
        : resolving ? { outcome: 'UNKNOWN' as const, reason: 'protocol' as const }
          : await service.submit(intent.payload, intent.key, pending.controller.signal);
      if (!current()) return;
      states.set(owner, { phase: 'settled', intent, result });
      // Stop active before expiring: preserve the actual reason and ignore late callbacks.
      if (result.outcome === 'UNKNOWN' && result.reason === 'session') { active = undefined; session.expireSession(expectedScope); }
    };
    pending.promise = Promise.resolve().then(async () => {
      try { if (vault) await vault.lock(owner, work); else await work(); }
      catch (error) {
        if (current()) {
          let retained = intent;
          if (error instanceof RecoveryBusy && vault) {
            try { retained = await vault.read(owner) ?? intent; } catch { /* Never authorize another mutation. */ }
          }
          if (current()) states.set(owner, uncertain(retained, error instanceof RecoveryBusy ? 'busy' : 'storage'));
        }
      } finally {
        if (active === pending) active = undefined;
        notify();
        if (!active && storageChangedWhileActive) {
          storageChangedWhileActive = false;
          void hydrate();
        }
      }
    });
    notify(); return pending.promise;
  }
  return {
    hasUnresolved: () => [...states.values()].some(state => state.phase === 'submitting' || state.phase === 'resolving' || state.phase === 'settled' && (state.result.outcome === 'PROCESSING' || state.result.outcome === 'UNKNOWN')),
    getSnapshot: (): TransferState => { const owner = principal(); return owner ? states.get(owner) ?? (vault ? loading : idle) : idle; },
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
    connect() {
      const stop = session.subscribe(() => {
        hydration++;
        if (active && (active.scope !== session.getScope() || active.owner !== principal())) interrupt();
        void hydrate(); notify();
      });
      const stopVault = vault?.subscribe(() => {
        if (active) storageChangedWhileActive = true;
        else void hydrate();
      });
      void hydrate();
      return () => { stop(); stopVault?.(); hydration++; interrupt(); notify(); };
    },
    reload: hydrate,
    submit(draft: TransferDraft, expectedScope: number): Promise<void> {
      const owner = principal();
      if (!owner || expectedScope !== session.getScope()) return Promise.resolve();
      if (active) return active.promise ?? Promise.resolve();
      const state = states.get(owner);
      if (state && state.phase !== 'idle' || vault && !state) return Promise.resolve();
      const payload = Object.freeze({ recipientAccountNumber: draft.recipientAccountNumber, amountVnd: parsePositiveMoneyVnd(draft.amountVnd),
        ...(Object.hasOwn(draft, 'note') ? { note: draft.note } : {}) });
      if (!payload.recipientAccountNumber.trim() || Object.hasOwn(payload, 'note') && typeof payload.note !== 'string') return Promise.reject(new TypeError('Invalid transfer draft'));
      const key = newKey();
      if (!key || key.length > 200 || /[\r\n]/.test(key)) return Promise.reject(new TypeError('Invalid intent key'));
      return run(Object.freeze({ owner, key, payload }), expectedScope, false);
    },
    resolve(): Promise<void> {
      const owner = principal(); const state = owner ? states.get(owner) : undefined;
      if (active) return active.promise ?? Promise.resolve();
      if (!owner || state?.phase !== 'settled' || !['UNKNOWN', 'PROCESSING'].includes(state.result.outcome)) return Promise.resolve();
      return run(state.intent, session.getScope(), true);
    },
    async newIntent() {
      const owner = principal(); const scope = session.getScope(); const state = owner ? states.get(owner) : undefined;
      if (!owner || active || state?.phase !== 'settled' || !['COMPLETED', 'REJECTED'].includes(state.result.outcome)) return;
      hydration++; // Invalidate reads begun before this deliberate release of the intent.
      if (!vault) { states.delete(owner); notify(); return; }
      try {
        await vault.lock(owner, async () => {
          const stored = await vault.read(owner);
          if (scope !== session.getScope() || owner !== principal()) return;
          if (stored && !same(stored, state.intent)) { states.set(owner, uncertain(stored)); return; }
          if (stored) await vault.remove(owner);
          if (scope === session.getScope() && owner === principal()) { hydration++; states.set(owner, idle); }
        });
      } catch { /* Keep the confirmed result. A failed cleanup cannot authorize another POST. */ }
      notify();
    },
  };
}
