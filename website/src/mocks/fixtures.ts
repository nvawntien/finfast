/** Synthetic fixtures only. Never import from production API modules or screens. */
export const mockIdentity = { userId: 'MOCK-CUSTOMER', role: 'CUSTOMER', displayName: 'Khách hàng mô phỏng' };
export const mockCredentials = { email: 'customer@example.test', password: 'mock-only-password' };
export const mockAccount = {
  accountNumber: 'MOCK-ACCOUNT-001', type: 'CUSTOMER', status: 'ACTIVE', currency: 'VND',
  balanceVnd: '9007199254740993', availableBalanceVnd: '9007199254740993',
};
export const mockHistoryItem = {
  reference: 'MOCK-TX-001', type: 'INTERNAL_TRANSFER', direction: 'OUTGOING', amountVnd: '200000',
  currency: 'VND', counterpartyLabel: 'MOCK-ACCOUNT-002', committedAt: '2026-10-23T00:00:00Z',
};
export const mockReceipt = {
  reference: mockHistoryItem.reference, type: mockHistoryItem.type, amountVnd: mockHistoryItem.amountVnd,
  currency: 'VND', sourceLabel: mockAccount.accountNumber, destinationLabel: mockHistoryItem.counterpartyLabel,
  committedAt: mockHistoryItem.committedAt, status: 'COMPLETED',
};
