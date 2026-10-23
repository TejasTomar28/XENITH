import React from 'react';
import { Typography, TypographyProps } from '@mui/material';
import { formatIstDate, formatIstDateTime, formatIstTime } from '../utils/date';

export interface IstDateTimeProps extends Omit<TypographyProps, 'children'> {
  value: string | Date | null | undefined;
  dateOnly?: boolean;
  timeOnly?: boolean;
}

export const IstDateTime: React.FC<IstDateTimeProps> = ({
  value,
  dateOnly = false,
  timeOnly = false,
  ...typographyProps
}) => {
  let formatted = '—';
  if (dateOnly) {
    formatted = formatIstDate(value);
  } else if (timeOnly) {
    formatted = formatIstTime(value);
  } else {
    formatted = formatIstDateTime(value);
  }

  return (
    <Typography component="span" {...typographyProps}>
      {formatted}
    </Typography>
  );
};
