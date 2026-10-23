import React from 'react';
import { Navigate, RouteObject } from 'react-router-dom';
import { AlertQueue } from './AlertQueue';
import { AlertDetail } from './AlertDetail';
import { Box, Card, CardContent, Grid, Typography } from '@mui/material';
import { KpiCard } from '../../shared/components/KpiCard';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import SpeedIcon from '@mui/icons-material/Speed';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

// My Stats Subpage
const AnalystStatsPage: React.FC = () => {
  return (
    <Box>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 1 }}>
        My Analyst Performance & Metrics
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Personal queue throughput, review duration, and SLA adherence statistics.
      </Typography>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={4}>
          <KpiCard
            title="Total Alerts Resolved"
            value="42"
            subtitle="Current calendar month"
            icon={<TaskAltIcon />}
            color="#0A2342"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            title="Average Resolution Time"
            value="8.4m"
            subtitle="Well within 30m SLA"
            icon={<SpeedIcon />}
            color="#2e7d32"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            title="SLA Compliance Rate"
            value="97.6%"
            subtitle="Only 1 overdue breach"
            icon={<CheckCircleIcon />}
            color="#1B5E8F"
          />
        </Grid>
      </Grid>

      <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
        <CardContent>
          <Typography variant="h6" fontWeight={700} gutterBottom>
            Queue SLA Guidelines
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            HIGH Priority alerts carry a strict 30-minute resolution SLA from the moment of creation.
            MEDIUM Priority alerts carry a 240-minute (4-hour) resolution SLA.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Resolving transactions strictly above ₹100,000 as False Positive automatically triggers
            maker-checker governance, requiring secondary review by a Fraud Risk Manager.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
};

export const analystRoutes: RouteObject[] = [
  {
    path: '/analyst',
    element: <Navigate to="/analyst/alerts" replace />,
  },
  {
    path: '/analyst/alerts',
    element: <AlertQueue />,
  },
  {
    path: '/analyst/alerts/:id',
    element: <AlertDetail />,
  },
  {
    path: '/analyst/stats',
    element: <AnalystStatsPage />,
  },
];
