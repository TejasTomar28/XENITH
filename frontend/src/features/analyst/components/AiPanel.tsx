import React from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  LinearProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import InsightsIcon from '@mui/icons-material/Insights';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { AlertEvidence } from '../../../types/analyst';

export interface AiPanelProps {
  evidence: AlertEvidence;
}

export const AiPanel: React.FC<AiPanelProps> = ({ evidence }) => {
  const isAvailable = evidence.model_status === 'AVAILABLE' && evidence.fraud_score !== null;

  const scorePct = isAvailable ? Math.round((evidence.fraud_score || 0) * 100) : 0;

  let scoreColor: 'success' | 'warning' | 'error' = 'success';
  if (scorePct >= 70) {
    scoreColor = 'error';
  } else if (scorePct >= 40) {
    scoreColor = 'warning';
  }

  return (
    <Card sx={{ height: '100%', borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
      <CardHeader
        avatar={<AutoAwesomeIcon sx={{ color: '#E8A000' }} />}
        title={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="h6" fontWeight={700}>
              AI-Generated Assessment
            </Typography>
            {evidence.model_version && (
              <Chip
                label={`v${evidence.model_version}`}
                size="small"
                variant="outlined"
                sx={{ fontSize: '0.65rem', height: 20 }}
              />
            )}
          </Box>
        }
        subheader="Real-time XGBoost inference & SHAP explainability"
      />
      <Divider />
      <CardContent>
        {!isAvailable ? (
          <Alert severity="info" icon={<InfoOutlinedIcon />} sx={{ my: 2 }}>
            AI score unavailable for this transaction. Decisions evaluated using deterministic rules
            engine R1–R13 only.
          </Alert>
        ) : (
          <>
            {/* Primary Fraud Risk Score Meter */}
            <Box sx={{ mb: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="body2" color="text.secondary" fontWeight={500}>
                  Normalized Fraud Probability
                </Typography>
                <Typography variant="h5" fontWeight={800} color={`${scoreColor}.main`}>
                  {scorePct}%
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={scorePct}
                color={scoreColor}
                sx={{ height: 10, borderRadius: 5 }}
              />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
                <Typography variant="caption" color="text.secondary">
                  Low Risk (&lt;40%)
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Elevated (&ge;70%)
                </Typography>
              </Box>
            </Box>

            {/* Second Detector Score if present */}
            {evidence.second_detector_score !== null &&
              evidence.second_detector_score !== undefined && (
                <Box
                  sx={{
                    p: 1.5,
                    mb: 3,
                    bgcolor: 'action.hover',
                    borderRadius: 2,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <Typography variant="body2" fontWeight={600}>
                    Second Detector Score (Isolation Forest):
                  </Typography>
                  <Chip
                    label={`${Math.round(evidence.second_detector_score * 100)}%`}
                    size="small"
                    color={evidence.second_detector_score > 0.7 ? 'error' : 'default'}
                    sx={{ fontWeight: 700 }}
                  />
                </Box>
              )}

            {/* Top 3 Contributing Explanatory Factors */}
            <Box>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>
                Top Contributing Risk Factors (SHAP)
              </Typography>
              <List disablePadding>
                {evidence.top_factors && evidence.top_factors.length > 0 ? (
                  evidence.top_factors.map((factor, idx) => (
                    <ListItem
                      key={factor.feature || idx}
                      disableGutters
                      sx={{
                        py: 1,
                        borderBottom:
                          idx < evidence.top_factors.length - 1 ? '1px dashed #e2e8f0' : 'none',
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: 32 }}>
                        <InsightsIcon color="primary" fontSize="small" />
                      </ListItemIcon>
                      <ListItemText
                        primary={factor.explanation}
                        secondary={`Telemetry field: ${factor.feature}`}
                        primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
                        secondaryTypographyProps={{ variant: 'caption', fontFamily: 'monospace' }}
                      />
                    </ListItem>
                  ))
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    No risk factor contributions reported.
                  </Typography>
                )}
              </List>
            </Box>
          </>
        )}
      </CardContent>
    </Card>
  );
};
