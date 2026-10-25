import type { createHttpClient } from '../../api/client';
import { ApiError } from '../../api/errors';
import { isRecord } from '../../api/contracts';
import { parsePositiveMoneyVnd } from '../../lib/money';
import type { TransferDraft } from './draft';

export type TransferResult =
  | { outcome: 'COMPLETED'; reference: string }
  | { outcome: 'REJECTED'; code: string; message: string }
  | { outcome: 'PROCESSING' | 'UNKNOWN'; reason?: 'conflict' | 'session' | 'forbidden' | 'timeout' | 'network' | 'protocol' | 'unavailable' | 'interrupted' | 'busy' | 'storage' };
const protocol = () => new ApiError('protocol', { financialOutcome: 'UNKNOWN', requestMayHaveBeenSent: true });
export function decodeTransferResult(status: number, value: unknown, payload: TransferDraft, resolving = false): TransferResult {
  if (!isRecord(value) || Object.keys(value).some(key => !['outcome', 'reference', 'code', 'message', 'amountVnd', 'currency', 'committedAt'].includes(key))) throw protocol();
  for (const key of ['reference', 'code', 'message', 'committedAt']) if (Object.hasOwn(value, key) && typeof value[key] !== 'string') throw protocol();
  if (Object.hasOwn(value, 'currency') && value.currency !== 'VND') throw protocol();
  if (Object.hasOwn(value, 'amountVnd')) {
    try { if (parsePositiveMoneyVnd(value.amountVnd) !== payload.amountVnd) throw protocol(); } catch { throw protocol(); }
  }
  if (Object.hasOwn(value, 'committedAt')) {
    const time = value.committedAt as string;
    if (time !== time.trim() || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/i.test(time) || !Number.isFinite(Date.parse(time))) throw protocol();
    const year = Number(time.slice(0, 4)); const month = Number(time.slice(5, 7)); const day = Number(time.slice(8, 10));
    const days = [31, year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (month < 1 || month > 12 || day < 1 || day > days[month - 1]! || Number(time.slice(11, 13)) > 23) throw protocol();
  }
  // FR-006/API 17 require a committed reference, even though the shared DTO makes it optional.
  if (status === 200 && value.outcome === 'COMPLETED' && typeof value.reference === 'string' && value.reference.trim()) return { outcome: 'COMPLETED', reference: value.reference };
  if (resolving && status === 200 && value.outcome === 'REJECTED' && !Object.hasOwn(value, 'reference') && !Object.hasOwn(value, 'committedAt')) {
    return { outcome: 'REJECTED', code: value.code as string ?? '', message: value.message as string ?? 'Máy chủ xác nhận yêu cầu đã bị từ chối.' };
  }
  if ((status === 202 || resolving && status === 200) && (value.outcome === 'PROCESSING' || value.outcome === 'UNKNOWN') && !Object.hasOwn(value, 'reference') && !Object.hasOwn(value, 'committedAt')) return { outcome: value.outcome };
  throw protocol();
}
export function createTransferService(client: ReturnType<typeof createHttpClient>) {
  return {
    async resolve(payload: TransferDraft, key: string, signal: AbortSignal): Promise<TransferResult> {
      try {
        const response = await client.request('/request-outcomes/resolve', { method: 'POST',
          body: { operationType: 'INTERNAL_TRANSFER', idempotencyKey: key }, signal });
        return decodeTransferResult(response.status, response.data, payload, true);
      } catch (error) {
        // 422 is not in this endpoint's contract. No HTTP error certifies final rejection.
        return unknownResult(error);
      }
    },
    async submit(payload: TransferDraft, key: string, signal: AbortSignal): Promise<TransferResult> {
      try {
        const response = await client.request('/transfers', { method: 'POST', body: payload, idempotencyKey: key, signal });
        return decodeTransferResult(response.status, response.data, payload);
      } catch (error) {
        // Only the validated command 422 envelope certifies durable business rejection.
        if (error instanceof ApiError && error.kind === 'http' && error.status === 422 && error.financialOutcome === 'REJECTED' && error.requestMayHaveBeenSent) {
          return { outcome: 'REJECTED', code: error.code!, message: error.serverMessage! };
        }
        return unknownResult(error); // No automatic retry or new-key replacement.
      }
    },
  };
}

function unknownResult(error: unknown): TransferResult {
  const reason = error instanceof ApiError
    ? error.kind === 'protocol' ? 'protocol' : error.status === 409 ? 'conflict' : error.status === 401 ? 'session'
      : error.status === 403 ? 'forbidden' : error.status === 503 || error.status === 404 ? 'unavailable'
        : error.kind === 'timeout' ? 'timeout' : error.kind === 'aborted' ? 'interrupted' : 'network'
    : 'protocol';
  return { outcome: 'UNKNOWN', reason };
}
