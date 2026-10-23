import { describe, it, expect } from 'vitest';
import { formatIstDateTime, formatIstDate } from '../date';

describe('date utility', () => {
  it('formats ISO-8601 timestamps in IST timezone with IST suffix', () => {
    // 2026-10-23T09:00:00Z -> in +05:30 it is 14:30 (02:30 PM)
    const formatted = formatIstDateTime('2026-10-23T09:00:00Z');
    expect(formatted).toContain('23 Oct 2026');
    expect(formatted).toContain('02:30 PM IST');
  });

  it('formats date only in IST', () => {
    const formatted = formatIstDate('2026-10-23T09:00:00Z');
    expect(formatted).toBe('23 Oct 2026');
  });

  it('returns em dash for null or undefined dates', () => {
    expect(formatIstDateTime(null)).toBe('—');
    expect(formatIstDateTime(undefined)).toBe('—');
    expect(formatIstDate(null)).toBe('—');
  });
});
