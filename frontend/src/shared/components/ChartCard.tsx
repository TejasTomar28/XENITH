import React from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  CardHeader,
  Skeleton,
  Typography,
} from '@mui/material';
import { EmptyState } from './EmptyState';

export interface ChartCardProps {
  title: string;
  subheader?: string;
  action?: React.ReactNode;
  loading?: boolean;
  error?: string | null;
  empty?: boolean;
  emptyMessage?: string;
  height?: number | string;
  children: React.ReactNode;
}

export const ChartCard: React.FC<ChartCardProps> = ({
  title,
  subheader,
  action,
  loading = false,
  error = null,
  empty = false,
  emptyMessage = 'No chart data available for the selected period.',
  height = 300,
  children,
}) => {
  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardHeader
        title={<Typography variant="h6">{title}</Typography>}
        subheader={subheader}
        action={action}
        sx={{ pb: 0 }}
      />
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', pt: 1 }}>
        {loading ? (
          <Box sx={{ flex: 1, minHeight: height, display: 'flex', flexDirection: 'column', gap: 1 }}>
            <Skeleton variant="rectangular" height="100%" sx={{ borderRadius: 1 }} />
          </Box>
        ) : error ? (
          <Box sx={{ py: 4, display: 'flex', justifyContent: 'center' }}>
            <Alert severity="error">{error}</Alert>
          </Box>
        ) : empty ? (
          <Box sx={{ flex: 1, minHeight: height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <EmptyState title="No data to visualize" description={emptyMessage} />
          </Box>
        ) : (
          <Box sx={{ flex: 1, width: '100%', minHeight: height }}>
            {children}
          </Box>
        )}
      </CardContent>
    </Card>
  );
};
