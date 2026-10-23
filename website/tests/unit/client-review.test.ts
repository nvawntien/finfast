import { afterEach, describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../src/api/client';

const baseUrl = 'https://review.test/api/v1';
const payload = { recipientAccountNumber: 'MOCK-002', amountVnd: '9007199254740993', note: '' };
const command = { method: 'POST' as const, body: payload, idempotencyKey: 'original-key' };
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { 'Content-Type': 'application/json' },
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}

afterEach(() => { vi.useRealTimers(); });

describe('review: immutable intent and abort signal', () => {
  it.each(['replace', 'delete'] as const)('keeps the original payload/key when options key is %s during CSRF', async change => {
    const csrf = deferred<Response>();
    const fetchImpl = vi.fn<typeof fetch>()
      .mockImplementationOnce(() => csrf.promise)
      .mockResolvedValueOnce(json({ outcome: 'UNKNOWN' }, 202));
    const requestBody = { ...payload };
    const options: NonNullable<Parameters<ReturnType<typeof createHttpClient>['request']>[1]> = {
      ...command, body: requestBody,
    };
    const pending = createHttpClient({ baseUrl, fetchImpl }).request('/transfers', options);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    if (change === 'replace') options.idempotencyKey = 'replacement-key';
    else delete options.idempotencyKey;
    requestBody.note = 'changed while pending';
    csrf.resolve(json({ token: 'csrf' }));
    await pending;
    expect(fetchImpl).toHaveBeenCalledTimes(2); // Exactly one CSRF read and one mutation.
    const init = fetchImpl.mock.calls[1][1]!;
    expect(new Headers(init.headers).get('Idempotency-Key')).toBe('original-key');
    expect(JSON.parse(String(init.body))).toEqual(payload);
  });

  it.each(['success', 'abort'] as const)('cleans up the original signal after %s despite options replacement', async finish => {
    const original = new AbortController();
    const replacement = new AbortController();
    const add = vi.spyOn(original.signal, 'addEventListener');
    const remove = vi.spyOn(original.signal, 'removeEventListener');
    const otherRemove = vi.spyOn(replacement.signal, 'removeEventListener');
    const csrf = deferred<Response>();
    const fetchImpl = vi.fn<typeof fetch>()
      .mockImplementationOnce(() => csrf.promise)
      .mockResolvedValueOnce(json({ outcome: 'UNKNOWN' }, 202));
    const options = { ...command, signal: original.signal };
    const pending = createHttpClient({ baseUrl, fetchImpl }).request('/transfers', options);
    options.signal = replacement.signal;
    if (finish === 'abort') original.abort();
    else replacement.abort(); // The replacement must not cancel the captured intent.
    csrf.resolve(json({ token: 'csrf' }));
    if (finish === 'abort') {
      await expect(pending).rejects.toMatchObject({ kind: 'aborted', requestMayHaveBeenSent: false });
      expect(fetchImpl).toHaveBeenCalledTimes(1);
    } else {
      await expect(pending).resolves.toMatchObject({ status: 202 });
      expect(fetchImpl).toHaveBeenCalledTimes(2);
    }
    expect(add).toHaveBeenCalledTimes(1);
    expect(remove).toHaveBeenCalledWith('abort', add.mock.calls[0][1]);
    expect(otherRemove).not.toHaveBeenCalled();
  });
});

describe('review: strict outcome types', () => {
  const invalid = [ ['array', ['COMPLETED']], ['object', {}], ['number', 1], ['null', null] ] as const;
  it.each(invalid)('rejects OperationResult outcome of type %s', async (_name, outcome) => {
    const fetchImpl = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json({ token: 'csrf' }))
      .mockResolvedValueOnce(json({ outcome }));
    await expect(createHttpClient({ baseUrl, fetchImpl }).request('/transfers', command))
      .rejects.toMatchObject({ kind: 'protocol', status: 200, financialOutcome: 'UNKNOWN' });
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it.each([['array', ['UNKNOWN']], ['object', {}], ['number', 1], ['null', null]])(
    'rejects ErrorResponse outcome of type %s', async (_name, outcome) => {
      const fetchImpl = vi.fn<typeof fetch>()
        .mockResolvedValueOnce(json({ token: 'csrf' }))
        .mockResolvedValueOnce(json({ code: 'TECHNICAL_FAILURE', message: 'Synthetic', outcome }, 503));
      await expect(createHttpClient({ baseUrl, fetchImpl }).request('/transfers', command))
        .rejects.toMatchObject({ kind: 'protocol', status: 503, financialOutcome: 'UNKNOWN' });
      expect(fetchImpl).toHaveBeenCalledTimes(2);
    },
  );
});

describe('review: response body failure boundaries', () => {
  it.each([200, 422, 503])('classifies stream interruption after HTTP %i headers as network, never final rejection', async status => {
    let stream!: ReadableStreamDefaultController<Uint8Array>;
    const response = new Response(new ReadableStream<Uint8Array>({
      start(controller) { stream = controller; controller.enqueue(new TextEncoder().encode('{"outcome":')); },
    }), { status, headers: { 'Content-Type': 'application/json' } });
    const reading = deferred<void>();
    const read = response.text.bind(response);
    vi.spyOn(response, 'text').mockImplementation(() => { reading.resolve(); return read(); });
    const fetchImpl = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json({ token: 'csrf' }))
      .mockResolvedValueOnce(response);
    const pending = createHttpClient({ baseUrl, fetchImpl }).request('/transfers', command);
    const assertion = expect(pending).rejects.toMatchObject({
      kind: 'network', status, financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: true,
    });
    await reading.promise;
    stream.error(new TypeError('Connection terminated while reading body'));
    await assertion;
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it('keeps complete but malformed JSON classified as protocol', async () => {
    const fetchImpl = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json({ token: 'csrf' }))
      .mockResolvedValueOnce(new Response('{"outcome":', { headers: { 'Content-Type': 'application/json' } }));
    await expect(createHttpClient({ baseUrl, fetchImpl }).request('/transfers', command))
      .rejects.toMatchObject({ kind: 'protocol', status: 200, financialOutcome: 'UNKNOWN' });
  });

  it.each(['timeout', 'aborted'] as const)('preserves %s classification while consuming the response stream', async kind => {
    vi.useFakeTimers();
    const caller = new AbortController();
    const reading = deferred<void>();
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValueOnce(json({ token: 'csrf' }))
      .mockImplementationOnce(async (_url, init) => {
        const response = new Response(new ReadableStream<Uint8Array>({
          start(controller) {
            init!.signal!.addEventListener('abort', () => controller.error(new DOMException('Aborted', 'AbortError')), { once: true });
            controller.enqueue(new TextEncoder().encode('{"outcome":'));
          },
        }), { headers: { 'Content-Type': 'application/json' } });
        const read = response.text.bind(response);
        vi.spyOn(response, 'text').mockImplementation(() => { reading.resolve(); return read(); });
        return response;
      });
    const pending = createHttpClient({ baseUrl, fetchImpl }).request('/transfers', {
      ...command, timeoutMs: 100, signal: caller.signal,
    });
    const assertion = expect(pending).rejects.toMatchObject({ kind, status: 200,
      financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: true });
    await reading.promise;
    if (kind === 'timeout') await vi.advanceTimersByTimeAsync(100);
    else caller.abort();
    await assertion;
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
  });
});
