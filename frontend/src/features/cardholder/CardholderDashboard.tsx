import React, { useState } from 'react';
import { Box, Grid, Typography } from '@mui/material';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined';
import VpnKeyOutlinedIcon from '@mui/icons-material/VpnKeyOutlined';
import { useQuery } from '@tanstack/react-query';
import { cardholderApi } from '../../api/cardholder.api';
import { KpiCard } from '../../shared/components/KpiCard';
import { formatMoney, parseMoney } from '../../shared/utils/money';
import { CardCarousel } from './components/CardCarousel';
import { StepUpBanner } from './components/StepUpBanner';
import { SpendCharts } from './components/SpendCharts';
import { TransactionGrid } from './components/TransactionGrid';

export const CardholderDashboard: React.FC = () => {
  const [selectedCardId, setSelectedCardId] = useState<string>('ALL');

  // Query cards
  const cardsQuery = useQuery({
    queryKey: ['me', 'cards'],
    queryFn: () => cardholderApi.getCards(),
  });

  // Query transactions
  const txnsQuery = useQuery({
    queryKey: ['me', 'transactions'],
    queryFn: () => cardholderApi.getTransactions(),
  });

  // Query step-ups
  const stepUpsQuery = useQuery({
    queryKey: ['me', 'step-ups'],
    queryFn: () => cardholderApi.getStepUps(),
    refetchInterval: 10000,
  });

  const cards = cardsQuery.data?.items || [];
  const transactions = txnsQuery.data?.items || [];
  const stepUps = stepUpsQuery.data?.items || [];

  // Compute KPIs
  const spendThisMonth = transactions
    .filter((t) => t.status === 'APPROVED')
    .reduce((sum, t) => sum + parseMoney(t.amount), 0);

  const blockedCount = transactions.filter((t) => t.status === 'BLOCKED').length;
  const pendingStepUpsCount = stepUps.length;

  return (
    <Box>
      {/* Page Title & Intro */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
          Cardholder Portal
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Monitor your card activity, review real-time security alerts, and authorize high-value
          step-up verifications.
        </Typography>
      </Box>

      {/* Prominent Step-Up Banner (if any pending) */}
      <StepUpBanner />

      {/* KPI Cards Strip */}
      <Grid container spacing={2.5} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="Spend This Month"
            value={formatMoney(spendThisMonth)}
            subtitle="Sum of approved transactions"
            icon={<AccountBalanceWalletOutlinedIcon />}
            color="#0A2342"
            loading={txnsQuery.isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="Total Transactions"
            value={transactions.length}
            subtitle="Recent activity"
            icon={<ReceiptLongOutlinedIcon />}
            color="#1B5E8F"
            loading={txnsQuery.isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="Declined / Blocked"
            value={blockedCount}
            subtitle="Stopped by security policy"
            icon={<BlockOutlinedIcon />}
            color="#d32f2f"
            loading={txnsQuery.isLoading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <KpiCard
            title="Pending Step-Ups"
            value={pendingStepUpsCount}
            subtitle="Awaiting your OTP authorization"
            icon={<VpnKeyOutlinedIcon />}
            color="#E8A000"
            loading={stepUpsQuery.isLoading}
          />
        </Grid>
      </Grid>

      {/* My Cards Section */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h6" fontWeight={700} sx={{ mb: 2 }}>
          My Cards
        </Typography>
        <CardCarousel
          cards={cards}
          loading={cardsQuery.isLoading}
          onCardUpdated={() => cardsQuery.refetch()}
        />
      </Box>

      {/* Visual Analytics */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h6" fontWeight={700} sx={{ mb: 2 }}>
          Spend Analytics
        </Typography>
        <SpendCharts transactions={transactions} loading={txnsQuery.isLoading} />
      </Box>

      {/* Transactions DataGrid */}
      <Box sx={{ mb: 4 }}>
        <TransactionGrid
          transactions={transactions}
          loading={txnsQuery.isLoading}
          selectedCardId={selectedCardId}
          onCardChange={setSelectedCardId}
          cardsList={cards}
        />
      </Box>
    </Box>
  );
};
