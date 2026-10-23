import React, { useState } from 'react';
import { Box, Grid, TextField } from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import { PieChart } from '@mui/x-charts/PieChart';
import { LineChart } from '@mui/x-charts/LineChart';
import { ChartCard } from '../../../shared/components/ChartCard';
import { CardholderTransaction } from '../../../types/cardholder';
import { parseMoney } from '../../../shared/utils/money';

export interface SpendChartsProps {
  transactions: CardholderTransaction[];
  loading?: boolean;
}

export const SpendCharts: React.FC<SpendChartsProps> = ({ transactions, loading = false }) => {
  const [dateRange, setDateRange] = useState({
    from: '2026-10-01',
    to: '2026-10-31',
  });

  // Calculate monthly spend from approved transactions
  const approvedTxns = transactions.filter((t) => t.status === 'APPROVED');

  // Compute status mix
  const statusCounts: Record<string, number> = {};
  transactions.forEach((t) => {
    statusCounts[t.status] = (statusCounts[t.status] || 0) + 1;
  });

  const statusPieData = [
    { id: 0, value: statusCounts['APPROVED'] || 0, label: 'Approved', color: '#2e7d32' },
    { id: 1, value: statusCounts['STEP_UP_PENDING'] || 0, label: 'Step-Up', color: '#ed6c02' },
    { id: 2, value: statusCounts['BLOCKED'] || 0, label: 'Blocked', color: '#d32f2f' },
  ].filter((item) => item.value > 0);

  // Spend by channel category
  const channelTotals: Record<string, number> = {};
  approvedTxns.forEach((t) => {
    channelTotals[t.channel] = (channelTotals[t.channel] || 0) + parseMoney(t.amount);
  });

  const channelPieData = Object.entries(channelTotals).map(([channel, total], index) => {
    const colors = ['#0A2342', '#1B5E8F', '#E8A000', '#3C8DCD'];
    return {
      id: index,
      value: Math.round(total),
      label: channel,
      color: colors[index % colors.length],
    };
  });

  // Monthly historical mock
  const monthsData = ['May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct'];
  const monthlySpendData = [18400, 24500, 21900, 31200, 28700, 39450];

  // Daily trend days
  const dailyDays = [1, 5, 10, 15, 20, 23];
  const dailySpend = [1250, 4500, 3499, 12999, 5000, 15000];

  return (
    <Box sx={{ mb: 4 }}>
      {/* Date Filter Bar */}
      <Box sx={{ display: 'flex', gap: 2, mb: 3, alignItems: 'center' }}>
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
      </Box>

      <Grid container spacing={3}>
        {/* Monthly Spend Bar Chart */}
        <Grid item xs={12} md={6}>
          <ChartCard title="Monthly Spend (Last 6 Months)" loading={loading} height={280}>
            <BarChart
              xAxis={[{ scaleType: 'band', data: monthsData }]}
              series={[
                {
                  data: monthlySpendData,
                  label: 'Spend (₹)',
                  color: '#1B5E8F',
                },
              ]}
              height={260}
            />
          </ChartCard>
        </Grid>

        {/* Daily Trend Line Chart */}
        <Grid item xs={12} md={6}>
          <ChartCard title="Daily Spend Trend (Current Month)" loading={loading} height={280}>
            <LineChart
              xAxis={[{ data: dailyDays, label: 'Day of Month' }]}
              series={[
                {
                  data: dailySpend,
                  label: 'Daily Amount (₹)',
                  color: '#E8A000',
                },
              ]}
              height={260}
            />
          </ChartCard>
        </Grid>

        {/* Spend by Channel Pie Donut */}
        <Grid item xs={12} md={6}>
          <ChartCard
            title="Spend by Payment Channel"
            empty={channelPieData.length === 0}
            loading={loading}
            height={280}
          >
            <PieChart
              series={[
                {
                  data: channelPieData,
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

        {/* Status Mix Donut */}
        <Grid item xs={12} md={6}>
          <ChartCard
            title="Transaction Status Mix"
            empty={statusPieData.length === 0}
            loading={loading}
            height={280}
          >
            <PieChart
              series={[
                {
                  data: statusPieData,
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
      </Grid>
    </Box>
  );
};
