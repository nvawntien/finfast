import { http, HttpResponse, passthrough } from 'msw';
import { isRecord } from '../api/contracts';
import { mockAccount, mockCredentials, mockHistoryItem, mockIdentity, mockReceipt } from './fixtures';

/** Per-worker/test state only. This is NOT a cookie/session security implementation. */
export function createMockApi(origin: string) {
  const base = `${new URL(origin).origin}/api/v1`;
  let authenticated = false;
  let generation = 0;
  const token = () => `mock-csrf-${generation}`;
  const unauthorized = () => HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'Mock session required.' }, { status: 401 });
  const validCsrf = (request: Request) => request.headers.get('X-CSRF-TOKEN') === token();
  const nonfinal = () => HttpResponse.json({ outcome: 'UNKNOWN', code: 'MOCK_PENDING', message: 'Fixture only; no financial operation was executed.' }, { status: 202 });

  return {
    reset() { authenticated = false; generation = 0; },
    handlers: [
      http.get(`${base}/auth/csrf`, () => HttpResponse.json({ token: token() })),
      http.post(`${base}/auth/register`, async ({ request }) => {
        // Frozen auth statuses do not define CSRF denial. Use a transport fault in this mock,
        // not an invented 403 contract. Actual security-filter mapping remains an integration gap.
        if (!validCsrf(request)) return HttpResponse.error();
        const data: unknown = await request.json();
        if (!isRecord(data) || !['displayName', 'email', 'password'].every(key => typeof data[key] === 'string' && data[key])
          || Object.keys(data).some(key => !['displayName', 'email', 'password'].includes(key))) {
          return HttpResponse.json({ code: 'INVALID_REQUEST', message: 'Use synthetic registration fields.' }, { status: 422 });
        }
        return HttpResponse.json({ customerId: 'MOCK-REGISTERED', accountNumber: 'MOCK-REGISTERED-ACCOUNT', status: 'ACTIVE', currency: 'VND', balanceVnd: '0' }, { status: 201 });
      }),
      http.post(`${base}/auth/login`, async ({ request }) => {
        if (!validCsrf(request)) return HttpResponse.error();
        const data: unknown = await request.json();
        if (!isRecord(data) || data.email !== mockCredentials.email || data.password !== mockCredentials.password
          || Object.keys(data).some(key => !['email', 'password'].includes(key))) return unauthorized();
        authenticated = true;
        generation++;
        return HttpResponse.json(mockIdentity);
      }),
      http.post(`${base}/auth/logout`, ({ request }) => {
        if (!authenticated) return unauthorized();
        if (!validCsrf(request)) return HttpResponse.error();
        authenticated = false;
        generation++;
        return HttpResponse.json({ message: 'Mock session ended.' });
      }),
      http.get(`${base}/auth/session`, () => authenticated ? HttpResponse.json(mockIdentity) : unauthorized()),
      http.get(`${base}/accounts/me`, () => authenticated ? HttpResponse.json(mockAccount) : unauthorized()),
      http.get(`${base}/accounts/me/history`, ({ request }) => {
        if (!authenticated) return unauthorized();
        const query = new URL(request.url).searchParams;
        const limit = query.get('limit');
        if (query.has('cursor') || (limit !== null && (!/^[1-9][0-9]*$/.test(limit) || Number(limit) > 100))) {
          return HttpResponse.json({ code: 'INVALID_REQUEST', message: 'Fixture has one page; use limit 1–100 and no cursor.' }, { status: 422 });
        }
        return HttpResponse.json({ items: [mockHistoryItem], nextCursor: null });
      }),
      http.get(`${base}/transactions/:reference/receipt`, ({ params }) => {
        if (!authenticated) return unauthorized();
        return params.reference === mockReceipt.reference ? HttpResponse.json(mockReceipt)
          : HttpResponse.json({ code: 'NOT_FOUND', message: 'Receipt unavailable.' }, { status: 404 });
      }),
      http.post(`${base}/transfers`, ({ request }) => {
        if (!authenticated) return unauthorized();
        if (!validCsrf(request)) return HttpResponse.json({ code: 'FORBIDDEN', message: 'Mock CSRF token required.' }, { status: 403 });
        if (!request.headers.get('Idempotency-Key')) return HttpResponse.error();
        return nonfinal();
      }),
      http.post(`${base}/request-outcomes/resolve`, async ({ request }) => {
        if (!authenticated) return unauthorized();
        if (!validCsrf(request)) return HttpResponse.json({ code: 'FORBIDDEN', message: 'Mock CSRF token required.' }, { status: 403 });
        const data: unknown = await request.json();
        if (!isRecord(data) || data.operationType !== 'INTERNAL_TRANSFER') {
          return HttpResponse.json({ code: 'FORBIDDEN', message: 'Customer mock only.' }, { status: 403 });
        }
        return nonfinal();
      }),
      // Fail closed for unhandled APIs AND other origins; never silently hit a real backend.
      http.all('*', ({ request }) => {
        const url = new URL(request.url);
        if (url.origin !== new URL(base).origin || /^\/api(?:\/|$)/.test(url.pathname)) return HttpResponse.error();
        return passthrough(); // Vite modules/assets only.
      }),
    ],
  };
}
