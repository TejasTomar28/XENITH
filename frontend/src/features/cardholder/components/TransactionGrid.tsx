import React, { useState } from 'react';
import {
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef, GridRowParams } from '@mui/x-data-grid';
import { CardholderTransaction } from '../../../types/cardholder';
import { MoneyText } from '../../../shared/components/MoneyText';
import { IstDateTime } from '../../../shared/components/IstDateTime';
import { StatusChip } from '../../../shared/components/StatusChip';
import { TransactionDrawer } from './TransactionDrawer';

export interface TransactionGridProps {
  transactions: CardholderTransaction[];
  loading?: boolean;
  selectedCardId?: string;
  onCardChange?: (cardId: string) => void;
  cardsList?: { card_id: string; masked_card_number: string }[];
}

export const TransactionGrid: React.FC<TransactionGridProps> = ({
  transactions,
  loading = false,
  selectedCardId = 'ALL',
  onCardChange,
  cardsList = [],
}) => {
  const [selectedTxn, setSelectedTxn] = useState<CardholderTransaction | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 10 });

  const filteredTransactions = transactions.filter((t) => {
    if (selectedCardId !== 'ALL' && t.card_id !== selectedCardId) return false;
    if (statusFilter !== 'ALL' && t.status !== statusFilter) return false;
    return true;
  });

  const columns: GridColDef<CardholderTransaction>[] = [
    {
      field: 'txn_ts',
      headerName: 'Date / Time (IST)',
      flex: 1.2,
      minWidth: 180,
      renderCell: (params) => <IstDateTime value={params.value} />,
    },
    {
      field: 'merchant_name',
      headerName: 'Merchant',
      flex: 1.4,
      minWidth: 160,
      renderCell: (params) => (
        <Typography variant="body2" fontWeight={600}>
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'channel',
      headerName: 'Channel',
      flex: 0.8,
      minWidth: 110,
    },
    {
      field: 'txn_city',
      headerName: 'City',
      flex: 0.9,
      minWidth: 120,
    },
    {
      field: 'amount',
      headerName: 'Amount (₹)',
      flex: 1,
      minWidth: 130,
      align: 'right',
      headerAlign: 'right',
      renderCell: (params) => (
        <MoneyText value={params.value} fontWeight={600} />
      ),
    },
    {
      field: 'status',
      headerName: 'Status',
      flex: 1.1,
      minWidth: 140,
      renderCell: (params) => <StatusChip status={params.value} />,
    },
  ];

  return (
    <Paper
      elevation={0}
      sx={{
        p: 2.5,
        borderRadius: 3,
        border: '1px solid',
        borderColor: 'divider',
      }}
    >
      {/* Filters Toolbar */}
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 2,
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 2.5,
        }}
      >
        <Typography variant="h6" fontWeight={700}>
          Transactions
        </Typography>

        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {cardsList.length > 0 && onCardChange && (
            <FormControl size="small" sx={{ minWidth: 180 }}>
              <InputLabel id="card-filter-label">Filter by Card</InputLabel>
              <Select
                labelId="card-filter-label"
                value={selectedCardId}
                label="Filter by Card"
                onChange={(e) => onCardChange(e.target.value)}
              >
                <MenuItem value="ALL">All Cards</MenuItem>
                {cardsList.map((c) => (
                  <MenuItem key={c.card_id} value={c.card_id}>
                    •••• {c.masked_card_number.slice(-4)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel id="status-filter-label">Filter Status</InputLabel>
            <Select
              labelId="status-filter-label"
              value={statusFilter}
              label="Filter Status"
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <MenuItem value="ALL">All Statuses</MenuItem>
              <MenuItem value="APPROVED">Approved</MenuItem>
              <MenuItem value="STEP_UP_PENDING">Step-up Pending</MenuItem>
              <MenuItem value="BLOCKED">Blocked</MenuItem>
            </Select>
          </FormControl>
        </Box>
      </Box>

      {/* Transactions DataGrid */}
      <Box sx={{ height: 460, width: '100%' }}>
        <DataGrid
          rows={filteredTransactions}
          getRowId={(row) => row.txn_id}
          columns={columns}
          loading={loading}
          paginationModel={paginationModel}
          onPaginationModelChange={setPaginationModel}
          pageSizeOptions={[5, 10, 20]}
          onRowClick={(params: GridRowParams<CardholderTransaction>) => setSelectedTxn(params.row)}
          sx={{
            border: 'none',
            '& .MuiDataGrid-row': {
              cursor: 'pointer',
              '&:hover': {
                bgcolor: 'action.hover',
              },
            },
          }}
          disableRowSelectionOnClick
        />
      </Box>

      {/* Transaction Detail Drawer */}
      <TransactionDrawer
        open={Boolean(selectedTxn)}
        transaction={selectedTxn}
        onClose={() => setSelectedTxn(null)}
      />
    </Paper>
  );
};
