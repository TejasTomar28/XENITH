import React from 'react';
import { Box, Button, Container, Typography } from '@mui/material';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import { useNavigate } from 'react-router-dom';

export const NotFoundPage: React.FC = () => {
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
            bgcolor: 'action.hover',
            color: 'text.secondary',
            mb: 2,
          }}
        >
          <SearchOffIcon sx={{ fontSize: 56 }} />
        </Box>
        <Typography variant="h4" fontWeight={700} gutterBottom>
          404 - Page Not Found
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
          The page or resource you are looking for does not exist or has been moved.
        </Typography>
        <Button variant="contained" onClick={() => navigate('/')}>
          Return Home
        </Button>
      </Box>
    </Container>
  );
};
