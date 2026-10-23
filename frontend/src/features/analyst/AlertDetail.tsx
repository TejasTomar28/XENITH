import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  FormLabel,
  Grid,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import HowToRegIcon from '@mui/icons-material/HowToReg';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';
import { Gauge } from '@mui/x-charts/Gauge';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { analystApi } from '../../api/analyst.api';
import { useAuth } from '../../shared/hooks/useAuth';
import { useSnackbar } from 'notistack';
import { MoneyText } from '../../shared/components/MoneyText';
import { IstDateTime } from '../../shared/components/IstDateTime';
import { MaskedCard } from '../../shared/components/MaskedCard';
import { StatusChip } from '../../shared/components/StatusChip';
import { PriorityChip } from '../../shared/components/PriorityChip';
import { CountdownChip } from '../../shared/components/CountdownChip';
import { LoadingSkeleton } from '../../shared/components/LoadingSkeleton';
import { FeatureTable } from './components/FeatureTable';
import { RuleChips } from './components/RuleChips';
import { AiPanel } from './components/AiPanel';
import { CardHistoryPanel } from './components/CardHistoryPanel';
import { parseMoney } from '../../shared/utils/money';

export const AlertDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();
  const { user } = useAuth();

  const [closeDialogOpen, setCloseDialogOpen] = useState(false);
  const [resolutionDecision, setResolutionDecision] = useState<'CONFIRMED_FRAUD' | 'FALSE_POSITIVE'>(
    'FALSE_POSITIVE'
  );
  const [resolutionComment, setResolutionComment] = useState('');
  const [commentTouched, setCommentTouched] = useState(false);

  // Fetch Alert Evidence
  const { data: evidence, isLoading, error, refetch } = useQuery({
    queryKey: ['analyst', 'alert', id],
    queryFn: () => analystApi.getAlert(id!),
    enabled: Boolean(id),
  });

  // Fetch Alert Card History
  const historyQuery = useQuery({
    queryKey: ['analyst', 'alert-history', id],
    queryFn: () => analystApi.getAlertHistory(id!),
    enabled: Boolean(id),
  });

  // Claim mutation
  const claimMutation = useMutation({
    mutationFn: () => analystApi.claimAlert(id!),
    onSuccess: (updatedAlert) => {
      enqueueSnackbar(`Alert ${updatedAlert.alert_id} successfully claimed.`, {
        variant: 'success',
      });
      refetch();
      queryClient.invalidateQueries({ queryKey: ['analyst', 'alerts'] });
    },
    onError: (err: any) => {
      enqueueSnackbar(err.message || 'Already claimed by another analyst.', {
        variant: 'error',
      });
      refetch();
    },
  });

  // Resolve mutation
  const resolveMutation = useMutation({
    mutationFn: () =>
      analystApi.resolveAlert(id!, {
        resolution: resolutionDecision,
        comment: resolutionComment,
      }),
    onSuccess: (res) => {
      if (res.proposal) {
        enqueueSnackbar(
          'Proposal submitted to Fraud Risk Manager for secondary maker-checker approval.',
          { variant: 'info' }
        );
      } else {
        enqueueSnackbar(`Alert resolved as ${res.alert.status}.`, {
          variant: 'success',
        });
      }
      setCloseDialogOpen(false);
      refetch();
      queryClient.invalidateQueries({ queryKey: ['analyst', 'alerts'] });
    },
    onError: (err: any) => {
      enqueueSnackbar(err.message || 'Failed to submit alert resolution.', {
        variant: 'error',
      });
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ p: 2 }}>
        <LoadingSkeleton variant="card" height={400} />
      </Box>
    );
  }

  if (error || !evidence) {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load alert details or alert does not exist.
        </Alert>
        <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/analyst/alerts')}>
          Back to Alert Queue
        </Button>
      </Box>
    );
  }

  const alert = evidence.alert;
  const txn = evidence.transaction;
  const isClaimedByMe = alert.claimed_by === (user?.username || 'analyst1');
  const isActionable = alert.status === 'IN_REVIEW' && isClaimedByMe;
  const canClaim = alert.status === 'OPEN';
  const amountNumber = parseMoney(alert.amount);
  const isHighValueFP = resolutionDecision === 'FALSE_POSITIVE' && amountNumber > 100000;
  const isCommentValid = resolutionComment.trim().length >= 10;

  return (
    <Box>
      {/* Top Header & Breadcrumb Navigation */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Button
            variant="outlined"
            size="small"
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/analyst/alerts')}
          >
            Alert Queue
          </Button>
          <Box>
            <Typography variant="h5" fontWeight={800}>
              Investigation: {alert.alert_id}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Transaction ID: {txn.txn_id} &bull; Claimed by:{' '}
              <strong>{alert.claimed_by || 'Unassigned'}</strong>
            </Typography>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <PriorityChip priority={alert.alert_priority} />
          <StatusChip status={alert.status} />
          <CountdownChip expiresAt={alert.sla_due_at} />
        </Box>
      </Box>

      {/* Main 2-Column Responsive Layout */}
      <Grid container spacing={3}>
        {/* LEFT COLUMN: Transaction, Features, Rules, Rule Score */}
        <Grid item xs={12} lg={7}>
          <Stack spacing={3}>
            {/* 1. Transaction Summary Card */}
            <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
              <CardHeader
                title={<Typography variant="h6" fontWeight={700}>Transaction Evidence</Typography>}
                subheader="Details captured at authorization time"
              />
              <Divider />
              <CardContent>
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="text.secondary">
                      Amount & Currency
                    </Typography>
                    <Box sx={{ mt: 0.5 }}>
                      <MoneyText value={txn.amount} variant="h5" fontWeight={800} color="primary.main" />
                    </Box>
                  </Grid>

                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="text.secondary">
                      Card Number
                    </Typography>
                    <Box sx={{ mt: 0.5 }}>
                      <MaskedCard value={txn.masked_card_number} />
                    </Box>
                  </Grid>

                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="text.secondary">
                      Merchant Name (Untrusted Free-text)
                    </Typography>
                    <Typography variant="body1" fontWeight={600} sx={{ mt: 0.5 }}>
                      {txn.merchant_name}
                    </Typography>
                  </Grid>

                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="text.secondary">
                      Channel & Location
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 0.5 }}>
                      {txn.channel} &bull; {txn.txn_city}, {txn.txn_country}
                    </Typography>
                  </Grid>

                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="text.secondary">
                      Timestamp (IST)
                    </Typography>
                    <Box sx={{ mt: 0.5 }}>
                      <IstDateTime value={txn.txn_ts} />
                    </Box>
                  </Grid>

                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="text.secondary">
                      Authorization Status
                    </Typography>
                    <Box sx={{ mt: 0.5 }}>
                      <StatusChip status={txn.status} />
                    </Box>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>

            {/* 2. Feature Table */}
            <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
              <CardHeader
                title={<Typography variant="h6" fontWeight={700}>Derived Risk Features</Typography>}
                subheader="Exact historical state computed at decision timestamp"
              />
              <Divider />
              <CardContent>
                <FeatureTable features={evidence.features_used} />
              </CardContent>
            </Card>

            {/* 3. Triggered Rules & Rule Risk Score Gauge */}
            <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
              <CardHeader
                title={<Typography variant="h6" fontWeight={700}>Rules Engine Evaluation (R1–R13)</Typography>}
                subheader={`Decision: ${evidence.rule_decision}`}
              />
              <Divider />
              <CardContent>
                <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, gap: 3, alignItems: 'center' }}>
                  <Box sx={{ width: 160, height: 160, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <Typography variant="caption" fontWeight={600} color="text.secondary" gutterBottom>
                      Rule Risk Score
                    </Typography>
                    <Gauge
                      value={evidence.rule_risk_score || 0}
                      height={130}
                      text={({ value }) => `${value}`}
                    />
                  </Box>

                  <Box sx={{ flex: 1 }}>
                    <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>
                      Triggered Reason Codes:
                    </Typography>
                    <RuleChips codes={evidence.reason_codes} />
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Stack>
        </Grid>

        {/* RIGHT COLUMN: AI Panel, AI Narrative, Card History */}
        <Grid item xs={12} lg={5}>
          <Stack spacing={3}>
            {/* 4. AI Inference Panel */}
            <AiPanel evidence={evidence} />

            {/* 5. AI Narrative Card (if present) */}
            {evidence.ai_narrative && (
              <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
                <CardHeader
                  title={<Typography variant="subtitle1" fontWeight={700}>AI Investigation Summary</Typography>}
                  subheader="Synthesized contextual briefing (Responsible AI)"
                />
                <Divider />
                <CardContent>
                  <Typography variant="body2" color="text.primary" sx={{ lineHeight: 1.6 }}>
                    {evidence.ai_narrative}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
                    Disclaimer: AI summaries are assistive guidance based on verifiable features and
                    must not substitute independent human verification.
                  </Typography>
                </CardContent>
              </Card>
            )}

            {/* 6. Card History Panel */}
            <CardHistoryPanel
              transactions={historyQuery.data?.items || []}
              loading={historyQuery.isLoading}
            />
          </Stack>
        </Grid>
      </Grid>

      {/* Action Bar Sticky / Bottom */}
      <Paper
        elevation={3}
        sx={{
          position: 'sticky',
          bottom: 16,
          mt: 4,
          p: 2,
          borderRadius: 3,
          bgcolor: 'background.paper',
          border: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          zIndex: 10,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <AssignmentLateIcon color="action" />
          <Typography variant="body2" fontWeight={600}>
            Investigation Action Bar
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 2 }}>
          {canClaim && (
            <Button
              variant="contained"
              color="primary"
              startIcon={
                claimMutation.isPending ? (
                  <CircularProgress size={16} color="inherit" />
                ) : (
                  <HowToRegIcon />
                )
              }
              onClick={() => claimMutation.mutate()}
              disabled={claimMutation.isPending}
            >
              Claim Alert
            </Button>
          )}

          {isActionable && (
            <Button
              variant="contained"
              color="secondary"
              startIcon={<CheckCircleOutlineIcon />}
              onClick={() => setCloseDialogOpen(true)}
            >
              Close Alert
            </Button>
          )}

          {!canClaim && !isActionable && (
            <Chip
              label={`Status: ${alert.status}`}
              color={alert.status === 'CONFIRMED_FRAUD' ? 'error' : 'default'}
              variant="outlined"
            />
          )}
        </Box>
      </Paper>

      {/* Close Alert Resolution Dialog */}
      <Dialog
        open={closeDialogOpen}
        onClose={() => setCloseDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle fontWeight={700}>Resolve & Close Fraud Alert</DialogTitle>
        <DialogContent dividers>
          <FormControl component="fieldset" sx={{ mb: 3 }}>
            <FormLabel component="legend" sx={{ fontWeight: 600, mb: 1 }}>
              Analyst Determination:
            </FormLabel>
            <RadioGroup
              row
              value={resolutionDecision}
              onChange={(e) =>
                setResolutionDecision(e.target.value as 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE')
              }
            >
              <FormControlLabel
                value="FALSE_POSITIVE"
                control={<Radio color="primary" />}
                label="False Positive (Genuine)"
              />
              <FormControlLabel
                value="CONFIRMED_FRAUD"
                control={<Radio color="error" />}
                label="Confirmed Fraud"
              />
            </RadioGroup>
          </FormControl>

          {isHighValueFP && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              High-Value Policy Notice: Transaction amount ({MoneyText({ value: alert.amount })}) is
              strictly greater than ₹100,000. This resolution will enter{' '}
              <strong>PENDING_MANAGER_APPROVAL</strong> for secondary Fraud Risk Manager review before
              final closure.
            </Alert>
          )}

          <Box sx={{ mt: 1 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="body2" fontWeight={600}>
                Investigation Comment (Mandatory):
              </Typography>
              <Typography
                variant="caption"
                color={resolutionComment.trim().length >= 10 ? 'success.main' : 'error.main'}
                fontWeight={600}
              >
                {resolutionComment.trim().length}/10 characters
              </Typography>
            </Box>
            <TextField
              fullWidth
              multiline
              rows={3}
              placeholder="Provide evidence summary (minimum 10 characters)..."
              value={resolutionComment}
              onChange={(e) => {
                setResolutionComment(e.target.value);
                setCommentTouched(true);
              }}
              error={commentTouched && !isCommentValid}
              helperText={
                commentTouched && !isCommentValid
                  ? 'Resolution comment must be at least 10 characters long'
                  : ''
              }
              inputProps={{ 'data-testid': 'resolution-comment-input' }}
            />
          </Box>
        </DialogContent>

        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setCloseDialogOpen(false)} color="inherit">
            Cancel
          </Button>
          <Button
            variant="contained"
            color="primary"
            disabled={!isCommentValid || resolveMutation.isPending}
            onClick={() => resolveMutation.mutate()}
            data-testid="submit-resolution-button"
          >
            {resolveMutation.isPending ? 'Submitting...' : 'Submit Resolution'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
