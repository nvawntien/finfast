/** Vite-served test module, never imported by the application or production bundle. */
import { http, HttpResponse } from 'msw';
import { worker } from '../../src/mocks/browser';
import { mockAccount, mockIdentity } from '../../src/mocks/fixtures';

let release: (() => void) | undefined;
let releaseSession: (() => void) | undefined;
export function releaseResponse() { release?.(); release = undefined; }
export function confirmSession() { releaseSession?.(); releaseSession = undefined; }
export function reset() { releaseResponse(); confirmSession(); worker.resetHandlers(); }
export function scenario(kind: 'account500' | 'account503' | 'accountPending' | 'sessionPending' | 'sessionExpired' | 'historyNetwork' | 'historyEmpty') {
  if (kind === 'accountPending') {
    const gate = new Promise<void>(resolve => { release = resolve; });
    worker.use(http.get('/api/v1/accounts/me', async () => { await gate; return HttpResponse.json(mockAccount); }));
  } else if (kind === 'sessionPending') {
    const gate = new Promise<void>(resolve => { releaseSession = resolve; });
    worker.use(http.get('/api/v1/auth/session', async () => { await gate; return HttpResponse.json(mockIdentity); }));
  } else if (kind === 'account500' || kind === 'account503') {
    const status = kind === 'account500' ? 500 : 503;
    worker.use(http.get('/api/v1/accounts/me', () => HttpResponse.json({ code: status === 500 ? 'INTERNAL_ERROR' : 'SERVICE_UNAVAILABLE', message: 'Synthetic fault' }, { status })));
  } else if (kind === 'sessionExpired') {
    worker.use(http.get('/api/v1/auth/session', () => HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'Synthetic expiry' }, { status: 401 })));
  } else {
    worker.use(http.get('/api/v1/accounts/me/history', () => kind === 'historyNetwork' ? HttpResponse.error() : HttpResponse.json({ items: [], nextCursor: null })));
  }
}
