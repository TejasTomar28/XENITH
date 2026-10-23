import React from 'react';
import {
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { CardholderTransaction } from '../../../types/cardholder';
import { MoneyText } from '../../../shared/components/MoneyText';
import { IstDateTime } from '../../../shared/components/IstDateTime';
import { StatusChip } from '../../../shared/components/StatusChip';

export interface TransactionDrawerProps {
  open: boolean;
  transaction: CardholderTransaction | null;
  onClose: () => void;
}

export const TransactionDrawer: React.FC<TransactionDrawerProps> = ({
  open,
  transaction,
  onClose,
}) => {
  if (!transaction) return null;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 400 }, p: 3 },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h6" fontWeight={700}>
          Transaction Details
        </Typography>
        <IconButton onClick={onClose} edge="end" size="small">
          <CloseIcon />
        </IconButton>
      </Box>

      <Divider sx={{ mb: 2 }} />

      {/* Hero Amount & Status */}
      <Box sx={{ textAlign: 'center', my: 2 }}>
        <MoneyText value={transaction.amount} variant="h4" fontWeight={700} color="primary.main" />
        <Box sx={{ mt: 1 }}>
          <StatusChip status={transaction.status} />
        </Box>
      </Box>

      <List disablePadding sx={{ mt: 2 }}>
        <ListItem disableGutters sx={{ py: 1 }}>
          <ListItemText
            primary="Transaction ID"
            secondary={transaction.txn_id}
            primaryTypographyProps={{ variant: 'caption', color: 'text.secondary' }}
            secondaryTypographyProps={{ variant: 'body2', fontWeight: 600, fontFamily: 'monospace' }}
          />
        </ListItem>
        <Divider />

        <ListItem disableGutters sx={{ py: 1 }}>
          <ListItemText
            primary="Merchant Name"
            secondary={transaction.merchant_name}
            primaryTypographyProps={{ variant: 'caption', color: 'text.secondary' }}
            secondaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
          />
        </ListItem>
        <Divider />

        <ListItem disableGutters sx={{ py: 1 }}>
          <ListItemText
            primary="Date & Time (IST)"
            secondary={<IstDateTime value={transaction.txn_ts} />}
            primaryTypographyProps={{ variant: 'caption', color: 'text.secondary' }}
            secondaryTypographyProps={{ variant: 'body2' }}
          />
        </ListItem>
        <Divider />

        <ListItem disableGutters sx={{ py: 1 }}>
          <ListItemText
            primary="Payment Channel"
            secondary={transaction.channel}
            primaryTypographyProps={{ variant: 'caption', color: 'text.secondary' }}
            secondaryTypographyProps={{ variant: 'body2', fontWeight: 500 }}
          />
        </ListItem>
        <Divider />

        <ListItem disableGutters sx={{ py: 1 }}>
          <ListItemText
            primary="Location"
            secondary={`${transaction.txn_city}, ${transaction.txn_country}`}
            primaryTypographyProps={{ variant: 'caption', color: 'text.secondary' }}
            secondaryTypographyProps={{ variant: 'body2' }}
          />
        </ListItem>
        <Divider />

        <ListItem disableGutters sx={{ py: 1.5 }}>
          <ListItemText
            primary="Status Description"
            secondary={transaction.display_reason || 'Processed according to account policy.'}
            primaryTypographyProps={{ variant: 'caption', color: 'text.secondary' }}
            secondaryTypographyProps={{
              variant: 'body2',
              color: 'text.primary',
              sx: { mt: 0.5, p: 1.5, bgcolor: 'background.default', borderRadius: 1.5 },
            }}
          />
        </ListItem>
      </List>
    </Drawer>
  );
};
