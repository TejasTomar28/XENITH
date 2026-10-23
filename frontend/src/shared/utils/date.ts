import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

export const IST_TIMEZONE = 'Asia/Kolkata';

/**
 * Format ISO-8601 timestamp in IST (Asia/Kolkata).
 * Format example: "23 Oct 2026, 02:30 PM IST"
 */
export function formatIstDateTime(dateStr: string | Date | null | undefined): string {
  if (!dateStr) return '—';
  const d = dayjs(dateStr).tz(IST_TIMEZONE);
  if (!d.isValid()) return '—';
  return `${d.format('DD MMM YYYY, hh:mm A')} IST`;
}

/**
 * Format ISO-8601 date in IST.
 * Format example: "23 Oct 2026"
 */
export function formatIstDate(dateStr: string | Date | null | undefined): string {
  if (!dateStr) return '—';
  const d = dayjs(dateStr).tz(IST_TIMEZONE);
  if (!d.isValid()) return '—';
  return d.format('DD MMM YYYY');
}

/**
 * Format time in IST.
 * Format example: "02:30 PM IST"
 */
export function formatIstTime(dateStr: string | Date | null | undefined): string {
  if (!dateStr) return '—';
  const d = dayjs(dateStr).tz(IST_TIMEZONE);
  if (!d.isValid()) return '—';
  return `${d.format('hh:mm A')} IST`;
}
