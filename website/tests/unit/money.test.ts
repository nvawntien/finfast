import { describe, expect, it } from 'vitest';
import { formatMoneyVnd, MAX_VND, parseMoneyVnd, parsePositiveMoneyVnd } from '../../src/lib/money';

describe('whole VND strings', () => {
  it.each(['0', '1', '9007199254740993', MAX_VND])('preserves %s exactly', value => {
    expect(parseMoneyVnd(value)).toBe(value);
  });
  it.each([0, 100, 1.5, null, undefined, '', '00', '01', '-1', '+1', '1.0', '1e3', ' 1', '1 ', '1\n', '1\r\n', '1\u2028', '1\u2029', '1,000', '9223372036854775808'])('rejects noncanonical or unsafe input %s', value => {
    expect(() => parseMoneyVnd(value)).toThrow(TypeError);
  });
  it('distinguishes zero balance from positive payment input', () => {
    expect(() => parsePositiveMoneyVnd('0')).toThrow(TypeError);
    expect(parsePositiveMoneyVnd('1')).toBe('1');
  });
  it('formats without Number conversion', () => {
    expect(formatMoneyVnd(parseMoneyVnd('9007199254740993'))).toBe('9.007.199.254.740.993 ₫');
  });
});
