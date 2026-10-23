import React from 'react';
import { Box, Chip, Tooltip, Typography } from '@mui/material';

export const REASON_CODE_DESCRIPTIONS: Record<string, string> = {
  CARD_NOT_ACTIVE: 'Card status is not ACTIVE or expiry date has passed (R1)',
  INTL_NOT_ENABLED: 'International transaction attempted while international flag is disabled (R2)',
  MERCHANT_BLACKLISTED: 'Merchant identifier is present on the blocked list (R3)',
  DAILY_LIMIT_EXCEEDED: 'Total approved spend today plus amount exceeds daily spending limit (R4)',
  VELOCITY_BURST: 'Extreme burst: 5 or more transactions in 10 minutes (R5)',
  IMPOSSIBLE_TRAVEL: 'Physical travel velocity between consecutive physical transactions exceeds 800 km/h (R6)',
  HIGH_VELOCITY: 'High velocity: 3 or 4 transactions in 10 minutes (R7)',
  AMOUNT_SPIKE: 'Transaction amount is >5× the card 30-day average (or ≥₹50,000 without history) (R8)',
  HIGH_RISK_GEO: 'Transaction in destination country flagged as high risk (R9)',
  HIGH_RISK_MCC: 'Merchant Category Code is on the high-risk monitoring list (R10)',
  ODD_HOUR_HIGH_VALUE: 'Transaction between 00:00–04:59 IST and amount ≥ ₹20,000 (R11)',
  NEW_MERCHANT_HIGH_VALUE: 'First transaction with merchant in 90 days, ECOM, amount ≥ ₹25,000 (R12)',
  MULTIPLE_RED_FLAGS: 'Multiple risk factors (at least 3 soft rules) triggered simultaneously (R13)',
  STEP_UP_DENIED: 'Cardholder explicitly reported transaction as NOT_ME (Step-up denied)',
  STEP_UP_TIMEOUT: 'Cardholder did not respond to step-up OTP challenge within 15 minutes',
  HIGH_FRAUD_SCORE_MODEL: 'AI model probability score escalated risk tier (rules tightened)',
};

export interface RuleChipsProps {
  codes?: string[];
}

export const RuleChips: React.FC<RuleChipsProps> = ({ codes = [] }) => {
  if (codes.length === 0) {
    return <Typography variant="caption" color="text.secondary">None</Typography>;
  }

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
      {codes.map((code) => {
        const desc = REASON_CODE_DESCRIPTIONS[code] || `Triggered rule code: ${code}`;
        const isHard = ['CARD_NOT_ACTIVE', 'INTL_NOT_ENABLED', 'MERCHANT_BLACKLISTED', 'DAILY_LIMIT_EXCEEDED', 'VELOCITY_BURST', 'IMPOSSIBLE_TRAVEL', 'MULTIPLE_RED_FLAGS'].includes(code);

        return (
          <Tooltip key={code} title={desc} arrow>
            <Chip
              label={code}
              size="small"
              sx={{
                bgcolor: isHard ? '#ffebee' : '#fff3e0',
                color: isHard ? '#b71c1c' : '#b45309',
                border: `1px solid ${isHard ? '#ef5350' : '#f59e0b'}50`,
                fontWeight: 700,
                fontSize: '0.72rem',
                fontFamily: 'monospace',
              }}
            />
          </Tooltip>
        );
      })}
    </Box>
  );
};
