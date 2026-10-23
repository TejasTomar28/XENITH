import React from 'react';
import { RouteObject } from 'react-router-dom';
import { CardholderDashboard } from './CardholderDashboard';
import { CardCarousel } from './components/CardCarousel';
import { TransactionGrid } from './components/TransactionGrid';
import { StepUpBanner } from './components/StepUpBanner';
import { Box, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { cardholderApi } from '../../api/cardholder.api';

// Dedicated Cards Subpage
const CardsPage: React.FC = () => {
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['me', 'cards'],
    queryFn: () => cardholderApi.getCards(),
  });

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 1 }}>
        My Payment Cards
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Manage active credit and debit cards, review spending limits, and execute self-service card
        locking.
      </Typography>
      <CardCarousel
        cards={data?.items || []}
        loading={isLoading}
        onCardUpdated={() => refetch()}
      />
    </Box>
  );
};

// Dedicated Transactions Subpage
const TransactionsPage: React.FC = () => {
  const txnsQuery = useQuery({
    queryKey: ['me', 'transactions'],
    queryFn: () => cardholderApi.getTransactions(),
  });

  const cardsQuery = useQuery({
    queryKey: ['me', 'cards'],
    queryFn: () => cardholderApi.getCards(),
  });

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 1 }}>
        Transaction History
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Detailed audit of all posted and real-time transaction authorizations.
      </Typography>
      <TransactionGrid
        transactions={txnsQuery.data?.items || []}
        loading={txnsQuery.isLoading}
        cardsList={cardsQuery.data?.items || []}
      />
    </Box>
  );
};

// Dedicated Step-ups Subpage
const StepUpsPage: React.FC = () => {
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'step-ups'],
    queryFn: () => cardholderApi.getStepUps(),
    refetchInterval: 10000,
  });

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 1 }}>
        Step-Up Verifications
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        High-risk transactions flagged by security policy requiring your explicit OTP authorization.
      </Typography>

      <StepUpBanner />

      {!isLoading && (data?.items?.length || 0) === 0 && (
        <Typography variant="body2" color="text.secondary">
          No pending step-up verifications at this time. All transactions are up to date.
        </Typography>
      )}
    </Box>
  );
};

export const cardholderRoutes: RouteObject[] = [
  {
    path: '/customer',
    element: <CardholderDashboard />,
  },
  {
    path: '/customer/cards',
    element: <CardsPage />,
  },
  {
    path: '/customer/transactions',
    element: <TransactionsPage />,
  },
  {
    path: '/customer/step-ups',
    element: <StepUpsPage />,
  },
];
