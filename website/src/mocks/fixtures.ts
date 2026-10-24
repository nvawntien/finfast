/** Synthetic fixtures only. Never import from production API modules or screens. */
export const mockIdentity = { userId: 'MOCK-CUSTOMER', role: 'CUSTOMER', displayName: 'Khách hàng mô phỏng' };
export const mockCredentials = { email: 'customer@example.test', password: 'mock-only-password' };
export const mockAccount = {
  accountNumber: 'MOCK-ACCOUNT-001', type: 'CUSTOMER', status: 'ACTIVE', currency: 'VND',
  balanceVnd: '9007199254740993', availableBalanceVnd: '9007199254740993',
};
export const mockFrozenCredentials = { email: 'frozen@example.test', password: 'mock-only-password' };
export const mockFrozenIdentity = { userId: 'MOCK-FROZEN-CUSTOMER', role: 'CUSTOMER', displayName: 'Khách hàng bị phong tỏa' };
export const mockFrozenAccount = { ...mockAccount, accountNumber: 'MOCK-FROZEN-ACCOUNT', status: 'FROZEN', balanceVnd: '250000', availableBalanceVnd: '250000' };
export const mockHistoryItem = {
  reference: 'MOCK-TX-001', type: 'INTERNAL_TRANSFER', direction: 'OUTGOING', amountVnd: '200000',
  currency: 'VND', counterpartyLabel: 'MOCK-ACCOUNT-002', committedAt: '2026-10-23T00:00:00Z',
};
export const mockReceipt = {
  reference: mockHistoryItem.reference, type: mockHistoryItem.type, amountVnd: mockHistoryItem.amountVnd,
  currency: 'VND', sourceLabel: mockAccount.accountNumber, destinationLabel: mockHistoryItem.counterpartyLabel,
  committedAt: mockHistoryItem.committedAt, status: 'COMPLETED',
};
// A separate frozen principal with 105 committed synthetic records, including timestamp ties.
export const mockFrozenHistory = Array.from({ length: 105 }, (_, index) => ({
  reference: `MOCK-FROZEN-TX-${String(105 - index).padStart(3, '0')}`,
  type: index === 0 ? 'DEMO_ALLOCATION' : 'INTERNAL_TRANSFER',
  direction: index % 2 === 0 && index !== 0 ? 'OUTGOING' : 'INCOMING',
  amountVnd: index === 0 ? '250000' : '1000', currency: 'VND',
  counterpartyLabel: index === 0 ? 'Treasury (mô phỏng)' : 'MOCK-COUNTERPARTY',
  committedAt: '2026-10-23T00:00:00Z',
}));
export const mockFrozenReceipts = mockFrozenHistory.map(item => ({
  reference: item.reference, type: item.type, amountVnd: item.amountVnd, currency: item.currency,
  sourceLabel: item.direction === 'INCOMING' ? item.counterpartyLabel : mockFrozenAccount.accountNumber,
  destinationLabel: item.direction === 'INCOMING' ? mockFrozenAccount.accountNumber : item.counterpartyLabel,
  committedAt: item.committedAt, status: 'COMPLETED',
}));
