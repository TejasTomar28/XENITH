import React from 'react';
import { Typography, TypographyProps } from '@mui/material';
import { formatMoney } from '../utils/money';

export interface MoneyTextProps extends Omit<TypographyProps, 'children'> {
  value: string | number | null | undefined;
}

export const MoneyText: React.FC<MoneyTextProps> = ({ value, ...typographyProps }) => {
  return (
    <Typography component="span" fontWeight="inherit" {...typographyProps}>
      {formatMoney(value)}
    </Typography>
  );
};
