import { ApiError } from '../../api/errors';
import type { createIdentityService, Session } from './service';
import type { Credentials } from './validation';

export type SessionState = { status: 'checking' | 'anonymous' | 'authenticated' | 'error' | 'signing-in' | 'signing-out'; session?: Session; expired?: boolean; logoutUncertain?: boolean; waitingForLogin?: boolean };
export class IdentityBusyError extends Error {
  constructor() { super('An identity request is already pending.'); this.name = 'IdentityBusyError'; }
}
export function createSessionStore(service: ReturnType<typeof createIdentityService>) {
  let state: SessionState = { status: 'checking' };
  let version = 0;
  let loginRequest: Promise<void> | undefined;
  let logoutRequest: Promise<void> | undefined;
  let loginUncertain = false;
  let refresh: Promise<void> | undefined;
  const listeners = new Set<() => void>();
  const set = (next: SessionState) => { state = next; listeners.forEach(listener => listener()); };
  return {
    getSnapshot: () => state,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
    refresh() {
      if (state.status === 'signing-in' || state.status === 'signing-out') return Promise.resolve();
      if (refresh) return refresh;
      const current = version;
      const wasAuthenticated = state.status === 'authenticated' || state.expired;
      refresh = (async () => {
        try {
          const session = await service.session();
          if (current === version) set({ status: 'authenticated', session });
        } catch (error) {
          if (current === version) set(error instanceof ApiError && error.kind === 'http' && error.status === 401
            ? { status: 'anonymous', expired: !!wasAuthenticated } : { status: 'error' });
        }
      })().finally(() => { refresh = undefined; });
      return refresh;
    },
    login(values: Credentials) {
      if (loginRequest || logoutRequest) return Promise.reject(new IdentityBusyError());
      const current = ++version;
      loginUncertain = false;
      set({ status: 'signing-in' });
      loginRequest = (async () => {
        try {
          const session = await service.login(values);
          if (current === version) set({ status: 'authenticated', session });
        } catch (error) {
          // A failed transport/protocol response cannot prove no server session was created.
          loginUncertain = !(error instanceof ApiError && error.kind === 'http' && error.status === 401);
          if (current === version) { set({ status: 'anonymous' }); throw error; }
          // Explicit logout superseded this login; its UI owns the final result.
        }
      })().finally(() => { loginRequest = undefined; });
      return loginRequest;
    },
    logout() {
      if (logoutRequest) return logoutRequest;
      const precedingLogin = loginRequest;
      version++;
      set({ status: 'signing-out', waitingForLogin: !!precedingLogin });
      logoutRequest = (async () => {
        if (precedingLogin) await precedingLogin.catch(() => undefined);
        set({ status: 'signing-out' });
        try {
          // Do not abort/retry login: wait for cookie rotation, then fetch fresh CSRF via the client.
          await service.logout();
          set(precedingLogin && loginUncertain ? { status: 'error', logoutUncertain: true } : { status: 'anonymous' });
        } catch (error) {
          if (error instanceof ApiError && error.kind === 'http' && error.status === 401 && !(precedingLogin && loginUncertain)) set({ status: 'anonymous' });
          else set({ status: 'error', logoutUncertain: true });
        }
      })().finally(() => { logoutRequest = undefined; });
      return logoutRequest;
    },
  };
}
