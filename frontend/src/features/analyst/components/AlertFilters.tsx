import React from 'react';
import {
  Box,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  Typography,
} from '@mui/material';

export interface AlertFilterValues {
  status: string;
  priority: string;
  mineOnly: boolean;
}

export interface AlertFiltersProps {
  values: AlertFilterValues;
  onChange: (newValues: AlertFilterValues) => void;
}

export const AlertFilters: React.FC<AlertFiltersProps> = ({ values, onChange }) => {
  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
        mb: 2.5,
      }}
    >
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel id="alert-status-filter-label">Filter Status</InputLabel>
          <Select
            labelId="alert-status-filter-label"
            value={values.status}
            label="Filter Status"
            onChange={(e) => onChange({ ...values, status: e.target.value })}
          >
            <MenuItem value="ALL">All Statuses</MenuItem>
            <MenuItem value="OPEN">Open</MenuItem>
            <MenuItem value="IN_REVIEW">In Review</MenuItem>
            <MenuItem value="CONFIRMED_FRAUD">Confirmed Fraud</MenuItem>
            <MenuItem value="FALSE_POSITIVE">False Positive</MenuItem>
            <MenuItem value="PENDING_MANAGER_APPROVAL">Pending Approval</MenuItem>
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="alert-priority-filter-label">Filter Priority</InputLabel>
          <Select
            labelId="alert-priority-filter-label"
            value={values.priority}
            label="Filter Priority"
            onChange={(e) => onChange({ ...values, priority: e.target.value })}
          >
            <MenuItem value="ALL">All Priorities</MenuItem>
            <MenuItem value="HIGH">High Priority</MenuItem>
            <MenuItem value="MEDIUM">Medium Priority</MenuItem>
          </Select>
        </FormControl>

        <FormControlLabel
          control={
            <Switch
              checked={values.mineOnly}
              onChange={(e) => onChange({ ...values, mineOnly: e.target.checked })}
              color="primary"
            />
          }
          label={<Typography variant="body2" fontWeight={500}>Claimed by me</Typography>}
        />
      </Box>
    </Box>
  );
};
