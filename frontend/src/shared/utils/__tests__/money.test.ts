import { describe, it, expect } from 'vitest';
import { formatMoney, parseMoney } from '../money';

describe('money utility', () => {
  it('formats numeric amounts as Indian Rupee with ₹ symbol', () => {
    const formatted = formatMoney(15000);
    // Remove non-breaking spaces if any from Intl output
    const clean = formatted.replace(/\u00a0/g, ' ');
    expect(clean).toContain('15,000.00');
  });

  it('formats string decimal numbers properly', () => {
    const formatted = formatMoney('100000.00');
    const clean = formatted.replace(/\u00a0/g, ' ');
    expect(clean).toContain('1,00,000.00');
  });

  it('handles null, undefined, or empty values with zero fallback', () => {
    expect(formatMoney(null).replace(/\u00a0/g, ' ')).toContain('0.00');
    expect(formatMoney(undefined).replace(/\u00a0/g, ' ')).toContain('0.00');
    expect(formatMoney('').replace(/\u00a0/g, ' ')).toContain('0.00');
  });

  it('parses string money values to float numbers correctly', () => {
    expect(parseMoney('15000.00')).toBe(15000);
    expect(parseMoney('₹1,50,000.00')).toBe(150000);
    expect(parseMoney(null)).toBe(0);
    expect(parseMoney(undefined)).toBe(0);
  });
});
