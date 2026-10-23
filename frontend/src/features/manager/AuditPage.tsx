import React, { useState } from 'react';
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import { useQuery } from '@tanstack/react-query';
import { managerApi } from '../../api/manager.api';
import { useSnackbar } from 'notistack';
import { AuditTimeline } from './components/AuditTimeline';

export const AuditPage: React.FC = () => {
  const { enqueueSnackbar } = useSnackbar();
  const [entityType, setEntityType] = useState<string>('TRANSACTION');
  const [entityId, setEntityId] = useState<string>('T200000050');
  const [searchParams, setSearchParams] = useState({ entityType: 'TRANSACTION', entityId: 'T200000050' });

  const { data, isLoading } = useQuery({
    queryKey: ['manager', 'audit', searchParams],
    queryFn: () => managerApi.getAuditTrails(searchParams.entityType, searchParams.entityId),
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!entityId.trim()) {
      enqueueSnackbar('Please enter an Entity ID to search.', { variant: 'warning' });
      return;
    }
    setSearchParams({ entityType, entityId: entityId.trim() });
  };

  const handleExportCsv = async () => {
    try {
      const blob = await managerApi.exportAuditTrails(searchParams.entityType, searchParams.entityId);
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'text/csv' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `audit_${searchParams.entityType}_${searchParams.entityId}.csv`);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
      enqueueSnackbar('Audit trail CSV downloaded successfully.', { variant: 'success' });
    } catch {
      enqueueSnackbar('Failed to export audit trail.', { variant: 'error' });
    }
  };

  const events = data?.items || [];

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={800} color="primary.main" gutterBottom>
          Immutable Compliance Audit Trail
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Append-only historical audit records for transactions, lifecycle transitions, alerts, card
          blocks, and configuration modifications.
        </Typography>
      </Box>

      {/* Search & Export Toolbar */}
      <Paper
        elevation={0}
        component="form"
        onSubmit={handleSearch}
        sx={{
          p: 2.5,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
          mb: 3,
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
        }}
      >
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center' }}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="entity-type-label">Entity Type</InputLabel>
            <Select
              labelId="entity-type-label"
              value={entityType}
              label="Entity Type"
              onChange={(e) => setEntityType(e.target.value)}
            >
              <MenuItem value="TRANSACTION">TRANSACTION</MenuItem>
              <MenuItem value="ALERT">ALERT</MenuItem>
              <MenuItem value="CARD">CARD</MenuItem>
              <MenuItem value="CONFIGURATION">CONFIGURATION</MenuItem>
            </Select>
          </FormControl>

          <TextField
            size="small"
            label="Entity ID"
            placeholder="e.g. T200000050"
            value={entityId}
            onChange={(e) => setEntityId(e.target.value)}
            sx={{ minWidth: 200 }}
          />

          <Button type="submit" variant="contained" startIcon={<SearchIcon />} sx={{ height: 40 }}>
            Search Trail
          </Button>
        </Box>

        <Button
          variant="outlined"
          color="secondary"
          startIcon={<FileDownloadIcon />}
          onClick={handleExportCsv}
          sx={{ height: 40 }}
        >
          Export CSV
        </Button>
      </Paper>

      {/* Audit Events Results */}
      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Typography variant="h6" fontWeight={700} sx={{ mb: 2 }}>
          Audit Events ({searchParams.entityType}: {searchParams.entityId})
        </Typography>

        <AuditTimeline events={events} loading={isLoading} />
      </Paper>
    </Box>
  );
};
