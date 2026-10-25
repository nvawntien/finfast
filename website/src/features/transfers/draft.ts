import { parsePositiveMoneyVnd, type MoneyVnd } from '../../lib/money';
import type { AccountOverview } from '../account/service';

export type TransferFields = { recipientAccountNumber: string; amountVnd: string; includeNote: boolean; note: string };
/** Immutable review payload; transferStore owns dispatch/key, Phase 6C owns recovery. */
export type TransferDraft = Readonly<{ recipientAccountNumber: string; amountVnd: MoneyVnd; note?: string }>;
export type DraftErrors = Partial<Record<'recipientAccountNumber' | 'amountVnd' | 'account', string>>;
export type DraftResult = { ok: false; errors: DraftErrors } | { ok: true; draft: TransferDraft };
export const emptyTransferFields = (): TransferFields => ({ recipientAccountNumber: '', amountVnd: '', includeNote: false, note: '' });

/** Only approved FR-004 prechecks. Server must recheck ownership/funds/states at commit. */
export function reviewTransfer(fields: TransferFields, account: AccountOverview): DraftResult {
  const errors: DraftErrors = {};
  if (account.status !== 'ACTIVE') errors.account = 'Tài khoản đang bị phong tỏa. Bạn vẫn có thể xem số dư, lịch sử và biên nhận.';
  if (!fields.recipientAccountNumber.trim()) errors.recipientAccountNumber = 'Nhập số tài khoản nhận.';
  else if (fields.recipientAccountNumber === account.accountNumber) errors.recipientAccountNumber = 'Không thể chuyển tiền đến chính tài khoản của bạn.';
  let amount: MoneyVnd | undefined;
  try {
    amount = parsePositiveMoneyVnd(fields.amountVnd);
    if (amount.length > account.availableBalanceVnd.length || amount.length === account.availableBalanceVnd.length && amount > account.availableBalanceVnd) {
      errors.amountVnd = 'Số tiền vượt số dư khả dụng theo thông tin tài khoản vừa tải.';
    }
  } catch { errors.amountVnd = 'Nhập số nguyên VND lớn hơn 0 trong phạm vi hỗ trợ, chỉ gồm chữ số; không dùng dấu phân cách hoặc phần thập phân.'; }
  if (Object.keys(errors).length || amount === undefined) return { ok: false, errors };
  // Do not trim/normalize identifiers or note; omission and explicit empty note differ.
  return { ok: true, draft: Object.freeze({ recipientAccountNumber: fields.recipientAccountNumber, amountVnd: amount,
    ...(fields.includeNote ? { note: fields.note } : {}) }) };
}
