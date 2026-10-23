import React from 'react';
import { Chip, ChipProps } from '@mui/material';
import { STATUS_COLORS } from '../../app/theme';

export interface StatusChipProps extends Omit<ChipProps, 'color'> {
  status: string | null | undefined;
}

export const StatusChip: React.FC<StatusChipProps> = ({ status, size = 'small', sx, ...props }) => {
  if (!status) return null;

  const colorConfig = STATUS_COLORS[status] || {
    main: '#757575',
    bg: '#f5f5f5',
    text: '#424242',
  };

  const formattedLabel = status.replace(/_/g, ' ');

  return (
    <Chip
      size={size}
      label={formattedLabel}
      sx={{
        bgcolor: colorConfig.bg,
        color: colorConfig.text,
        fontWeight: 600,
        fontSize: '0.75rem',
        border: `1px solid ${colorConfig.main}40`,
        ...sx,
      }}
      {...props}
    />
  );
};
