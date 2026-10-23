import React from 'react';
import { Grid } from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import { PieChart } from '@mui/x-charts/PieChart';
import { LineChart } from '@mui/x-charts/LineChart';
import { ChartCard } from '../../../shared/components/ChartCard';
import { ManagerDashboardMetrics } from '../../../types/manager';

export interface DashboardChartsProps {
  metrics?: ManagerDashboardMetrics;
  loading?: boolean;
}

export const DashboardCharts: React.FC<DashboardChartsProps> = ({ metrics, loading = false }) => {
  const decisions = metrics?.transactions_by_decision || {
    APPROVE: 1106,
    STEP_UP: 101,
    BLOCK: 40,
  };

  const decisionPieData = [
    { id: 0, value: decisions.APPROVE, label: 'Approved', color: '#2e7d32' },
    { id: 1, value: decisions.STEP_UP, label: 'Step-Up', color: '#ed6c02' },
    { id: 2, value: decisions.BLOCK, label: 'Blocked', color: '#d32f2f' },
  ];

  const alertsByPriority = metrics?.open_alerts_by_priority || { HIGH: 5, MEDIUM: 9 };

  const topReasons = (metrics?.top_reason_codes || [
    { reason_code: 'IMPOSSIBLE_TRAVEL', count: 18 },
    { reason_code: 'AMOUNT_SPIKE', count: 14 },
    { reason_code: 'HIGH_RISK_GEO', count: 12 },
    { reason_code: 'NEW_MERCHANT', count: 9 },
    { reason_code: 'ODD_HOURS', count: 7 },
  ]).slice(0, 5);

  return (
    <Grid container spacing={3}>
      {/* 1. Transactions by Decision Over Time (Stacked Bar) */}
      <Grid item xs={12} md={6}>
        <ChartCard title="Transactions by Decision Trend" loading={loading} height={280}>
          <BarChart
            xAxis={[{ scaleType: 'band', data: ['Oct 20', 'Oct 21', 'Oct 22', 'Oct 23'] }]}
            series={[
              {
                data: [940, 1010, 1080, decisions.APPROVE],
                label: 'Approved',
                stack: 'decision',
                color: '#2e7d32',
              },
              {
                data: [78, 85, 94, decisions.STEP_UP],
                label: 'Step-Up',
                stack: 'decision',
                color: '#ed6c02',
              },
              {
                data: [25, 30, 35, decisions.BLOCK],
                label: 'Blocked',
                stack: 'decision',
                color: '#d32f2f',
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

      {/* 5. SLA Breach Trend */}
      <Grid item xs={12} md={4}>
        <ChartCard title="SLA Breaches Trend (Daily)" loading={loading} height={280}>
          <LineChart
            xAxis={[{ data: [19, 20, 21, 22, 23], label: 'Day' }]}
            series={[
              {
                data: [1, 2, 4, 2, metrics?.sla_breaches || 3],
                label: 'Breaches',
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
