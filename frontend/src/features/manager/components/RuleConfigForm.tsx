import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import VisibilityIcon from '@mui/icons-material/Visibility';
import SaveIcon from '@mui/icons-material/Save';
import { RuleConfiguration } from '../../../types/manager';

export interface RuleConfigFormProps {
  initialConfig: RuleConfiguration;
  onSave: (settings: { key: string; value: unknown }[], comment: string) => void;
  loading?: boolean;
}

export const RuleConfigForm: React.FC<RuleConfigFormProps> = ({
  initialConfig,
  onSave,
  loading = false,
}) => {
  // Extract setting values with defaults
  const getInitial = (key: string, def: any) => {
    const s = initialConfig.settings.find((item) => item.key === key);
    return s !== undefined ? s.value : def;
  };

  // State variables for all form groups
  const [velocity10mBurst, setVelocity10mBurst] = useState<number>(() =>
    Number(getInitial('velocity_10m_burst_threshold', 5))
  );
  const [velocity10mHigh, setVelocity10mHigh] = useState<number>(() =>
    Number(getInitial('velocity_10m_high_threshold', 3))
  );
  const [velocity10mWindow, setVelocity10mWindow] = useState<number>(() =>
    Number(getInitial('velocity_10m_window_minutes', 10))
  );

  const [amountSpikeMult, setAmountSpikeMult] = useState<number>(() =>
    Number(getInitial('amount_spike_multiplier', 5))
  );
  const [amountSpikeMin, setAmountSpikeMin] = useState<number>(() =>
    Number(getInitial('amount_spike_min_amount', 10000))
  );
  const [amountNoHistoryMin, setAmountNoHistoryMin] = useState<number>(() =>
    Number(getInitial('amount_no_history_min_amount', 50000))
  );

  const [speedKmph, setSpeedKmph] = useState<number>(() =>
    Number(getInitial('impossible_travel_speed_kmph', 800))
  );
  const [speedLookback, setSpeedLookback] = useState<number>(() =>
    Number(getInitial('impossible_travel_window_hours', 6))
  );

  const [oddHourStart, setOddHourStart] = useState<number>(() =>
    Number(getInitial('odd_hours_start_hour', 0))
  );
  const [oddHourEnd, setOddHourEnd] = useState<number>(() =>
    Number(getInitial('odd_hours_end_hour', 5))
  );
  const [oddHourMin, setOddHourMin] = useState<number>(() =>
    Number(getInitial('odd_hours_min_amount', 20000))
  );

  const [newMerchantDays, setNewMerchantDays] = useState<number>(() =>
    Number(getInitial('new_merchant_lookback_days', 90))
  );
  const [newMerchantMin, setNewMerchantMin] = useState<number>(() =>
    Number(getInitial('new_merchant_min_amount', 25000))
  );

  const [mccList, setMccList] = useState<string[]>(() => {
    const raw = getInitial('high_risk_mcc_list', ['4829', '5967', '6051', '7995']);
    return Array.isArray(raw) ? raw : ['4829', '5967', '6051', '7995'];
  });
  const [newMccInput, setNewMccInput] = useState('');

  const [multipleRedFlagsMin, setMultipleRedFlagsMin] = useState<number>(() =>
    Number(getInitial('multiple_red_flags_min_triggers', 3))
  );
  const [stepUpExpiryMin, setStepUpExpiryMin] = useState<number>(() =>
    Number(getInitial('step_up_expiry_minutes', 15))
  );
  const [makerCheckerThreshold, setMakerCheckerThreshold] = useState<number>(() =>
    Number(getInitial('maker_checker_threshold_amount', 100000))
  );

  const [aiStepUpThreshold, setAiStepUpThreshold] = useState<number>(() =>
    Number(getInitial('ai_step_up_threshold', 0.7))
  );
  const [aiBlockThreshold, setAiBlockThreshold] = useState<number>(() =>
    Number(getInitial('ai_block_threshold', 0.9))
  );

  const [changeComment, setChangeComment] = useState('');
  const [commentTouched, setCommentTouched] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  // Validation
  const isAiThresholdsValid = aiStepUpThreshold < aiBlockThreshold;
  const isCommentValid = changeComment.trim().length > 0;

  const handleAddMcc = () => {
    const trimmed = newMccInput.trim();
    if (trimmed && /^\d{4}$/.test(trimmed) && !mccList.includes(trimmed)) {
      setMccList([...mccList, trimmed]);
      setNewMccInput('');
    }
  };

  const handleRemoveMcc = (code: string) => {
    setMccList(mccList.filter((c) => c !== code));
  };

  const buildCurrentSettings = () => [
    { key: 'velocity_10m_burst_threshold', value: velocity10mBurst },
    { key: 'velocity_10m_high_threshold', value: velocity10mHigh },
    { key: 'velocity_10m_window_minutes', value: velocity10mWindow },
    { key: 'amount_spike_multiplier', value: amountSpikeMult },
    { key: 'amount_spike_min_amount', value: amountSpikeMin },
    { key: 'amount_no_history_min_amount', value: amountNoHistoryMin },
    { key: 'impossible_travel_speed_kmph', value: speedKmph },
    { key: 'impossible_travel_window_hours', value: speedLookback },
    { key: 'odd_hours_start_hour', value: oddHourStart },
    { key: 'odd_hours_end_hour', value: oddHourEnd },
    { key: 'odd_hours_min_amount', value: oddHourMin },
    { key: 'new_merchant_lookback_days', value: newMerchantDays },
    { key: 'new_merchant_min_amount', value: newMerchantMin },
    { key: 'high_risk_mcc_list', value: mccList },
    { key: 'multiple_red_flags_min_triggers', value: multipleRedFlagsMin },
    { key: 'step_up_expiry_minutes', value: stepUpExpiryMin },
    { key: 'maker_checker_threshold_amount', value: makerCheckerThreshold },
    { key: 'ai_step_up_threshold', value: aiStepUpThreshold },
    { key: 'ai_block_threshold', value: aiBlockThreshold },
  ];

  const handleFinalSubmit = () => {
    setCommentTouched(true);
    if (!isCommentValid || !isAiThresholdsValid) return;
    onSave(buildCurrentSettings(), changeComment);
    setPreviewOpen(false);
  };

  return (
    <Box>
      {!isAiThresholdsValid && (
        <Alert severity="error" sx={{ mb: 3 }}>
          AI Step-Up threshold ({aiStepUpThreshold}) must be strictly less than AI Block threshold (
          {aiBlockThreshold}).
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* Velocity Rules Group */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="Velocity Limits (R5 / R7)" subheader="Burst and high transaction frequency" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Burst Block (R5)"
                    type="number"
                    fullWidth
                    value={velocity10mBurst}
                    onChange={(e) => setVelocity10mBurst(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="High Step-up (R7)"
                    type="number"
                    fullWidth
                    value={velocity10mHigh}
                    onChange={(e) => setVelocity10mHigh(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Window (Mins)"
                    type="number"
                    fullWidth
                    value={velocity10mWindow}
                    onChange={(e) => setVelocity10mWindow(Number(e.target.value))}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Amount Spike Group */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="Amount Spike Thresholds (R8)" subheader="Multipliers and floor amounts" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Multiplier (x)"
                    type="number"
                    fullWidth
                    value={amountSpikeMult}
                    onChange={(e) => setAmountSpikeMult(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Min Floor (₹)"
                    type="number"
                    fullWidth
                    value={amountSpikeMin}
                    onChange={(e) => setAmountSpikeMin(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="No-History Min (₹)"
                    type="number"
                    fullWidth
                    value={amountNoHistoryMin}
                    onChange={(e) => setAmountNoHistoryMin(Number(e.target.value))}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Impossible Travel Group */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="Impossible Travel (R6)" subheader="Physical city distance and velocity" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Max Speed (km/h)"
                    type="number"
                    fullWidth
                    value={speedKmph}
                    onChange={(e) => setSpeedKmph(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Lookback Window (Hours)"
                    type="number"
                    fullWidth
                    value={speedLookback}
                    onChange={(e) => setSpeedLookback(Number(e.target.value))}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Odd Hours Group */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="Odd Hours High Value (R11)" subheader="Late night window in IST" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Start Hour (IST)"
                    type="number"
                    fullWidth
                    value={oddHourStart}
                    onChange={(e) => setOddHourStart(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="End Hour (IST)"
                    type="number"
                    fullWidth
                    value={oddHourEnd}
                    onChange={(e) => setOddHourEnd(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Min Amount (₹)"
                    type="number"
                    fullWidth
                    value={oddHourMin}
                    onChange={(e) => setOddHourMin(Number(e.target.value))}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* New Merchant & Red Flags */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="New Merchant & Multi-Flags (R12 / R13)" subheader="ECOM familiarity & soft rule count" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Lookback (Days)"
                    type="number"
                    fullWidth
                    value={newMerchantDays}
                    onChange={(e) => setNewMerchantDays(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Min Amount (₹)"
                    type="number"
                    fullWidth
                    value={newMerchantMin}
                    onChange={(e) => setNewMerchantMin(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    label="Min Flags (R13)"
                    type="number"
                    fullWidth
                    value={multipleRedFlagsMin}
                    onChange={(e) => setMultipleRedFlagsMin(Number(e.target.value))}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Governance & Timers */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="Governance & Expiry" subheader="Step-up expiry and maker-checker limit" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Step-Up Expiry (Mins)"
                    type="number"
                    fullWidth
                    value={stepUpExpiryMin}
                    onChange={(e) => setStepUpExpiryMin(Number(e.target.value))}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Maker-Checker Limit (₹)"
                    type="number"
                    fullWidth
                    value={makerCheckerThreshold}
                    onChange={(e) => setMakerCheckerThreshold(Number(e.target.value))}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* AI Probability Thresholds */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="AI Decision Thresholds (AI-2)" subheader="Tightening rules-based decisions" />
            <Divider />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="AI Step-Up Threshold"
                    type="number"
                    inputProps={{ step: 0.05, min: 0, max: 1 }}
                    fullWidth
                    value={aiStepUpThreshold}
                    onChange={(e) => setAiStepUpThreshold(Number(e.target.value))}
                    helperText="APPROVE tightened to STEP_UP (default 0.70)"
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="AI Block Threshold"
                    type="number"
                    inputProps={{ step: 0.05, min: 0, max: 1 }}
                    fullWidth
                    value={aiBlockThreshold}
                    onChange={(e) => setAiBlockThreshold(Number(e.target.value))}
                    helperText="STEP_UP escalated to BLOCK (default 0.90)"
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* High-Risk MCC Chip List */}
        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <CardHeader title="High-Risk MCC List (R10)" subheader="Merchant category codes subject to step-up" />
            <Divider />
            <CardContent>
              <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
                <TextField
                  size="small"
                  label="Add 4-Digit MCC"
                  placeholder="e.g. 7995"
                  value={newMccInput}
                  onChange={(e) => setNewMccInput(e.target.value)}
                  inputProps={{ maxLength: 4 }}
                />
                <IconButton color="primary" onClick={handleAddMcc}>
                  <AddCircleOutlineIcon />
                </IconButton>
              </Box>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {mccList.map((mcc) => (
                  <Chip
                    key={mcc}
                    label={mcc}
                    onDelete={() => handleRemoveMcc(mcc)}
                    color="primary"
                    variant="outlined"
                  />
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Mandatory Change Comment & Action Bar */}
      <Paper
        elevation={0}
        sx={{
          mt: 4,
          p: 3,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.paper',
        }}
      >
        <Typography variant="subtitle1" fontWeight={700} gutterBottom>
          Audit Change Justification (Mandatory)
        </Typography>
        <TextField
          fullWidth
          multiline
          rows={2}
          placeholder="Document reason for threshold update (e.g. quarterly risk review)..."
          value={changeComment}
          onChange={(e) => {
            setChangeComment(e.target.value);
            setCommentTouched(true);
          }}
          error={commentTouched && !isCommentValid}
          helperText={commentTouched && !isCommentValid ? 'Audit comment cannot be blank' : ''}
          sx={{ mb: 2 }}
        />

        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
          <Button
            variant="outlined"
            startIcon={<VisibilityIcon />}
            onClick={() => setPreviewOpen(true)}
          >
            Preview Changes
          </Button>
          <Button
            variant="contained"
            color="primary"
            startIcon={<SaveIcon />}
            disabled={!isCommentValid || !isAiThresholdsValid || loading}
            onClick={handleFinalSubmit}
          >
            {loading ? 'Saving Changes...' : 'Save Configuration'}
          </Button>
        </Box>
      </Paper>

      {/* Preview Dialog */}
      <Dialog open={previewOpen} onClose={() => setPreviewOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle fontWeight={700}>Preview Configuration Changes</DialogTitle>
        <DialogContent dividers>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Configuration Key</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>New Value</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {buildCurrentSettings().map((s) => (
                  <TableRow key={s.key}>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{s.key}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>
                      {Array.isArray(s.value) ? s.value.join(', ') : String(s.value)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setPreviewOpen(false)}>Back to Editing</Button>
          <Button
            variant="contained"
            color="primary"
            disabled={!isCommentValid || !isAiThresholdsValid || loading}
            onClick={handleFinalSubmit}
          >
            Confirm & Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
