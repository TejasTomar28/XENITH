import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined';
import VpnKeyOutlinedIcon from '@mui/icons-material/VpnKeyOutlined';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cardholderApi } from '../../../api/cardholder.api';
import { PendingStepUp } from '../../../types/cardholder';
import { MoneyText } from '../../../shared/components/MoneyText';
import { CountdownChip } from '../../../shared/components/CountdownChip';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { useSnackbar } from 'notistack';

export const StepUpBanner: React.FC = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();
  const [otpMap, setOtpMap] = useState<Record<string, string>>({});
  const [notMeTarget, setNotMeTarget] = useState<PendingStepUp | null>(null);

  // Poll step-ups every 10 seconds
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'step-ups'],
    queryFn: () => cardholderApi.getStepUps(),
    refetchInterval: 10000,
  });

  const stepUps: PendingStepUp[] = data?.items || [];

  const responseMutation = useMutation({
    mutationFn: ({
      stepUpId,
      response,
      otp,
    }: {
      stepUpId: string;
      response: 'CONFIRM' | 'NOT_ME';
      otp: string;
    }) => cardholderApi.respondToStepUp(stepUpId, { response, otp }),
    onSuccess: (result, variables) => {
      queryClient.invalidateQueries({ queryKey: ['me', 'step-ups'] });
      queryClient.invalidateQueries({ queryKey: ['me', 'transactions'] });
      queryClient.invalidateQueries({ queryKey: ['me', 'cards'] });

      if (variables.response === 'CONFIRM') {
        enqueueSnackbar(`Transaction verified successfully: ${result.display_reason}`, {
          variant: 'success',
        });
      } else {
        enqueueSnackbar(
          `Transaction denied: ${result.display_reason}. Your card has been permanently blocked.`,
          { variant: 'warning' }
        );
      }
      setNotMeTarget(null);
    },
    onError: (err: any) => {
      enqueueSnackbar(err.message || 'Verification response failed. Please check OTP and try again.', {
        variant: 'error',
      });
    },
  });

  const handleOtpChange = (id: string, val: string) => {
    // Only accept up to 6 digits
    const cleaned = val.replace(/\D/g, '').slice(0, 6);
    setOtpMap((prev) => ({ ...prev, [id]: cleaned }));
  };

  const handleConfirm = (item: PendingStepUp) => {
    const otp = otpMap[item.step_up_id] || '';
    if (otp.length < 6) {
      enqueueSnackbar('Please enter a valid 6-digit OTP code before confirming.', {
        variant: 'warning',
      });
      return;
    }
    responseMutation.mutate({
      stepUpId: item.step_up_id,
      response: 'CONFIRM',
      otp,
    });
  };

  const handleNotMe = (item: PendingStepUp) => {
    const otp = otpMap[item.step_up_id] || '';
    if (otp.length < 6) {
      enqueueSnackbar('Please enter the 6-digit OTP received for identity validation.', {
        variant: 'warning',
      });
      return;
    }
    setNotMeTarget(item);
  };

  const confirmNotMeExecution = () => {
    if (!notMeTarget) return;
    const otp = otpMap[notMeTarget.step_up_id] || '';
    responseMutation.mutate({
      stepUpId: notMeTarget.step_up_id,
      response: 'NOT_ME',
      otp,
    });
  };

  if (isLoading) {
    return null;
  }

  if (stepUps.length === 0) {
    return null;
  }

  return (
    <Box sx={{ mb: 4 }}>
      <Stack spacing={2}>
        {stepUps.map((item) => {
          const otp = otpMap[item.step_up_id] || '';
          return (
            <Card
              key={item.step_up_id}
              sx={{
                border: '2px solid',
                borderColor: 'warning.main',
                bgcolor: 'background.paper',
                borderRadius: 3,
                boxShadow: '0 4px 14px 0 rgba(232, 160, 0, 0.2)',
              }}
            >
              <CardContent sx={{ p: 3 }}>
                <Box
                  sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 1.5,
                    mb: 2,
                  }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <VpnKeyOutlinedIcon sx={{ color: 'warning.main', fontSize: 32 }} />
                    <Box>
                      <Typography variant="h6" fontWeight={700}>
                        Step-Up Verification Required
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        A transaction requires your authentication to proceed
                      </Typography>
                    </Box>
                  </Box>

                  <CountdownChip expiresAt={item.expires_at} />
                </Box>

                <Divider sx={{ my: 1.5 }} />

                <Box
                  sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 2,
                    my: 2,
                  }}
                >
                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Merchant
                    </Typography>
                    <Typography variant="subtitle1" fontWeight={700}>
                      {item.merchant_name}
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Amount
                    </Typography>
                    <MoneyText
                      value={item.amount}
                      variant="h5"
                      fontWeight={800}
                      color="primary.main"
                    />
                  </Box>

                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Card
                    </Typography>
                    <Typography variant="body2" fontWeight={600} fontFamily="monospace">
                      Ending with {item.card_id}
                    </Typography>
                  </Box>
                </Box>

                <Alert severity="info" sx={{ mb: 2.5 }}>
                  A simulated 6-digit one-time passcode has been generated for your session (e.g. enter{' '}
                  <strong>123456</strong> for testing).
                </Alert>

                <Box
                  sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    alignItems: 'center',
                    gap: 2,
                  }}
                >
                  <TextField
                    size="small"
                    label="Enter 6-digit OTP"
                    placeholder="123456"
                    value={otp}
                    onChange={(e) => handleOtpChange(item.step_up_id, e.target.value)}
                    inputProps={{ maxLength: 6, style: { letterSpacing: 4, fontWeight: 700 } }}
                    sx={{ width: 180 }}
                  />

                  <Button
                    variant="contained"
                    color="success"
                    startIcon={
                      responseMutation.isPending ? (
                        <CircularProgress size={16} color="inherit" />
                      ) : (
                        <CheckCircleOutlineIcon />
                      )
                    }
                    onClick={() => handleConfirm(item)}
                    disabled={responseMutation.isPending || otp.length < 6}
                    sx={{ fontWeight: 600 }}
                  >
                    Yes, it was me (CONFIRM)
                  </Button>

                  <Button
                    variant="outlined"
                    color="error"
                    startIcon={<BlockOutlinedIcon />}
                    onClick={() => handleNotMe(item)}
                    disabled={responseMutation.isPending}
                    sx={{ fontWeight: 600 }}
                  >
                    Not me (NOT_ME)
                  </Button>
                </Box>
              </CardContent>
            </Card>
          );
        })}
      </Stack>

      {/* Warning confirmation for NOT_ME */}
      <ConfirmDialog
        open={Boolean(notMeTarget)}
        title="Report Unauthorized Transaction?"
        message="Reporting this transaction as 'Not Me' indicates suspected fraud or stolen credentials."
        warning="This will immediately DECLINE the transaction and permanently BLOCK your card for your protection."
        confirmText="Yes, Report & Block Card"
        danger
        loading={responseMutation.isPending}
        onConfirm={confirmNotMeExecution}
        onCancel={() => setNotMeTarget(null)}
      />
    </Box>
  );
};
