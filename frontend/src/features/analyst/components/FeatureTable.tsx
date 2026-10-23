import React from 'react';
import {
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { FeaturesUsed } from '../../../types/analyst';
import { MoneyText } from '../../../shared/components/MoneyText';

export interface FeatureTableProps {
  features?: FeaturesUsed;
}

export const FeatureTable: React.FC<FeatureTableProps> = ({ features }) => {
  if (!features) {
    return (
      <Typography variant="body2" color="text.secondary">
        No feature telemetry recorded for this decision.
      </Typography>
    );
  }

  const rows = [
    {
      name: 'velocity_10m',
      label: '10-Minute Velocity',
      value: features.velocity_10m !== null ? `${features.velocity_10m} txns` : '—',
      desc: 'Count of prior transactions on this card in the last 10 minutes',
    },
    {
      name: 'approved_today',
      label: 'Approved Spend Today',
      value: features.approved_today ? <MoneyText value={features.approved_today} /> : '—',
      desc: 'Sum of approved transactions on the same IST calendar date',
    },
    {
      name: 'avg_amount_30d',
      label: '30-Day Average Spend',
      value: features.avg_amount_30d ? <MoneyText value={features.avg_amount_30d} /> : '—',
      desc: 'Historical mean of prior approved transactions in the last 30 days',
    },
    {
      name: 'speed_kmph',
      label: 'Physical Travel Speed',
      value: features.speed_kmph !== null ? `${features.speed_kmph.toFixed(2)} km/h` : '—',
      desc: 'Haversine distance divided by elapsed time from prior approved physical transaction',
    },
    {
      name: 'new_merchant_flag',
      label: 'New Merchant Indicator',
      value: features.new_merchant_flag !== null ? (features.new_merchant_flag ? 'YES' : 'NO') : '—',
      desc: 'Whether this card has had any transactions with this merchant in the past 90 days',
    },
  ];

  return (
    <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
      <Table size="small">
        <TableHead sx={{ bgcolor: 'action.hover' }}>
          <TableRow>
            <TableCell sx={{ fontWeight: 700 }}>Telemetry Feature</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Observed Value</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Context & Definition</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={r.name} hover>
              <TableCell sx={{ fontWeight: 600 }}>{r.label}</TableCell>
              <TableCell sx={{ fontFamily: 'monospace', fontWeight: 600 }}>{r.value}</TableCell>
              <TableCell sx={{ color: 'text.secondary', fontSize: '0.8rem' }}>{r.desc}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};
