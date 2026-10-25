import { describe, expect, it } from 'vitest';
import { decodeAccount } from '../../src/features/account/service';
import { emptyTransferFields, reviewTransfer } from '../../src/features/transfers/draft';
import { MAX_VND } from '../../src/lib/money';
import { mockAccount } from '../../src/mocks/fixtures';
const account = decodeAccount({ ...mockAccount, balanceVnd: MAX_VND, availableBalanceVnd: MAX_VND });
const fields = () => ({ ...emptyTransferFields(), recipientAccountNumber: 'DESTINATION', amountVnd: '200000' });

describe('Phase 6A local transfer draft', () => {
  it.each(['', ' ', '\n'])('requires recipient %j without inventing identifier syntax', recipientAccountNumber => {
    const result = reviewTransfer({ ...fields(), recipientAccountNumber }, account);
    expect(result).toMatchObject({ ok: false, errors: { recipientAccountNumber: expect.any(String) } });
  });
  it('rejects exact self-transfer', () => expect(reviewTransfer({ ...fields(), recipientAccountNumber: account.accountNumber }, account)).toMatchObject({ ok: false, errors: { recipientAccountNumber: expect.any(String) } }));
  it.each(['', '0', '-1', '+1', '1.5', '1e3', '1,000', '1.000', '01', ' 1', '1 ', '1\n', '1\r\n', '１２', '9223372036854775808'])('rejects invalid whole VND %j', amountVnd => {
    expect(reviewTransfer({ ...fields(), amountVnd }, account)).toMatchObject({ ok: false, errors: { amountVnd: expect.any(String) } });
  });
  it.each(['1', '9007199254740993', MAX_VND])('retains exact amount %s without coercion', amountVnd => {
    expect(reviewTransfer({ ...fields(), amountVnd }, account)).toEqual({ ok: true, draft: { recipientAccountNumber: 'DESTINATION', amountVnd } });
  });
  it.each([['9', '10'], ['9007199254740992', '9007199254740993'], ['0', '1']])('compares canonical strings against available %s', (balance, amountVnd) => {
    const source = decodeAccount({ ...mockAccount, balanceVnd: balance, availableBalanceVnd: balance });
    expect(reviewTransfer({ ...fields(), amountVnd }, source)).toMatchObject({ ok: false, errors: { amountVnd: expect.any(String) } });
  });
  it('allows exact available balance and rejects FROZEN even with valid fields', () => {
    const source = decodeAccount({ ...mockAccount, balanceVnd: '200000', availableBalanceVnd: '200000' });
    expect(reviewTransfer(fields(), source).ok).toBe(true);
    expect(reviewTransfer(fields(), { ...source, status: 'FROZEN' })).toMatchObject({ ok: false, errors: { account: expect.any(String) } });
  });
  it('distinguishes omitted/empty note and preserves exact text and recipient', () => {
    expect(reviewTransfer({ ...fields(), note: 'discarded', includeNote: false }, account)).toEqual({ ok: true, draft: { recipientAccountNumber: 'DESTINATION', amountVnd: '200000' } });
    expect(reviewTransfer({ ...fields(), includeNote: true, note: '' }, account)).toMatchObject({ ok: true, draft: { note: '' } });
    expect(reviewTransfer({ ...fields(), recipientAccountNumber: '  A-α/1  ', includeNote: true, note: '  Ghi chú\n💚  ' }, account)).toMatchObject({ ok: true, draft: { recipientAccountNumber: '  A-α/1  ', note: '  Ghi chú\n💚  ' } });
  });
  it('snapshots only contract fields; no idempotency key, source, currency, command or outcome', () => {
    const input = { ...fields(), includeNote: true, note: 'original' }; const result = reviewTransfer(input, account);
    expect(result.ok).toBe(true); if (!result.ok) return;
    input.amountVnd = '1'; input.note = 'changed'; input.recipientAccountNumber = 'changed';
    expect(result.draft).toEqual({ recipientAccountNumber: 'DESTINATION', amountVnd: '200000', note: 'original' });
    expect(Object.isFrozen(result.draft)).toBe(true);
  });
});
