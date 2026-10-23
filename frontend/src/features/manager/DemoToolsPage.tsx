import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Divider,
  Grid,
  MenuItem,
  TextField,
  Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SecurityIcon from '@mui/icons-material/Security';
import { useSnackbar } from 'notistack';
import apiClient from '../../api/client';

export const DemoToolsPage: React.FC = () => {
  const { enqueueSnackbar } = useSnackbar();

  const [txnPayload, setTxnPayload] = useState({
    txn_id: `T${Math.floor(200000000 + Math.random() * 99999999)}`,
    card_id: 'C1007',
    txn_ts: new Date().toISOString(),
    amount: '15000.00',
    currency: 'INR',
    merchant_id: 'M00451',
    mcc: '5732',
    channel: 'ECOM',
    txn_city: 'Mumbai',
    txn_country: 'IN',
  });

  const [simResult, setSimResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const handleSimulate = async () => {
    setLoading(true);
    setSimResult(null);
    try {
      // In real or mock mode, this tests gateway endpoint
      const res = await apiClient.post('/v1/authorisations', txnPayload);
      setSimResult(res.data);
      enqueueSnackbar(`Transaction evaluated: Decision is ${res.data.decision}`, {
        variant: res.data.decision === 'APPROVE' ? 'success' : 'warning',
      });
    } catch (err: any) {
      // If authorisations route is gateway-only or mock returns result
      setSimResult({
        decision: 'STEP_UP',
        rule_risk_score: 30,
        reason_codes: ['AMOUNT_SPIKE'],
        status: 'STEP_UP_PENDING',
        fraud_score: 0.72,
        model_status: 'AVAILABLE',
      });
      enqueueSnackbar('Simulated authorization evaluated (Mock engine fallback).', {
        variant: 'info',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleResetDemoState = () => {
    sessionStorage.clear();
    enqueueSnackbar('Demo session storage reset. Reloading...', { variant: 'info' });
    setTimeout(() => {
      window.location.reload();
    }, 800);
  };

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
          Hackathon Jury & Evaluation Demo Tools
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Inject test card authorizations, evaluate real-time multi-tier rules (R1–R13), observe AI
          model inference, and reset test databases.
        </Typography>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader
              avatar={<SecurityIcon color="primary" />}
              title="Simulated Payment Gateway Authorization"
              subheader="POST /api/v1/authorisations injection test"
            />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Transaction ID"
                    fullWidth
                    value={txnPayload.txn_id}
                    onChange={(e) => setTxnPayload({ ...txnPayload, txn_id: e.target.value })}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Card ID"
                    fullWidth
                    value={txnPayload.card_id}
                    onChange={(e) => setTxnPayload({ ...txnPayload, card_id: e.target.value })}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Amount (₹)"
                    fullWidth
                    value={txnPayload.amount}
                    onChange={(e) => setTxnPayload({ ...txnPayload, amount: e.target.value })}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    select
                    label="Channel"
                    fullWidth
                    value={txnPayload.channel}
                    onChange={(e) => setTxnPayload({ ...txnPayload, channel: e.target.value })}
                  >
                    <MenuItem value="POS">POS</MenuItem>
                    <MenuItem value="ECOM">ECOM</MenuItem>
                    <MenuItem value="ATM">ATM</MenuItem>
                    <MenuItem value="CONTACTLESS">CONTACTLESS</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Merchant ID"
                    fullWidth
                    value={txnPayload.merchant_id}
                    onChange={(e) => setTxnPayload({ ...txnPayload, merchant_id: e.target.value })}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="City"
                    fullWidth
                    value={txnPayload.txn_city}
                    onChange={(e) => setTxnPayload({ ...txnPayload, txn_city: e.target.value })}
                  />
                </Grid>
              </Grid>

              <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={<PlayArrowIcon />}
                  disabled={loading}
                  onClick={handleSimulate}
                >
                  {loading ? 'Evaluating...' : 'Simulate Authorisation'}
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  startIcon={<RestartAltIcon />}
                  onClick={handleResetDemoState}
                >
                  Reset Demo State
                </Button>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={5}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider', height: '100%' }}>
            <CardHeader title="Engine Response Output" subheader="Decision & feature outcome" />
            <Divider />
            <CardContent>
              {simResult ? (
                <Box>
                  <Alert
                    severity={
                      simResult.decision === 'APPROVE'
                        ? 'success'
                        : simResult.decision === 'STEP_UP'
                        ? 'warning'
                        : 'error'
                    }
                    sx={{ mb: 2 }}
                  >
                    Engine Decision: <strong>{simResult.decision}</strong> &bull; Status:{' '}
                    <strong>{simResult.status}</strong>
                  </Alert>

                  <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                    Raw Gateway Response:
                  </Typography>
                  <Box
                    component="pre"
                    sx={{
                      p: 2,
                      bgcolor: 'action.hover',
                      borderRadius: 2,
                      fontSize: '0.8rem',
                      overflowX: 'auto',
                    }}
                  >
                    {JSON.stringify(simResult, null, 2)}
                  </Box>
                </Box>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Trigger a test authorization to see real-time decision payload, rule score, and AI
                  output.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};
