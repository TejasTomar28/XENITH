import React from 'react';
import { Grid } from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import { PieChart } from '@mui/x-charts/PieChart';
import { ChartCard } from '../../../shared/components/ChartCard';
import { ManagerDashboardMetrics } from '../../../types/manager';

export interface DashboardChartsProps {
  metrics?: ManagerDashboardMetrics;
  loading?: boolean;
}

export const DashboardCharts: React.FC<DashboardChartsProps> = ({ metrics, loading = false }) => {
  const decisions = metrics?.transactions_by_decision || { APPROVE: 0, STEP_UP: 0, BLOCK: 0 };

  const decisionPieData = [
    { id: 0, value: decisions.APPROVE, label: 'Approved', color: '#2e7d32' },
    { id: 1, value: decisions.STEP_UP, label: 'Step-Up', color: '#ed6c02' },
    { id: 2, value: decisions.BLOCK, label: 'Blocked', color: '#d32f2f' },
  ];

  const alertsByPriority = metrics?.open_alerts_by_priority || { HIGH: 0, MEDIUM: 0 };

  const topReasons = (metrics?.top_reason_codes || []).slice(0, 5);

  return (
    <Grid container spacing={3}>
      {/* 1. Transactions by Decision for the selected period (the API returns period totals, not a daily series) */}
      <Grid item xs={12} md={6}>
        <ChartCard title="Transactions by Decision" loading={loading} height={280}>
          <BarChart
            xAxis={[{ scaleType: 'band', data: ['Approved', 'Step-Up', 'Blocked'] }]}
            series={[
              {
                data: [decisions.APPROVE, decisions.STEP_UP, decisions.BLOCK],
                label: 'Transactions',
                color: '#1B5E8F',
              },
            ]}
            height={260}
          />
        </ChartCard>
      </Grid>

      {/* 2. Overall Decision Distribution Donut */}
      <Grid item xs={12} md={6}>
        <ChartCard title="Decision Distribution Mix" loading={loading} height={280}>
          <PieChart
            series={[
              {
                data: decisionPieData,
                innerRadius: 55,
                outerRadius: 95,
                paddingAngle: 3,
                cornerRadius: 4,
              },
            ]}
            height={260}
          />
        </ChartCard>
      </Grid>

      {/* 3. Open Alerts by Priority */}
      <Grid item xs={12} md={4}>
        <ChartCard title="Open Alerts by Priority" loading={loading} height={280}>
          <BarChart
            xAxis={[{ scaleType: 'band', data: ['HIGH', 'MEDIUM'] }]}
            series={[
              {
                data: [alertsByPriority.HIGH, alertsByPriority.MEDIUM],
                label: 'Alert Count',
                color: '#d32f2f',
              },
            ]}
            height={260}
          />
        </ChartCard>
      </Grid>

      {/* 4. Top 5 Reason Codes */}
      <Grid item xs={12} md={4}>
        <ChartCard title="Top 5 Triggered Reason Codes" loading={loading} height={280}>
          <BarChart
            xAxis={[
              {
                scaleType: 'band',
                data: topReasons.map((r) => r.reason_code.replace(/_/g, ' ')),
              },
            ]}
            series={[
              {
                data: topReasons.map((r) => r.count),
                label: 'Triggers',
                color: '#1B5E8F',
              },
            ]}
            height={260}
          />
        </ChartCard>
      </Grid>

      {/* 5. SLA status of currently open alerts */}
      <Grid item xs={12} md={4}>
        <ChartCard title="Open Alerts vs SLA Breaches" loading={loading} height={280}>
          <BarChart
            xAxis={[{ scaleType: 'band', data: ['Open alerts', 'SLA breached'] }]}
            series={[
              {
                data: [alertsByPriority.HIGH + alertsByPriority.MEDIUM, metrics?.sla_breaches ?? 0],
                label: 'Alerts',
                color: '#b71c1c',
              },
            ]}
            height={260}
          />
        </ChartCard>
      </Grid>
    </Grid>
  );
};
