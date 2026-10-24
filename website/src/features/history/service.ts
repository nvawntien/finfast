import type { createHttpClient } from '../../api/client';
import { isRecord } from '../../api/contracts';
import { ApiError } from '../../api/errors';
import { parsePositiveMoneyVnd, type MoneyVnd } from '../../lib/money';

type Movement = { reference: string; type: 'INTERNAL_TRANSFER' | 'DEMO_ALLOCATION'; amountVnd: MoneyVnd; currency: 'VND'; committedAt: string };
export type HistoryItem = Movement & { direction: 'INCOMING' | 'OUTGOING'; counterpartyLabel: string };
export type HistoryPage = { items: HistoryItem[]; nextCursor: string | null };
export type Receipt = Movement & { sourceLabel: string; destinationLabel: string; status: 'COMPLETED' };
const protocol = () => new ApiError('protocol');
function fields(value: unknown, keys: string[]): Record<string, unknown> {
  if (!isRecord(value) || Object.keys(value).length !== keys.length || keys.some(key => !Object.hasOwn(value, key))) throw protocol();
  return value;
}
function text(value: unknown): string { if (typeof value !== 'string' || !value.trim()) throw protocol(); return value; }
function movement(data: Record<string, unknown>): Movement {
  if (data.type !== 'INTERNAL_TRANSFER' && data.type !== 'DEMO_ALLOCATION' || data.currency !== 'VND') throw protocol();
  const committedAt = text(data.committedAt);
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/i.test(committedAt) || !Number.isFinite(Date.parse(committedAt))) throw protocol();
  // Date.parse normalizes February 30 and 24:00; never silently change receipt facts.
  const year = Number(committedAt.slice(0, 4)); const month = Number(committedAt.slice(5, 7)); const day = Number(committedAt.slice(8, 10));
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (month < 1 || month > 12 || day < 1 || day > days[month - 1]! || Number(committedAt.slice(11, 13)) > 23) throw protocol();
  try { return { reference: text(data.reference), type: data.type, currency: 'VND', amountVnd: parsePositiveMoneyVnd(data.amountVnd), committedAt }; }
  catch { throw protocol(); }
}
export function decodeHistory(value: unknown): HistoryPage {
  const data = fields(value, ['items', 'nextCursor']);
  if (!Array.isArray(data.items) || !(data.nextCursor === null || typeof data.nextCursor === 'string')) throw protocol();
  const items = data.items.map(value => {
    const item = fields(value, ['reference', 'type', 'direction', 'amountVnd', 'currency', 'counterpartyLabel', 'committedAt']);
    if (item.direction !== 'INCOMING' && item.direction !== 'OUTGOING') throw protocol();
    if (item.type === 'DEMO_ALLOCATION' && item.direction !== 'INCOMING') throw protocol();
    return { ...movement(item), direction: item.direction, counterpartyLabel: text(item.counterpartyLabel) } as HistoryItem;
  });
  if (new Set(items.map(item => item.reference)).size !== items.length) throw protocol();
  return { items, nextCursor: data.nextCursor };
}
export function decodeReceipt(value: unknown, reference: string): Receipt {
  const data = fields(value, ['reference', 'type', 'amountVnd', 'currency', 'sourceLabel', 'destinationLabel', 'committedAt', 'status']);
  if (data.status !== 'COMPLETED' || data.reference !== reference) throw protocol();
  return { ...movement(data), sourceLabel: text(data.sourceLabel), destinationLabel: text(data.destinationLabel), status: 'COMPLETED' };
}
export function createHistoryService(client: ReturnType<typeof createHttpClient>) {
  return {
    async history(cursor: string | undefined, signal: AbortSignal): Promise<HistoryPage> {
      const query = new URLSearchParams({ limit: '20' });
      if (cursor !== undefined) query.set('cursor', cursor); // Opaque: never decode, increment or rebuild it.
      const page = decodeHistory((await client.request(`/accounts/me/history?${query}`, { signal })).data);
      if (page.items.length > 20 || cursor !== undefined && page.nextCursor === cursor) throw protocol();
      return page;
    },
    async receipt(reference: string, signal: AbortSignal): Promise<Receipt> {
      // Dot segments would be normalized by URL and must not become a different operation.
      if (!reference || reference === '.' || reference === '..') throw protocol();
      return decodeReceipt((await client.request(`/transactions/${encodeURIComponent(reference)}/receipt`, { signal })).data, reference);
    },
  };
}
