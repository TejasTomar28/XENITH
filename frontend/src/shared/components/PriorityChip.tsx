import React from 'react';
import { Chip, ChipProps } from '@mui/material';
import { PRIORITY_COLORS } from '../../app/theme';
import { AlertPriority } from '../../types/common';

export interface PriorityChipProps extends Omit<ChipProps, 'color'> {
  priority: AlertPriority | string | null | undefined;
}

export const PriorityChip: React.FC<PriorityChipProps> = ({
  priority,
  size = 'small',
  sx,
  ...props
}) => {
  if (!priority) return null;

  const key = priority === 'HIGH' ? 'HIGH' : 'MEDIUM';
  const colorConfig = PRIORITY_COLORS[key];

  return (
    <Chip
      size={size}
      label={priority}
      sx={{
        bgcolor: colorConfig.bg,
        color: colorConfig.text,
        fontWeight: 700,
        fontSize: '0.75rem',
        border: `1px solid ${colorConfig.main}50`,
        ...sx,
      }}
      {...props}
    />
  );
};
