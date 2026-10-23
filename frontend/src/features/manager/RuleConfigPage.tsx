import React from 'react';
import { Box, Card, CardContent, Divider, Typography } from '@mui/material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { managerApi } from '../../api/manager.api';
import { useSnackbar } from 'notistack';
import { RuleConfigForm } from './components/RuleConfigForm';
import { AuditTimeline } from './components/AuditTimeline';
import { LoadingSkeleton } from '../../shared/components/LoadingSkeleton';

export const RuleConfigPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  // Load Rule Configuration
  const { data: config, isLoading, refetch } = useQuery({
    queryKey: ['manager', 'rule-configuration'],
    queryFn: () => managerApi.getRuleConfiguration(),
  });

  // Load Configuration Audit Trail
  const auditQuery = useQuery({
    queryKey: ['manager', 'audit', 'CONFIGURATION'],
    queryFn: () => managerApi.getAuditTrails('CONFIGURATION', 'CONFIG-v1.0'),
  });

  const updateMutation = useMutation({
    mutationFn: ({
      settings,
      comment,
    }: {
      settings: Record<string, unknown>;
      comment: string;
    }) =>
      managerApi.updateRuleConfiguration({
        expected_version: config?.version || 'v1.0',
        settings,
        comment,
      }),
    onSuccess: (updated) => {
      enqueueSnackbar(
        `Rule configuration updated successfully (New Version: ${updated.version}).`,
        { variant: 'success' }
      );
      refetch();
      auditQuery.refetch();
      queryClient.invalidateQueries({ queryKey: ['manager', 'dashboard'] });
    },
    onError: (err: any) => {
      enqueueSnackbar(err.message || 'Failed to update rule configuration.', { variant: 'error' });
    },
  });

  if (isLoading || !config) {
    return (
      <Box sx={{ p: 2 }}>
        <LoadingSkeleton variant="card" height={500} />
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
          Rules Engine & AI Parameters Configuration
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Manage dynamic fraud rule thresholds (R1–R13), velocity parameters, odd-hour ceilings,
          high-risk MCC lists, and AI model probability boundaries.
        </Typography>
      </Box>

      {/* Main Configuration Form */}
      <Box sx={{ mb: 5 }}>
        <RuleConfigForm
          initialConfig={config}
          loading={updateMutation.isPending}
          onSave={(settings, comment) => updateMutation.mutate({ settings, comment })}
        />
      </Box>

      {/* Configuration Audit Trail History */}
      <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
        <CardContent>
          <Typography variant="h6" fontWeight={700} gutterBottom>
            Configuration Change Audit History
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Recent modifications made to risk thresholds and rule governance.
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <AuditTimeline events={auditQuery.data?.items || []} loading={auditQuery.isLoading} />
        </CardContent>
      </Card>
    </Box>
  );
};
