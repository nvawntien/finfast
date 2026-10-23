import { customerOperations, isRecord, operationFor } from './contracts';
import { ApiError } from './errors';
import { validateMoneyFields } from '../lib/money';

type RequestOptions = {
  method?: 'GET' | 'POST';
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};
export type ApiResponse = { status: number; data: unknown };

/** No retries, redirects, session storage, logging or automatic financial outcomes. */
export function createHttpClient({ baseUrl, fetchImpl = (input, init) => globalThis.fetch(input, init) }: {
  baseUrl: string; fetchImpl?: typeof fetch;
}) {
  const base = new URL(baseUrl);
  if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password
    || base.search || base.hash || base.pathname !== '/api/v1') {
    throw new TypeError('Expected an HTTP(S) origin followed by /api/v1.');
  }

  return {
    async request(path: string, options: RequestOptions = {}): Promise<ApiResponse> {
      const { method = 'GET', body: requestBody, idempotencyKey, signal,
        timeoutMs = 15_000 } = options; // Client default, not a backend SLA.
      if (!path.startsWith('/') || path.startsWith('//') || /[\\#\s]/.test(path)) {
        throw new TypeError('Expected a relative Customer API path.');
      }
      const url = new URL(`${base.href}${path}`);
      if (url.origin !== base.origin || !url.pathname.startsWith('/api/v1/')) {
        throw new TypeError('API path must stay within /api/v1.');
      }
      const operation = operationFor(method, url.pathname.slice('/api/v1'.length));
      const financial = operation === 'POST /transfers' || operation === 'POST /request-outcomes/resolve';
      if (method === 'GET' && requestBody !== undefined) throw new TypeError('GET cannot have a body.');
      if (operation === 'POST /transfers' && (!idempotencyKey
        || idempotencyKey.length > 200 || /[\r\n]/.test(idempotencyKey))) {
        throw new TypeError('The original Idempotency-Key (1–200 characters) is required.');
      }
      validateMoneyFields(requestBody);
      // Snapshot before awaiting CSRF: callers cannot accidentally change an in-flight intent.
      const body = requestBody === undefined ? undefined : JSON.stringify(requestBody);
      if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1) throw new TypeError('Invalid timeout.');
      const controller = new AbortController();
      let timedOut = false;
      let dispatched = false;
      const abort = () => controller.abort();
      signal?.addEventListener('abort', abort, { once: true });
      if (signal?.aborted) abort();
      const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
      const errorOptions = () => ({ financialOutcome: financial ? 'UNKNOWN' as const : undefined,
        requestMayHaveBeenSent: dispatched });

      async function send(target: string, init: RequestInit, statuses: readonly number[]): Promise<ApiResponse> {
        const response = await fetchImpl(target, {
          ...init, credentials: 'include', redirect: 'error', cache: 'no-store', signal: controller.signal,
        });
        if (!response.headers.get('Content-Type')?.toLowerCase().includes('application/json')) {
          throw new ApiError('protocol', { status: response.status, ...errorOptions() });
        }
        // Reading the stream and parsing JSON are separate failure boundaries.
        let text: string;
        try {
          text = await response.text();
        } catch {
          throw new ApiError(timedOut ? 'timeout' : controller.signal.aborted ? 'aborted' : 'network', {
            status: response.status, ...errorOptions(),
          });
        }
        let data: unknown;
        try {
          data = JSON.parse(text);
        } catch {
          throw new ApiError('protocol', { status: response.status, ...errorOptions() });
        }
        if (!statuses.includes(response.status)) {
          throw new ApiError('protocol', { status: response.status, ...errorOptions() });
        }
        if (!response.ok) {
          if (!isRecord(data) || typeof data.code !== 'string' || typeof data.message !== 'string'
            || (data.outcome !== undefined && (typeof data.outcome !== 'string'
              || !['REJECTED', 'PROCESSING', 'UNKNOWN'].includes(data.outcome)))) {
            throw new ApiError('protocol', { status: response.status, ...errorOptions() });
          }
          const rejection = operation === 'POST /transfers' && response.status === 422;
          if (rejection && data.outcome !== 'REJECTED') {
            throw new ApiError('protocol', { status: response.status, ...errorOptions() });
          }
          throw new ApiError('http', {
            ...errorOptions(), status: response.status, code: data.code, serverMessage: data.message,
            financialOutcome: rejection ? 'REJECTED' : errorOptions().financialOutcome,
          });
        }
        try { validateMoneyFields(data); } catch {
          throw new ApiError('protocol', { status: response.status, ...errorOptions() });
        }
        return { status: response.status, data };
      }

      try {
        controller.signal.throwIfAborted();
        const headers = new Headers({ Accept: 'application/json' });
        if (method !== 'GET') {
          // Fresh on each mutation, including public login/register and after session rotation.
          const csrf = await send(`${base.href}/auth/csrf`, { headers }, [200]);
          if (!isRecord(csrf.data) || typeof csrf.data.token !== 'string' || !csrf.data.token
            || /[\r\n]/.test(csrf.data.token)) throw new ApiError('protocol', errorOptions());
          headers.set('X-CSRF-TOKEN', csrf.data.token);
        }
        if (body !== undefined) headers.set('Content-Type', 'application/json');
        if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey);
        controller.signal.throwIfAborted();
        dispatched = true;
        const result = await send(url.href, { method, body, headers }, customerOperations[operation]);
        if (financial) {
          const allowed = result.status === 202 ? ['PROCESSING', 'UNKNOWN']
            : operation === 'POST /transfers' ? ['COMPLETED'] : ['COMPLETED', 'REJECTED'];
          if (!isRecord(result.data) || typeof result.data.outcome !== 'string' || !allowed.includes(result.data.outcome)) {
            throw new ApiError('protocol', { status: result.status, ...errorOptions() });
          }
        }
        return result; // Preserve 202 and resolved REJECTED; HTTP 2xx alone is NOT financial success.
      } catch (error) {
        if (error instanceof ApiError) throw error;
        throw new ApiError(timedOut ? 'timeout' : controller.signal.aborted ? 'aborted' : 'network', errorOptions());
      } finally {
        clearTimeout(timer);
        signal?.removeEventListener('abort', abort);
      }
    },
  };
}
