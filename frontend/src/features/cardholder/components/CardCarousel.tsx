import React, { useState } from 'react';
import {
  Box,
  Button,
  Card,
  Chip,
  Grid,
  Skeleton,
  Switch,
  Typography,
} from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import PublicIcon from '@mui/icons-material/Public';
import SecurityIcon from '@mui/icons-material/Security';
import { CardView } from '../../../types/cardholder';
import { cardholderApi } from '../../../api/cardholder.api';
import { MaskedCard } from '../../../shared/components/MaskedCard';
import { MoneyText } from '../../../shared/components/MoneyText';
import { StatusChip } from '../../../shared/components/StatusChip';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { useSnackbar } from 'notistack';

export interface CardCarouselProps {
  cards: CardView[];
  loading?: boolean;
  onCardUpdated?: () => void;
}

export const CardCarousel: React.FC<CardCarouselProps> = ({
  cards,
  loading = false,
  onCardUpdated,
}) => {
  const { enqueueSnackbar } = useSnackbar();
  const [blockingCard, setBlockingCard] = useState<CardView | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleConfirmBlock = async () => {
    if (!blockingCard) return;
    setIsSubmitting(true);
    try {
      await cardholderApi.blockCard(blockingCard.card_id);
      enqueueSnackbar(`Card ending in ${blockingCard.masked_card_number.slice(-4)} has been blocked.`, {
        variant: 'warning',
      });
      setBlockingCard(null);
      if (onCardUpdated) {
        onCardUpdated();
      }
    } catch (err: any) {
      enqueueSnackbar(err.message || 'Failed to block card. Please try again.', {
        variant: 'error',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Grid container spacing={3}>
        {[1, 2].map((i) => (
          <Grid item xs={12} sm={6} md={6} key={i}>
            <Skeleton variant="rectangular" height={220} sx={{ borderRadius: 3 }} />
          </Grid>
        ))}
      </Grid>
    );
  }

  return (
    <>
      <Grid container spacing={3}>
        {cards.map((card) => {
          const isBlocked = card.card_status === 'BLOCKED';
          const isCredit = card.card_type === 'CREDIT';

          return (
            <Grid item xs={12} sm={6} md={6} key={card.card_id}>
              <Card
                sx={{
                  height: '100%',
                  minHeight: 220,
                  borderRadius: 4,
                  position: 'relative',
                  overflow: 'hidden',
                  background: isBlocked
                    ? 'linear-gradient(135deg, #374151 0%, #111827 100%)'
                    : isCredit
                    ? 'linear-gradient(135deg, #1e293b 0%, #0f172a 50%, #334155 100%)'
                    : 'linear-gradient(135deg, #0A2342 0%, #1B5E8F 100%)',
                  color: '#ffffff',
                  boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.25)',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  p: 3,
                }}
              >
                {/* Card Top Row */}
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <SecurityIcon sx={{ color: '#E8A000', fontSize: 24 }} />
                    <Typography variant="subtitle2" fontWeight={700} letterSpacing={0.5}>
                      Kaveri National Bank
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip
                      label={card.card_type}
                      size="small"
                      sx={{
                        bgcolor: 'rgba(255,255,255,0.15)',
                        color: '#ffffff',
                        fontWeight: 700,
                        fontSize: '0.7rem',
                      }}
                    />
                    <StatusChip status={card.card_status} />
                  </Box>
                </Box>

                {/* EMV Chip & Contactless Visual */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, my: 1.5 }}>
                  <Box
                    sx={{
                      width: 44,
                      height: 32,
                      bgcolor: '#E8A000',
                      borderRadius: 1.5,
                      border: '1px solid #b37d00',
                      background: 'linear-gradient(135deg, #fcd34d 0%, #b45309 100%)',
                    }}
                  />
                  <CreditCardIcon sx={{ color: 'rgba(255,255,255,0.5)', fontSize: 24 }} />
                </Box>

                {/* Card Number */}
                <Box sx={{ my: 1 }}>
                  <MaskedCard
                    value={card.masked_card_number}
                    sx={{
                      fontSize: { xs: '1.1rem', sm: '1.25rem' },
                      color: '#ffffff',
                      letterSpacing: 2,
                    }}
                  />
                </Box>

                {/* Card Bottom Details */}
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'flex-end',
                    justifyContent: 'space-between',
                    mt: 1,
                  }}
                >
                  <Box>
                    <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.6)', display: 'block' }}>
                      DAILY LIMIT
                    </Typography>
                    <MoneyText
                      value={card.daily_limit}
                      sx={{ color: '#ffffff', fontWeight: 600, fontSize: '0.9rem' }}
                    />
                  </Box>

                  <Box>
                    <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.6)', display: 'block' }}>
                      EXPIRES
                    </Typography>
                    <Typography variant="body2" sx={{ color: '#ffffff', fontWeight: 600 }}>
                      {card.expiry_date}
                    </Typography>
                  </Box>

                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <PublicIcon sx={{ fontSize: 16, color: 'rgba(255,255,255,0.7)' }} />
                    <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.8)' }}>
                      Intl:
                    </Typography>
                    <Switch
                      size="small"
                      checked={card.international_enabled}
                      disabled
                      sx={{
                        '& .MuiSwitch-switchBase.Mui-checked': {
                          color: '#E8A000',
                        },
                        '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': {
                          backgroundColor: '#E8A000',
                        },
                      }}
                    />
                  </Box>
                </Box>

                {/* Quick Block Action if active */}
                {!isBlocked && (
                  <Box sx={{ mt: 2, pt: 1.5, borderTop: '1px solid rgba(255,255,255,0.1)' }}>
                    <Button
                      size="small"
                      variant="outlined"
                      color="error"
                      startIcon={<BlockIcon />}
                      onClick={() => setBlockingCard(card)}
                      sx={{
                        color: '#ffcdd2',
                        borderColor: 'rgba(239, 83, 80, 0.5)',
                        '&:hover': {
                          bgcolor: 'rgba(239, 83, 80, 0.2)',
                          borderColor: '#ef5350',
                        },
                      }}
                    >
                      Block Card Permanently
                    </Button>
                  </Box>
                )}
              </Card>
            </Grid>
          );
        })}
      </Grid>

      {/* Confirm Block Dialog */}
      <ConfirmDialog
        open={Boolean(blockingCard)}
        title="Permanently Block Card?"
        message={
          blockingCard
            ? `Are you sure you want to block your card ending in ${blockingCard.masked_card_number.slice(
                -4
              )}? This action is permanent and cannot be undone via self-service.`
            : ''
        }
        warning="All future transaction attempts on this card will be declined immediately."
        confirmText="Yes, Block Card"
        danger
        loading={isSubmitting}
        onConfirm={handleConfirmBlock}
        onCancel={() => setBlockingCard(null)}
      />
    </>
  );
};
