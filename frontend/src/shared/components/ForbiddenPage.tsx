import React from 'react';
import { Box, Button, Container, Typography } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { useNavigate } from 'react-router-dom';

export const ForbiddenPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          minHeight: '70vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          textAlign: 'center',
        }}
      >
        <Box
          sx={{
            p: 2,
            borderRadius: '50%',
            bgcolor: 'error.lighter',
            color: 'error.main',
            mb: 2,
          }}
        >
          <LockOutlinedIcon sx={{ fontSize: 56 }} />
        </Box>
        <Typography variant="h4" fontWeight={700} gutterBottom>
          403 - Access Forbidden
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
          You do not have the required permissions or role to view this page. If you believe this is
          an error, please contact your FraudShield administrator.
        </Typography>
        <Button variant="contained" onClick={() => navigate('/')}>
          Return to Dashboard
        </Button>
      </Box>
    </Container>
  );
};
