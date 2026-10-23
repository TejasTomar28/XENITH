import React, { useState } from 'react';
import { Box, Grid, Paper, Typography } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import AlarmOnIcon from '@mui/icons-material/AlarmOn';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import { DataGrid, GridColDef, GridRowParams } from '@mui/x-data-grid';
import { BarChart } from '@mui/x-charts/BarChart';
import { PieChart } from '@mui/x-charts/PieChart';
import { LineChart } from '@mui/x-charts/LineChart';
import { Gauge } from '@mui/x-charts/Gauge';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { analystApi } from '../../api/analyst.api';
import { AlertSummary } from '../../types/analyst';
import { KpiCard } from '../../shared/components/KpiCard';
import { ChartCard } from '../../shared/components/ChartCard';
import { PriorityChip } from '../../shared/components/PriorityChip';
import { StatusChip } from '../../shared/components/StatusChip';
import { CountdownChip } from '../../shared/components/CountdownChip';
import { MoneyText } from '../../shared/components/MoneyText';
import { MaskedCard } from '../../shared/components/MaskedCard';
import { RuleChips } from './components/RuleChips';
import { AlertFilters, AlertFilterValues } from './components/AlertFilters';

export const AlertQueue: React.FC = () => {
  const navigate = useNavigate();

  const [filterValues, setFilterValues] = useState<AlertFilterValues>({
    status: 'ALL',
    priority: 'ALL',
    mineOnly: false,
  });

  const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 10 });

  // Poll alerts every 5 seconds
  const { data, isLoading } = useQuery({
    queryKey: ['analyst', 'alerts', filterValues],
    queryFn: () =>
      analystApi.getAlerts({
        status: filterValues.status === 'ALL' ? undefined : filterValues.status,
        priority: filterValues.priority === 'ALL' ? undefined : filterValues.priority,
        claimed_by: filterValues.mineOnly ? 'me' : undefined,
      }),
    refetchInterval: 5000,
  });

  const alerts = data?.items || [];

  // Compute KPI counts
  const openAlertsCount = alerts.filter((a) => a.status === 'OPEN').length;
  const claimedCount = alerts.filter((a) => a.claimed_by === 'analyst1').length;
  const closedCount = alerts.filter(
    (a) => a.status === 'CONFIRMED_FRAUD' || a.status === 'FALSE_POSITIVE'
  ).length;
  const breachedCount = alerts.filter((a) => a.sla_breached).length;
  const slaCompliancePct = alerts.length > 0 ? Math.round(((alerts.length - breachedCount) / alerts.length) * 100) : 100;

  // Charts data
  const highPriorityCount = alerts.filter((a) => a.alert_priority === 'HIGH').length;
  const medPriorityCount = alerts.filter((a) => a.alert_priority === 'MEDIUM').length;

  const outcomeCounts: Record<string, number> = {};
  alerts.forEach((a) => {
    outcomeCounts[a.status] = (outcomeCounts[a.status] || 0) + 1;
  });

  const outcomePieData = [
    { id: 0, value: outcomeCounts['CONFIRMED_FRAUD'] || 0, label: 'Confirmed Fraud', color: '#d32f2f' },
    { id: 1, value: outcomeCounts['FALSE_POSITIVE'] || 0, label: 'False Positive', color: '#00897b' },
    { id: 2, value: outcomeCounts['PENDING_MANAGER_APPROVAL'] || 0, label: 'Pending Approval', color: '#7b1fa2' },
    { id: 3, value: (outcomeCounts['OPEN'] || 0) + (outcomeCounts['IN_REVIEW'] || 0), label: 'In Progress / Open', color: '#0288d1' },
  ].filter((item) => item.value > 0);

  const columns: GridColDef<AlertSummary>[] = [
    {
      field: 'alert_priority',
      headerName: 'Priority',
      flex: 0.8,
      minWidth: 100,
      renderCell: (params) => <PriorityChip priority={params.value} />,
    },
    {
      field: 'txn_id',
      headerName: 'Transaction ID',
      flex: 1.1,
      minWidth: 130,
      renderCell: (params) => (
        <Typography variant="body2" fontFamily="monospace" fontWeight={600}>
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'masked_card_number',
      headerName: 'Masked Card',
      flex: 1.2,
      minWidth: 150,
      renderCell: (params) => <MaskedCard value={params.value || 'XXXXXXXXXXXX1234'} />,
    },
    {
      field: 'amount',
      headerName: 'Amount (₹)',
      flex: 1,
      minWidth: 120,
      align: 'right',
      headerAlign: 'right',
      renderCell: (params) => <MoneyText value={params.value} fontWeight={600} />,
    },
    {
      field: 'reason_codes',
      headerName: 'Triggered Rules',
      flex: 1.8,
      minWidth: 220,
      renderCell: (params) => <RuleChips codes={params.value || []} />,
    },
    {
      field: 'status',
      headerName: 'Status',
      flex: 1.2,
      minWidth: 140,
      renderCell: (params) => <StatusChip status={params.value} />,
    },
    {
      field: 'sla_due_at',
      headerName: 'SLA Due',
      flex: 1.2,
      minWidth: 140,
      renderCell: (params) => {
        if (params.row.sla_breached) {
          return <StatusChip status="SLA_BREACHED" />;
        }
        return <CountdownChip expiresAt={params.value} />;
      },
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
          Fraud Alert Priority Queue
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Triage incoming suspicious transaction alerts, investigate multi-factor signals, and
          execute claim-and-resolve workflows.
        </Typography>
      </Box>

      {/* KPI Cards Strip */}
      <Grid container spacing={2.5} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="Open Alerts"
            value={openAlertsCount}
            subtitle="Awaiting analyst triage"
            icon={<WarningAmberIcon />}
            color="#d32f2f"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="My Claimed Alerts"
            value={claimedCount}
            subtitle="Currently under your review"
            icon={<AssignmentTurnedInIcon />}
            color="#0288d1"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="Closed Today"
            value={closedCount}
            subtitle="Fraud / False Positive resolutions"
            icon={<TaskAltIcon />}
            color="#00897b"
            loading={isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="SLA Compliance"
            value={`${slaCompliancePct}%`}
            subtitle={`${breachedCount} alerts breached SLA`}
            icon={<AlarmOnIcon />}
            color={slaCompliancePct >= 90 ? '#2e7d32' : '#d32f2f'}
            loading={isLoading}
          />
        </Grid>
      </Grid>

      {/* Analyst Visual Analytics */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <ChartCard title="SLA Compliance Rate" height={220} loading={isLoading}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
              <Gauge
                value={slaCompliancePct}
                startAngle={-110}
                endAngle={110}
                height={180}
                text={({ value }) => `${value}%`}
              />
            </Box>
          </ChartCard>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <ChartCard title="Priority Distribution" height={220} loading={isLoading}>
            <BarChart
              xAxis={[{ scaleType: 'band', data: ['HIGH', 'MEDIUM'] }]}
              series={[
                {
                  data: [highPriorityCount, medPriorityCount],
                  label: 'Alerts',
                  color: '#d32f2f',
                },
              ]}
              height={180}
            />
          </ChartCard>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <ChartCard title="Outcomes Split" height={220} loading={isLoading} empty={outcomePieData.length === 0}>
            <PieChart
              series={[
                {
                  data: outcomePieData,
                  innerRadius: 40,
                  outerRadius: 75,
                },
              ]}
              height={180}
            />
          </ChartCard>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <ChartCard title="Alert Inflow (Hours)" height={220} loading={isLoading}>
            <LineChart
              xAxis={[{ data: [1, 2, 3, 4, 5, 6], label: 'Hour' }]}
              series={[
                {
                  data: [2, 5, 3, 7, 4, alerts.length],
                  label: 'Inflow',
                  color: '#0A2342',
                },
              ]}
              height={180}
            />
          </ChartCard>
        </Grid>
      </Grid>

      {/* Main Alert Queue DataGrid */}
      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
        }}
      >
        <AlertFilters values={filterValues} onChange={setFilterValues} />

        <Box sx={{ height: 500, width: '100%' }}>
          <DataGrid
            rows={alerts}
            getRowId={(row) => row.alert_id}
            columns={columns}
            loading={isLoading}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
            pageSizeOptions={[10, 20, 50]}
            onRowClick={(params: GridRowParams<AlertSummary>) =>
              navigate(`/analyst/alerts/${params.row.alert_id}`)
            }
            getRowClassName={(params) =>
              params.row.sla_breached ? 'sla-breached-row' : ''
            }
            sx={{
              border: 'none',
              '& .MuiDataGrid-row': {
                cursor: 'pointer',
                transition: 'background-color 0.2s',
                '&:hover': {
                  bgcolor: 'action.hover',
                },
              },
              '& .sla-breached-row': {
                bgcolor: 'rgba(211, 47, 47, 0.05)',
                '&:hover': {
                  bgcolor: 'rgba(211, 47, 47, 0.12)',
                },
              },
            }}
            disableRowSelectionOnClick
          />
        </Box>
      </Paper>
    </Box>
  );
};
