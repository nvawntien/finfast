/** Canonical JSON money stays a string; never pass through Number/parseFloat. */
export type MoneyVnd = string & { readonly __moneyVnd: unique symbol };
export const MAX_VND = '9223372036854775807';

export function parseMoneyVnd(value: unknown): MoneyVnd {
  if (typeof value !== 'string' || value !== value.trim() || !/^(0|[1-9][0-9]*)$/.test(value)
    || value.length > MAX_VND.length || (value.length === MAX_VND.length && value > MAX_VND)) {
    throw new TypeError('VND must be a canonical nonnegative signed-64-bit decimal string.');
  }
  return value as MoneyVnd;
}

export function parsePositiveMoneyVnd(value: unknown): MoneyVnd {
  const amount = parseMoneyVnd(value);
  if (amount === '0') throw new TypeError('Amount must exceed zero.');
  return amount;
}

export function formatMoneyVnd(value: MoneyVnd): string {
  return `${parseMoneyVnd(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.')} ₫`;
}

/** Check monetary fields at the JSON boundary without coercing or mutating data. */
export function validateMoneyFields(value: unknown): void {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    if (['amountVnd', 'balanceVnd', 'availableBalanceVnd'].includes(key)) parseMoneyVnd(child);
    else validateMoneyFields(child);
  }
}
