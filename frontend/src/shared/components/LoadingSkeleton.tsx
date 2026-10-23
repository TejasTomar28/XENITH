import React from 'react';
import { Box, Card, CardContent, Skeleton } from '@mui/material';

export interface LoadingSkeletonProps {
  variant?: 'card' | 'table' | 'chart';
  height?: number | string;
}

export const LoadingSkeleton: React.FC<LoadingSkeletonProps> = ({
  variant = 'card',
  height = 200,
}) => {
  if (variant === 'table') {
    return (
      <Box sx={{ width: '100%', py: 2 }}>
        <Skeleton variant="rectangular" height={48} sx={{ mb: 1, borderRadius: 1 }} />
        <Skeleton variant="rectangular" height={40} sx={{ mb: 1, borderRadius: 1 }} />
        <Skeleton variant="rectangular" height={40} sx={{ mb: 1, borderRadius: 1 }} />
        <Skeleton variant="rectangular" height={40} sx={{ borderRadius: 1 }} />
      </Box>
    );
  }

  if (variant === 'chart') {
    return (
      <Card sx={{ height, p: 2 }}>
        <Skeleton variant="text" width="40%" height={32} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height="calc(100% - 48px)" sx={{ borderRadius: 1 }} />
      </Card>
    );
  }

  return (
    <Card sx={{ height }}>
      <CardContent>
        <Skeleton variant="text" width="50%" height={28} sx={{ mb: 1 }} />
        <Skeleton variant="text" width="80%" height={20} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={80} sx={{ borderRadius: 1 }} />
      </CardContent>
    </Card>
  );
};
