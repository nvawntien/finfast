import { http, HttpResponse, passthrough } from 'msw';
import { isRecord } from '../api/contracts';
import { mockAccount, mockCredentials, mockFrozenAccount, mockFrozenCredentials, mockFrozenIdentity, mockFrozenHistory, mockFrozenReceipts, mockHistoryItem, mockIdentity, mockReceipt } from './fixtures';
import { exceedsPasswordByteLimit } from '../features/auth/validation';

type MockCustomer = {
  identity: typeof mockIdentity; customerId: string; account: typeof mockAccount;
  history: typeof mockHistoryItem[]; receipts: typeof mockReceipt[]; digest?: string;
};

/** Per-worker/test state only. This is NOT a cookie/session security implementation. */
export function createMockApi(origin: string) {
  const base = `${new URL(origin).origin}/api/v1`;
  let generation = 0;
  let nextCustomer = 0;
  const fixtureCustomer: MockCustomer = { identity: mockIdentity, customerId: 'MOCK-CUSTOMER-ID',
    account: mockAccount, history: [mockHistoryItem], receipts: [mockReceipt] };
  const frozenCustomer: MockCustomer = { identity: mockFrozenIdentity, customerId: 'MOCK-FROZEN-CUSTOMER-ID', account: mockFrozenAccount, history: mockFrozenHistory, receipts: mockFrozenReceipts };
  const cursors = new Map<string, { owner: string; generation: number; limit: number; items: typeof mockHistoryItem[]; offset: number }>();
  let principal: MockCustomer | undefined;
  const registrations = new Map<string, MockCustomer>();
  // Ephemeral fixture verifier only, never fed into BCrypt or used to bypass TD-007.
  const digest = async (password: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(password))), byte => byte.toString(16).padStart(2, '0')).join('');
  const token = () => `mock-csrf-${generation}`;
  const unauthorized = () => HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'Mock session required.' }, { status: 401 });
  const validCsrf = (request: Request) => request.headers.get('X-CSRF-TOKEN') === token();
  const nonfinal = () => HttpResponse.json({ outcome: 'UNKNOWN', code: 'MOCK_PENDING', message: 'Fixture only; no financial operation was executed.' }, { status: 202 });

  return {
    reset() { principal = undefined; generation = 0; nextCustomer = 0; registrations.clear(); cursors.clear(); },
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
        if (exceedsPasswordByteLimit(data.password as string)) {
          return HttpResponse.json({ code: 'INVALID_REQUEST', message: 'Password exceeds 72 UTF-8 bytes.' }, { status: 422 });
        }
        const email = (data.email as string).trim().toLowerCase();
        if (email === mockCredentials.email || email === mockFrozenCredentials.email || registrations.has(email)) {
          return HttpResponse.json({ code: 'EMAIL_EXISTS', message: 'Synthetic email already registered.' }, { status: 409 });
        }
        const passwordDigest = await digest(data.password as string);
        // Recheck after await so simultaneous synthetic registrations stay unique.
        if (registrations.has(email)) return HttpResponse.json({ code: 'EMAIL_EXISTS', message: 'Synthetic email already registered.' }, { status: 409 });
        const id = ++nextCustomer;
        const customer: MockCustomer = {
          identity: { userId: `MOCK-USER-${id}`, role: 'CUSTOMER', displayName: data.displayName as string },
          customerId: `MOCK-CUSTOMER-${id}`, digest: passwordDigest,
          account: { accountNumber: `MOCK-REGISTERED-ACCOUNT-${id}`, type: 'CUSTOMER', status: 'ACTIVE', currency: 'VND', balanceVnd: '0', availableBalanceVnd: '0' },
          history: [], receipts: [],
        };
        registrations.set(email, customer);
        return HttpResponse.json({ customerId: customer.customerId, accountNumber: customer.account.accountNumber, status: 'ACTIVE', currency: 'VND', balanceVnd: '0' }, { status: 201 });
      }),
      http.post(`${base}/auth/login`, async ({ request }) => {
        if (!validCsrf(request)) return HttpResponse.error();
        const data: unknown = await request.json();
        if (!isRecord(data) || typeof data.email !== 'string' || typeof data.password !== 'string'
          || Object.keys(data).some(key => !['email', 'password'].includes(key))) return unauthorized();
        // Login has no frozen 422 response: invalid credentials remain 401.
        if (exceedsPasswordByteLimit(data.password)) return unauthorized();
        const email = data.email.trim().toLowerCase();
        const registered = registrations.get(email);
        if (email === mockCredentials.email && data.password === mockCredentials.password) principal = fixtureCustomer;
        else if (email === mockFrozenCredentials.email && data.password === mockFrozenCredentials.password) principal = frozenCustomer;
        else if (registered && registered.digest === await digest(data.password)) principal = registered;
        else return unauthorized();
        generation++;
        return HttpResponse.json(principal.identity);
      }),
      http.post(`${base}/auth/logout`, ({ request }) => {
        if (!principal) return unauthorized();
        if (!validCsrf(request)) return HttpResponse.error();
        principal = undefined;
        generation++;
        return HttpResponse.json({ message: 'Mock session ended.' });
      }),
      http.get(`${base}/auth/session`, () => principal ? HttpResponse.json(principal.identity) : unauthorized()),
      http.get(`${base}/accounts/me`, () => principal ? HttpResponse.json(principal.account) : unauthorized()),
      http.get(`${base}/accounts/me/history`, ({ request }) => {
        if (!principal) return unauthorized();
        const query = new URL(request.url).searchParams;
        const rawLimit = query.get('limit') ?? '20';
        const invalid = () => HttpResponse.json({ code: 'INVALID_REQUEST', message: 'Invalid history continuation or limit.' }, { status: 422 });
        if (!/^[1-9][0-9]*$/.test(rawLimit) || Number(rawLimit) > 100) return invalid();
        const limit = Number(rawLimit); // Page size only, never money.
        const cursor = query.get('cursor');
        const continuation = cursor === null ? undefined : cursors.get(cursor);
        if (cursor !== null && (!continuation || continuation.owner !== principal.identity.userId || continuation.generation !== generation || continuation.limit !== limit)) return invalid();
        const items = continuation?.items ?? principal.history.map(item => ({ ...item }));
        const offset = continuation?.offset ?? 0;
        const end = offset + limit;
        let nextCursor: string | null = null;
        if (end < items.length) {
          nextCursor = `mock-continuation:${crypto.randomUUID()}`;
          cursors.set(nextCursor, { owner: principal.identity.userId, generation, limit, items, offset: end });
        }
        return HttpResponse.json({ items: items.slice(offset, end), nextCursor });
      }),
      http.get(`${base}/transactions/:reference/receipt`, ({ params }) => {
        if (!principal) return unauthorized();
        const receipt = principal.receipts.find(item => item.reference === params.reference);
        return receipt ? HttpResponse.json(receipt)
          : HttpResponse.json({ code: 'NOT_FOUND', message: 'Receipt unavailable.' }, { status: 404 });
      }),
      http.post(`${base}/transfers`, ({ request }) => {
        if (!principal) return unauthorized();
        if (!validCsrf(request)) return HttpResponse.json({ code: 'FORBIDDEN', message: 'Mock CSRF token required.' }, { status: 403 });
        if (!request.headers.get('Idempotency-Key')) return HttpResponse.error();
        return nonfinal();
      }),
      http.post(`${base}/request-outcomes/resolve`, async ({ request }) => {
        if (!principal) return unauthorized();
        if (!validCsrf(request)) return HttpResponse.json({ code: 'FORBIDDEN', message: 'Mock CSRF token required.' }, { status: 403 });
        const data: unknown = await request.json();
        if (!isRecord(data) || data.operationType !== 'INTERNAL_TRANSFER') {
          return HttpResponse.json({ code: 'FORBIDDEN', message: 'Customer mock only.' }, { status: 403 });
        }
        if (typeof data.idempotencyKey !== 'string' || !data.idempotencyKey
          || Object.keys(data).some(key => !['operationType', 'idempotencyKey'].includes(key))) return HttpResponse.error();
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
