import { useEffect, useState } from 'react';

export interface CountdownState {
  secondsRemaining: number;
  isExpired: boolean;
  formatted: string;
  totalSeconds: number;
}

export function useCountdown(targetDate: string | Date | null | undefined): CountdownState {
  const calculateRemaining = (): number => {
    if (!targetDate) return 0;
    const target = new Date(targetDate).getTime();
    const now = Date.now();
    const diff = Math.floor((target - now) / 1000);
    return diff > 0 ? diff : 0;
  };

  const [secondsRemaining, setSecondsRemaining] = useState<number>(calculateRemaining);

  useEffect(() => {
    setSecondsRemaining(calculateRemaining());
    if (!targetDate) return;

    const interval = setInterval(() => {
      const remaining = calculateRemaining();
      setSecondsRemaining(remaining);
      if (remaining <= 0) {
        clearInterval(interval);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [targetDate]);

  const isExpired = secondsRemaining <= 0;
  const minutes = Math.floor(secondsRemaining / 60);
  const seconds = secondsRemaining % 60;
  const formatted = isExpired
    ? 'Expired'
    : `${minutes}m ${seconds.toString().padStart(2, '0')}s`;

  return {
    secondsRemaining,
    isExpired,
    formatted,
    totalSeconds: secondsRemaining,
  };
}
