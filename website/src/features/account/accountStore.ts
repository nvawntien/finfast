import { ApiError } from '../../api/errors';
import type { createSessionStore } from '../auth/sessionStore';
import { AccountDataError, type AccountOverview, type createAccountService } from './service';

export type AccountState = { status: 'loading' } | { status: 'ready'; account: AccountOverview }
  | { status: 'error'; reason: 'empty' | 'protocol' | 'forbidden' | 'server' | 'network' | 'timeout' };
const loading: AccountState = { status: 'loading' };

/** Private to a mounted Dashboard; no persistent cache, retries or cross-principal data. */
export function createAccountStore(session: ReturnType<typeof createSessionStore>, service: ReturnType<typeof createAccountService>) {
  let state: AccountState = loading;
  let scope = -1;
  let sequence = 0;
  let controller: AbortController | undefined;
  const listeners = new Set<() => void>();
  const authorized = () => session.getSnapshot().status === 'authenticated' && session.getSnapshot().session?.role === 'CUSTOMER';
  const notify = () => listeners.forEach(listener => listener());
  const clear = () => { sequence++; controller?.abort(); controller = undefined; state = loading; };
  async function load() {
    clear(); scope = session.getScope(); notify();
    if (!authorized()) return;
    const requestScope = scope;
    const requestSequence = sequence;
    const requestController = new AbortController(); controller = requestController;
    const current = () => !requestController.signal.aborted && requestSequence === sequence && requestScope === session.getScope() && authorized();
    try {
      const account = await service.read(requestController.signal);
      if (current()) { state = { status: 'ready', account }; notify(); }
    } catch (error) {
      if (!current()) return;
      if (error instanceof ApiError && error.kind === 'http' && error.status === 401) {
        clear(); notify(); session.expireSession(requestScope); return;
      }
      const reason = error instanceof AccountDataError && error.empty ? 'empty'
        : error instanceof ApiError && error.kind === 'http' && error.status === 403 ? 'forbidden'
        : error instanceof ApiError && error.kind === 'http' && (error.status === 500 || error.status === 503) ? 'server'
        : error instanceof ApiError && error.kind === 'timeout' ? 'timeout'
        : error instanceof ApiError && error.kind === 'protocol' ? 'protocol' : 'network';
      state = { status: 'error', reason }; notify();
    }
  }
  return {
    // Hide data synchronously during render even before effect cleanup runs.
    getSnapshot: (): AccountState => scope === session.getScope() && authorized() ? state : loading,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
    connect() {
      const onSession = () => { if (scope !== session.getScope()) void load(); };
      const unsubscribe = session.subscribe(onSession);
      onSession();
      return () => { unsubscribe(); clear(); scope = -1; };
    },
    reload: load,
  };
}
