import React, { useState } from 'react';
import {
  Box,
  FormControlLabel,
  Grid,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import BlockIcon from '@mui/icons-material/Block';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import AlarmOffIcon from '@mui/icons-material/AlarmOff';
import ThumbUpAltOutlinedIcon from '@mui/icons-material/ThumbUpAltOutlined';
import { useQuery } from '@tanstack/react-query';
import { managerApi } from '../../api/manager.api';
import { KpiCard } from '../../shared/components/KpiCard';
import { DashboardCharts } from './components/DashboardCharts';

export const ManagerDashboard: React.FC = () => {
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [dateRange, setDateRange] = useState({
    from: '',
    to: '',
  });

  const { data: metrics, isLoading } = useQuery({
    queryKey: ['manager', 'dashboard', dateRange],
    queryFn: () =>
      managerApi.getDashboard({
        from: dateRange.from || undefined,
        to: dateRange.to || undefined,
      }),
    refetchInterval: autoRefresh ? 30000 : false,
  });

  const totalTxns = metrics?.total_transactions ?? 0;
  const blockRate = ((metrics?.block_rate ?? 0) * 100).toFixed(1);
  const stepUpRate = ((metrics?.step_up_rate ?? 0) * 100).toFixed(1);
  const openAlerts = metrics
    ? metrics.open_alerts_by_priority.HIGH + metrics.open_alerts_by_priority.MEDIUM
    : 0;
  const slaBreaches = metrics?.sla_breaches ?? 0;
  const fpRate = metrics?.false_positive_rate !== null && metrics?.false_positive_rate !== undefined
    ? (metrics.false_positive_rate * 100).toFixed(1)
    : 'N/A';

  return (
    <Box>
      {/* Title & Controls Bar */}
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 3,
        }}
      >
        <Box>
          <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
            Fraud Risk Operations Executive Dashboard
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Portfolio-wide fraud metrics, decision distribution, SLA compliance, and governance
            overview.
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <TextField
            size="small"
            label="From Date"
            type="date"
            value={dateRange.from}
            onChange={(e) => setDateRange((prev) => ({ ...prev, from: e.target.value }))}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            size="small"
            label="To Date"
            type="date"
            value={dateRange.to}
            onChange={(e) => setDateRange((prev) => ({ ...prev, to: e.target.value }))}
            InputLabelProps={{ shrink: true }}
          />

          <FormControlLabel
            control={
              <Switch
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
                color="primary"
              />
            }
            label={<Typography variant="body2">Auto-Refresh (30s)</Typography>}
          />
        </Box>
      </Box>

      {/* KPI Cards Strip */}
      <Grid container spacing={2.5} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={2}>
          <KpiCard
            title="Total Authorizations"
            value={totalTxns}
            subtitle="Processed period"
            icon={<ReceiptLongIcon />}
            color="#0A2342"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={2}>
          <KpiCard
            title="Block Rate"
            value={`${blockRate}%`}
            subtitle="Stopped by rules / AI"
            icon={<BlockIcon />}
            color="#d32f2f"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={2}>
          <KpiCard
            title="Step-Up Rate"
            value={`${stepUpRate}%`}
            subtitle="OTP verification"
            icon={<VpnKeyIcon />}
            color="#E8A000"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={2}>
          <KpiCard
            title="Open Alerts"
            value={openAlerts}
            subtitle="In analyst queue"
            icon={<WarningAmberIcon />}
            color="#1B5E8F"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={2}>
          <KpiCard
            title="SLA Breaches"
            value={slaBreaches}
            subtitle="Overdue investigations"
            icon={<AlarmOffIcon />}
            color="#b71c1c"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={2}>
          <KpiCard
            title="False-Positive Rate"
            value={`${fpRate}%`}
            subtitle="Of closed alerts"
            icon={<ThumbUpAltOutlinedIcon />}
            color="#00897b"
            loading={isLoading}
          />
        </Grid>
      </Grid>

      {/* Charts Section */}
      <DashboardCharts metrics={metrics} loading={isLoading} />
    </Box>
  );
};
