import { ApiError } from '../../api/errors';
import type { createSessionStore } from '../auth/sessionStore';

export type ReadState<T> = { status: 'loading' } | { status: 'ready'; data: T }
  | { status: 'error'; reason: 'not-found' | 'forbidden' | 'cursor' | 'protocol' | 'network' | 'timeout' };
/** History/receipt reads use the Phase 4 session scope; inputs and data never persist. */
export function createReadStore<T, K>(session: ReturnType<typeof createSessionStore>, read: (input: K, signal: AbortSignal) => Promise<T>, initial: K) {
  const loading: ReadState<T> = { status: 'loading' };
  let state: ReadState<T> = loading; let scope = -1; let sequence = 0; let input = initial;
  let controller: AbortController | undefined;
  const listeners = new Set<() => void>();
  const authorized = () => session.getSnapshot().status === 'authenticated' && session.getSnapshot().session?.role === 'CUSTOMER';
  const notify = () => listeners.forEach(listener => listener());
  const clear = () => { sequence++; controller?.abort(); controller = undefined; state = loading; };
  async function load(next: K) {
    clear(); input = next; scope = session.getScope(); notify();
    if (!authorized()) return;
    const epoch = scope; const request = sequence; const abort = new AbortController(); controller = abort;
    const current = () => !abort.signal.aborted && request === sequence && epoch === session.getScope() && authorized();
    try {
      const data = await read(next, abort.signal);
      if (current()) { state = { status: 'ready', data }; notify(); }
    } catch (error) {
      if (!current()) return;
      if (error instanceof ApiError && error.kind === 'http' && error.status === 401) { clear(); notify(); session.expireSession(epoch); return; }
      const reason = error instanceof ApiError && error.kind === 'http'
        ? error.status === 404 ? 'not-found' : error.status === 403 ? 'forbidden' : error.status === 422 ? 'cursor' : 'protocol'
        : error instanceof ApiError && error.kind === 'protocol' ? 'protocol'
        : error instanceof ApiError && error.kind === 'timeout' ? 'timeout' : 'network';
      state = { status: 'error', reason }; notify();
    }
  }
  return {
    getSnapshot: (): ReadState<T> => scope === session.getScope() && authorized() ? state : loading,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
    connect() {
      const changed = () => { if (scope !== session.getScope()) void load(initial); };
      const stop = session.subscribe(changed); changed();
      return () => { stop(); clear(); scope = -1; input = initial; };
    },
    load, retry: () => load(input),
  };
}
