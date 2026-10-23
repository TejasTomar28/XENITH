import React from 'react';
import { Box, Card, CardContent, CardHeader, Typography } from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import { LineChart } from '@mui/x-charts/LineChart';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { CardholderTransaction } from '../../../types/cardholder';
import { MoneyText } from '../../../shared/components/MoneyText';
import { IstDateTime } from '../../../shared/components/IstDateTime';
import { StatusChip } from '../../../shared/components/StatusChip';
import { parseMoney } from '../../../shared/utils/money';

export interface CardHistoryPanelProps {
  transactions: CardholderTransaction[];
  loading?: boolean;
}

export const CardHistoryPanel: React.FC<CardHistoryPanelProps> = ({
  transactions,
  loading = false,
}) => {
  // Sort oldest to newest for trend line
  const sorted = [...transactions].sort(
    (a, b) => new Date(a.txn_ts).getTime() - new Date(b.txn_ts).getTime()
  );

  const amounts = sorted.map((t) => parseMoney(t.amount));
  const indices = sorted.map((_, i) => i + 1);

  const columns: GridColDef<CardholderTransaction>[] = [
    {
      field: 'txn_ts',
      headerName: 'Date / Time (IST)',
      flex: 1.2,
      minWidth: 170,
      renderCell: (params) => <IstDateTime value={params.value} />,
    },
    {
      field: 'merchant_name',
      headerName: 'Merchant',
      flex: 1.2,
      minWidth: 140,
    },
    {
      field: 'channel',
      headerName: 'Channel',
      flex: 0.8,
      minWidth: 100,
    },
    {
      field: 'amount',
      headerName: 'Amount (₹)',
      flex: 1,
      minWidth: 120,
      align: 'right',
      headerAlign: 'right',
      renderCell: (params) => <MoneyText value={params.value} fontWeight={600} />,
    },
    {
      field: 'status',
      headerName: 'Status',
      flex: 1,
      minWidth: 130,
      renderCell: (params) => <StatusChip status={params.value} />,
    },
  ];

  return (
    <Card sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
      <CardHeader
        avatar={<HistoryIcon color="primary" />}
        title={<Typography variant="h6" fontWeight={700}>Card Historical Activity</Typography>}
        subheader="Prior transactions for pattern and velocity analysis"
      />
      <CardContent sx={{ pt: 0 }}>
        {/* Trend Line Chart */}
        {amounts.length > 0 && (
          <Box sx={{ height: 220, width: '100%', mb: 2 }}>
            <LineChart
              xAxis={[{ data: indices, label: 'Prior Transaction Sequence' }]}
              series={[
                {
                  data: amounts,
                  label: 'Amount (₹)',
                  color: '#1B5E8F',
                },
              ]}
              height={200}
            />
          </Box>
        )}

        {/* Small DataGrid */}
        <Box sx={{ height: 260, width: '100%' }}>
          <DataGrid
            rows={transactions}
            getRowId={(row) => row.txn_id}
            columns={columns}
            loading={loading}
            pageSizeOptions={[5]}
            initialState={{
              pagination: { paginationModel: { pageSize: 5, page: 0 } },
            }}
            disableRowSelectionOnClick
            sx={{ border: 'none' }}
          />
        </Box>
      </CardContent>
    </Card>
  );
};
