import React from 'react';
import { Box, Typography, TypographyProps } from '@mui/material';
import CreditCardIcon from '@mui/icons-material/CreditCard';

export interface MaskedCardProps extends Omit<TypographyProps, 'children'> {
  value: string | null | undefined;
  showIcon?: boolean;
}

export const MaskedCard: React.FC<MaskedCardProps> = ({
  value,
  showIcon = false,
  ...typographyProps
}) => {
  if (!value) {
    return <Typography component="span">—</Typography>;
  }

  // Ensure last 4 digits format: XXXXXXXXXXXX1234
  const digits = value.replace(/\D/g, '');
  const last4 = digits.length >= 4 ? digits.slice(-4) : value.slice(-4);
  const formatted = `XXXXXXXXXXXX${last4}`;

  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
      {showIcon && <CreditCardIcon fontSize="small" sx={{ color: 'text.secondary' }} />}
      <Typography
        component="span"
        fontFamily="monospace"
        fontWeight={600}
        letterSpacing={1}
        {...typographyProps}
      >
        {formatted}
      </Typography>
    </Box>
  );
};
