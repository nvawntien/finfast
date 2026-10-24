import type { createHttpClient } from '../../api/client';
import { isRecord } from '../../api/contracts';
import { ApiError } from '../../api/errors';
import { parseMoneyVnd, type MoneyVnd } from '../../lib/money';

export type AccountOverview = {
  accountNumber: string; type: 'CUSTOMER'; status: 'ACTIVE' | 'FROZEN'; currency: 'VND';
  balanceVnd: MoneyVnd; availableBalanceVnd: MoneyVnd;
};
export class AccountDataError extends ApiError {
  constructor(readonly empty: boolean = false) { super('protocol'); }
}
export function decodeAccount(data: unknown): AccountOverview {
  if (data === null || (isRecord(data) && Object.keys(data).length === 0)) throw new AccountDataError(true);
  if (!isRecord(data) || typeof data.accountNumber !== 'string' || !data.accountNumber.trim()
    || data.type !== 'CUSTOMER' || (data.status !== 'ACTIVE' && data.status !== 'FROZEN')
    || data.currency !== 'VND'
    || Object.keys(data).some(key => !['accountNumber', 'type', 'status', 'currency', 'balanceVnd', 'availableBalanceVnd'].includes(key))) throw new AccountDataError();
  try {
    const balanceVnd = parseMoneyVnd(data.balanceVnd);
    const availableBalanceVnd = parseMoneyVnd(data.availableBalanceVnd);
    // FR-003: no holds in V1. Never calculate or silently correct either server value.
    if (balanceVnd !== availableBalanceVnd) throw new AccountDataError();
    return { accountNumber: data.accountNumber, type: 'CUSTOMER', status: data.status as 'ACTIVE' | 'FROZEN', currency: 'VND', balanceVnd, availableBalanceVnd };
  } catch { throw new AccountDataError(); }
}
export function createAccountService(client: ReturnType<typeof createHttpClient>) {
  return { async read(signal: AbortSignal) { return decodeAccount((await client.request('/accounts/me', { signal })).data); } };
}
