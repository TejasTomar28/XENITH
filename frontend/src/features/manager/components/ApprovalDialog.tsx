import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
  TextField,
  Typography,
} from '@mui/material';
import { FalsePositiveProposalItem } from '../../../types/manager';
import { MoneyText } from '../../../shared/components/MoneyText';
import { IstDateTime } from '../../../shared/components/IstDateTime';
import { MaskedCard } from '../../../shared/components/MaskedCard';
import { RuleChips } from '../../analyst/components/RuleChips';

export interface ApprovalDialogProps {
  open: boolean;
  item: FalsePositiveProposalItem | null;
  onClose: () => void;
  onSubmit: (decision: 'APPROVE' | 'REJECT', comment: string) => void;
  loading?: boolean;
}

export const ApprovalDialog: React.FC<ApprovalDialogProps> = ({
  open,
  item,
  onClose,
  onSubmit,
  loading = false,
}) => {
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [comment, setComment] = useState('');
  const [touched, setTouched] = useState(false);

  if (!item) return null;

  const isCommentValid = comment.trim().length > 0;

  const handleConfirm = () => {
    setTouched(true);
    if (!isCommentValid) return;
    onSubmit(decision, comment);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>
        Maker-Checker Review: Proposal {item.proposal.proposal_id}
      </DialogTitle>
      <DialogContent dividers>
        {/* Evidence Summary */}
        <Box sx={{ mb: 2.5 }}>
          <Typography variant="subtitle2" fontWeight={700} gutterBottom>
            Proposed Transaction Overview:
          </Typography>
          <Box
            sx={{
              p: 2,
              bgcolor: 'action.hover',
              borderRadius: 2,
              display: 'flex',
              flexDirection: 'column',
              gap: 1,
            }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="caption" color="text.secondary">
                Amount
              </Typography>
              <MoneyText value={item.alert.amount} fontWeight={700} color="primary.main" />
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="caption" color="text.secondary">
                Card
              </Typography>
              <MaskedCard value={item.alert.masked_card_number} />
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="caption" color="text.secondary">
                Proposed By (Maker)
              </Typography>
              <Typography variant="body2" fontWeight={600}>
                {item.proposal.proposed_by}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="caption" color="text.secondary">
                Proposed At
              </Typography>
              <IstDateTime value={item.proposal.proposed_at} />
            </Box>
          </Box>
        </Box>

        {/* Maker's Comment */}
        <Box sx={{ mb: 2.5 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Analyst Justification Comment:
          </Typography>
          <Typography
            variant="body2"
            sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1.5, mt: 0.5 }}
          >
            {item.proposal.comment}
          </Typography>
        </Box>

        <Box sx={{ mb: 2.5 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.5 }}>
            Triggered Rules:
          </Typography>
          <RuleChips codes={item.alert.reason_codes || []} />
        </Box>

        <Divider sx={{ my: 2 }} />

        {/* Manager Decision */}
        <FormControl component="fieldset" sx={{ mb: 2 }}>
          <FormLabel component="legend" sx={{ fontWeight: 600, mb: 1 }}>
            Fraud Risk Manager Determination:
          </FormLabel>
          <RadioGroup
            row
            value={decision}
            onChange={(e) => setDecision(e.target.value as 'APPROVE' | 'REJECT')}
          >
            <FormControlLabel
              value="APPROVE"
              control={<Radio color="success" />}
              label="Approve Proposal (Finalize FALSE_POSITIVE)"
            />
            <FormControlLabel
              value="REJECT"
              control={<Radio color="error" />}
              label="Reject Proposal (Escalate to CONFIRMED_FRAUD)"
            />
          </RadioGroup>
        </FormControl>

        {decision === 'REJECT' && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Rejecting this proposal will confirm fraud, permanently blocking the cardholder account.
          </Alert>
        )}

        <Box>
          <Typography variant="body2" fontWeight={600} gutterBottom>
            Manager Verification Comment (Mandatory):
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={3}
            placeholder="Record independent check observations..."
            value={comment}
            onChange={(e) => {
              setComment(e.target.value);
              setTouched(true);
            }}
            error={touched && !isCommentValid}
            helperText={touched && !isCommentValid ? 'Manager comment cannot be blank' : ''}
          />
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={loading} color="inherit">
          Cancel
        </Button>
        <Button
          variant="contained"
          color={decision === 'APPROVE' ? 'success' : 'error'}
          disabled={!isCommentValid || loading}
          onClick={handleConfirm}
        >
          {loading ? 'Submitting...' : decision === 'APPROVE' ? 'Approve & Release' : 'Reject & Block'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
