import React from 'react';
import { Chip, ChipProps } from '@mui/material';
import TimerIcon from '@mui/icons-material/Timer';
import { useCountdown } from '../hooks/useCountdown';

export interface CountdownChipProps extends Omit<ChipProps, 'color'> {
  expiresAt: string | Date | null | undefined;
}

export const CountdownChip: React.FC<CountdownChipProps> = ({
  expiresAt,
  size = 'small',
  sx,
  ...props
}) => {
  const { secondsRemaining, isExpired, formatted } = useCountdown(expiresAt);

  let bg = '#e8f5e9'; // Green > 5 min
  let text = '#1b5e20';
  let border = '#2e7d32';

  if (isExpired) {
    bg = '#ffebee';
    text = '#c62828';
    border = '#d32f2f';
  } else if (secondsRemaining < 60) {
    // Red < 1 min
    bg = '#ffebee';
    text = '#c62828';
    border = '#d32f2f';
  } else if (secondsRemaining <= 300) {
    // Orange 1-5 min
    bg = '#fff3e0';
    text = '#e65100';
    border = '#f57c00';
  }

  return (
    <Chip
      icon={<TimerIcon style={{ color: text }} />}
      size={size}
      label={formatted}
      sx={{
        bgcolor: bg,
        color: text,
        fontWeight: 600,
        fontSize: '0.75rem',
        border: `1px solid ${border}40`,
        ...sx,
      }}
      {...props}
    />
  );
};
