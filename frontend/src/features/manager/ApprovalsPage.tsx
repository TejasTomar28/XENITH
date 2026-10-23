import React, { useState } from 'react';
import {
  Box,
  Button,
  Paper,
  Tooltip,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { managerApi } from '../../api/manager.api';
import { FalsePositiveProposalItem } from '../../types/manager';
import { useAuth } from '../../shared/hooks/useAuth';
import { useSnackbar } from 'notistack';
import { MoneyText } from '../../shared/components/MoneyText';
import { IstDateTime } from '../../shared/components/IstDateTime';
import { StatusChip } from '../../shared/components/StatusChip';
import { ApprovalDialog } from './components/ApprovalDialog';

export const ApprovalsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();
  const { user } = useAuth();

  const [activeItem, setActiveItem] = useState<FalsePositiveProposalItem | null>(null);

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['manager', 'proposals'],
    queryFn: () => managerApi.getFalsePositiveProposals({ status: 'PENDING' }),
  });

  const proposals = data?.items || [];

  const decisionMutation = useMutation({
    mutationFn: ({
      proposalId,
      decision,
      comment,
    }: {
      proposalId: string;
      decision: 'APPROVE' | 'REJECT';
      comment: string;
    }) =>
      managerApi.decideFalsePositiveProposal(proposalId, {
        decision,
        comment,
      }),
    onSuccess: (res, vars) => {
      enqueueSnackbar(
        `Proposal ${vars.decision === 'APPROVE' ? 'approved' : 'rejected'}. Transaction alert finalized as ${res.alert.status}.`,
        { variant: vars.decision === 'APPROVE' ? 'success' : 'warning' }
      );
      setActiveItem(null);
      refetch();
      queryClient.invalidateQueries({ queryKey: ['manager', 'dashboard'] });
    },
    onError: (err: any) => {
      enqueueSnackbar(err.message || 'Action failed: Maker-checker verification error.', {
        variant: 'error',
      });
    },
  });

  const handleOpenAction = (item: FalsePositiveProposalItem) => {
    setActiveItem(item);
  };

  const columns: GridColDef<FalsePositiveProposalItem>[] = [
    {
      field: 'proposal_id',
      headerName: 'Proposal ID',
      flex: 1,
      minWidth: 120,
      valueGetter: (_, row) => row.proposal.proposal_id,
      renderCell: (params) => (
        <Typography variant="body2" fontFamily="monospace" fontWeight={600}>
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'alert_id',
      headerName: 'Alert ID',
      flex: 1,
      minWidth: 120,
      valueGetter: (_, row) => row.proposal.alert_id,
      renderCell: (params) => (
        <Typography variant="body2" fontFamily="monospace">
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'amount',
      headerName: 'Amount (₹)',
      flex: 1,
      minWidth: 130,
      valueGetter: (_, row) => row.alert.amount,
      renderCell: (params) => <MoneyText value={params.value} fontWeight={600} />,
    },
    {
      field: 'proposed_by',
      headerName: 'Analyst (Maker)',
      flex: 1.1,
      minWidth: 130,
      valueGetter: (_, row) => row.proposal.proposed_by,
      renderCell: (params) => (
        <Typography variant="body2" fontWeight={600}>
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'comment',
      headerName: 'Analyst Justification',
      flex: 1.8,
      minWidth: 200,
      valueGetter: (_, row) => row.proposal.comment,
      renderCell: (params) => (
        <Typography variant="body2" color="text.secondary" noWrap>
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'proposed_at',
      headerName: 'Proposed At (IST)',
      flex: 1.3,
      minWidth: 170,
      valueGetter: (_, row) => row.proposal.proposed_at,
      renderCell: (params) => <IstDateTime value={params.value} />,
    },
    {
      field: 'status',
      headerName: 'Status',
      flex: 1,
      minWidth: 120,
      valueGetter: (_, row) => row.proposal.status,
      renderCell: (params) => <StatusChip status={params.value} />,
    },
    {
      field: 'actions',
      headerName: 'Actions',
      flex: 1.2,
      minWidth: 150,
      sortable: false,
      renderCell: (params: GridRenderCellParams<FalsePositiveProposalItem>) => {
        const item = params.row;
        const isSelfProposal = item.proposal.proposed_by === user?.username;

        if (isSelfProposal) {
          return (
            <Tooltip title="Maker-checker policy: You cannot approve your own proposal">
              <span>
                <Button size="small" variant="outlined" disabled>
                  Self Proposal
                </Button>
              </span>
            </Tooltip>
          );
        }

        return (
          <Button
            size="small"
            variant="contained"
            color="primary"
            onClick={() => handleOpenAction(item)}
          >
            Review & Check
          </Button>
        );
      },
    },
  ];

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
          High-Value False-Positive Approvals
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Maker-checker governance queue: Secondary risk officer authorization for false-positive
          resolutions exceeding ₹100,000.
        </Typography>
      </Box>

      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Box sx={{ height: 460, width: '100%' }}>
          <DataGrid
            rows={proposals}
            getRowId={(row) => row.proposal.proposal_id}
            columns={columns}
            loading={isLoading}
            pageSizeOptions={[10, 20]}
            disableRowSelectionOnClick
            sx={{ border: 'none' }}
          />
        </Box>
      </Paper>

      {/* Review Dialog */}
      <ApprovalDialog
        open={Boolean(activeItem)}
        item={activeItem}
        onClose={() => setActiveItem(null)}
        loading={decisionMutation.isPending}
        onSubmit={(decision, comment) => {
          if (!activeItem) return;
          decisionMutation.mutate({
            proposalId: activeItem.proposal.proposal_id,
            decision,
            comment,
          });
        }}
      />
    </Box>
  );
};
