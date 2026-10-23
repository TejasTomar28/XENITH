import React from 'react';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { AuditEvent } from '../../../types/manager';
import { IstDateTime } from '../../../shared/components/IstDateTime';
import { StatusChip } from '../../../shared/components/StatusChip';

export interface AuditTimelineProps {
  events: AuditEvent[];
  loading?: boolean;
}

export const AuditTimeline: React.FC<AuditTimelineProps> = ({ events }) => {
  if (events.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
        No audit events recorded for this entity query.
      </Typography>
    );
  }

  return (
    <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
      <Table size="small">
        <TableHead sx={{ bgcolor: 'action.hover' }}>
          <TableRow>
            <TableCell sx={{ fontWeight: 700 }}>Timestamp (IST)</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Entity & ID</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Actor</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Role</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Action</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Status Transition</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>Audit Notes</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {events.map((e) => (
            <TableRow key={e.audit_id} hover>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>
                <IstDateTime value={e.timestamp} />
              </TableCell>
              <TableCell sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                {e.entity}: {e.entity_id}
              </TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{e.actor}</TableCell>
              <TableCell>
                <Typography variant="caption" sx={{ px: 1, py: 0.25, bgcolor: 'action.hover', borderRadius: 1 }}>
                  {e.role}
                </Typography>
              </TableCell>
              <TableCell sx={{ fontWeight: 600, color: 'primary.main' }}>{e.action}</TableCell>
              <TableCell>
                {e.from_status || e.to_status ? (
                  <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                    {e.from_status && <StatusChip status={e.from_status} size="small" />}
                    {e.from_status && e.to_status && <span>&rarr;</span>}
                    {e.to_status && <StatusChip status={e.to_status} size="small" />}
                  </Box>
                ) : (
                  '—'
                )}
              </TableCell>
              <TableCell sx={{ color: 'text.secondary', fontSize: '0.8rem', maxWidth: 300 }}>
                {e.comment || '—'}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};
