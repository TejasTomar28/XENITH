import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Container,
  Divider,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Link,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import SecurityIcon from '@mui/icons-material/Security';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import SpeedOutlinedIcon from '@mui/icons-material/SpeedOutlined';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../shared/hooks/useAuth';

const loginSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
  rememberMe: z.boolean().optional(),
});

type LoginFormData = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const { login } = useAuth();
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: '',
      password: '',
      rememberMe: false,
    },
  });

  const onSubmit = async (data: LoginFormData) => {
    setLoginError(null);
    try {
      const resp = await login({
        username: data.username,
        password: data.password,
      });

      const role = resp.user?.roles?.[0];
      if (role === 'CARDHOLDER') {
        navigate('/customer');
      } else if (role === 'FRAUD_ANALYST') {
        navigate('/analyst');
      } else if (role === 'FRAUD_RISK_MANAGER') {
        navigate('/manager');
      } else {
        navigate('/');
      }
    } catch (err: any) {
      setLoginError(err.message || 'Invalid credentials. Please try again.');
    }
  };

  const handleQuickLogin = (uname: string) => {
    setValue('username', uname);
    setValue('password', 'password123');
    onSubmit({ username: uname, password: 'password123' });
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', width: '100%' }}>
      {/* Left Branding Column */}
      <Box
        sx={{
          flex: 1,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 6,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Background decorative circles */}
        <Box
          sx={{
            position: 'absolute',
            top: -100,
            right: -100,
            width: 350,
            height: 350,
            borderRadius: '50%',
            bgcolor: 'primary.light',
            opacity: 0.1,
          }}
        />
        <Box
          sx={{
            position: 'absolute',
            bottom: -50,
            left: -50,
            width: 300,
            height: 300,
            borderRadius: '50%',
            bgcolor: 'warning.main',
            opacity: 0.05,
          }}
        />

        {/* Top Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <SecurityIcon sx={{ color: 'warning.main', fontSize: 40 }} />
          <Box>
            <Typography variant="h5" fontWeight={800} letterSpacing={0.5}>
              FraudShield
            </Typography>
            <Typography variant="caption" sx={{ letterSpacing: 1.5, opacity: 0.85 }}>
              KAVERI NATIONAL BANK
            </Typography>
          </Box>
        </Box>

        {/* Central Value Proposition */}
        <Box sx={{ maxWidth: 480 }}>
          <Typography variant="h3" fontWeight={700} lineHeight={1.2} gutterBottom>
            Real-Time Card Fraud Defense & Verification
          </Typography>
          <Typography variant="body1" sx={{ opacity: 0.85, mb: 4 }}>
            Empowering cardholders with instantaneous control and equipping fraud risk officers
            with AI-assisted intelligence, velocity analytics, and maker-checker governance.
          </Typography>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <ShieldOutlinedIcon sx={{ color: 'warning.main' }} />
              <Typography variant="body2">
                Sub-300ms real-time multi-tier authorization & rules engine
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <VerifiedUserOutlinedIcon sx={{ color: 'warning.main' }} />
              <Typography variant="body2">
                Instant cardholder step-up verification via secure OTP confirmation
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <SpeedOutlinedIcon sx={{ color: 'warning.main' }} />
              <Typography variant="body2">
                AI explainability with SHAP factors & responsible machine learning
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* Footer */}
        <Typography variant="caption" sx={{ opacity: 0.6 }}>
          © {new Date().getFullYear()} Kaveri National Bank. All rights reserved. Confidential
          Banking System.
        </Typography>
      </Box>

      {/* Right Login Form Column */}
      <Box
        sx={{
          flex: { xs: 1, md: 0.8, lg: 0.7 },
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          p: { xs: 3, sm: 6 },
          bgcolor: 'background.default',
        }}
      >
        <Container maxWidth="xs" disableGutters>
          {/* Mobile Brand Banner */}
          <Box sx={{ display: { xs: 'flex', md: 'none' }, alignItems: 'center', gap: 1, mb: 3 }}>
            <SecurityIcon color="primary" fontSize="large" />
            <Box>
              <Typography variant="h6" fontWeight={700}>
                FraudShield
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Kaveri National Bank
              </Typography>
            </Box>
          </Box>

          <Paper
            elevation={0}
            sx={{
              p: { xs: 3, sm: 4 },
              borderRadius: 3,
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.paper',
            }}
          >
            <Typography variant="h5" fontWeight={700} gutterBottom>
              Sign In
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Enter your credentials to access your secure portal
            </Typography>

            {loginError && (
              <Alert severity="error" sx={{ mb: 3 }}>
                {loginError}
              </Alert>
            )}

            <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
              <TextField
                margin="normal"
                fullWidth
                id="username"
                label="Username"
                autoComplete="username"
                autoFocus
                {...register('username')}
                error={Boolean(errors.username)}
                helperText={errors.username?.message}
                disabled={isSubmitting}
              />

              <TextField
                margin="normal"
                fullWidth
                id="password"
                label="Password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                {...register('password')}
                error={Boolean(errors.password)}
                helperText={errors.password?.message}
                disabled={isSubmitting}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label="toggle password visibility"
                        onClick={() => setShowPassword(!showPassword)}
                        edge="end"
                      >
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
              />

              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 1 }}>
                <FormControlLabel
                  control={<Checkbox {...register('rememberMe')} color="primary" />}
                  label={<Typography variant="body2">Remember me</Typography>}
                />
              </Box>

              <Button
                type="submit"
                fullWidth
                variant="contained"
                size="large"
                disabled={isSubmitting}
                sx={{ mt: 3, mb: 2, py: 1.25, fontWeight: 700 }}
              >
                {isSubmitting ? 'Signing in...' : 'Sign In'}
              </Button>

              <Box sx={{ textAlign: 'center', mt: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Cardholder without an account?{' '}
                  <Link component={RouterLink} to="/register" underline="hover" fontWeight={600}>
                    Register here
                  </Link>
                </Typography>
              </Box>
            </Box>

            {/* Quick Demo Login Helpers */}
            <Divider sx={{ my: 3 }}>
              <Typography variant="caption" color="text.secondary">
                DEMO LOGIN PROFILES
              </Typography>
            </Divider>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Chip
                label="Cardholder (cardholder1)"
                clickable
                onClick={() => handleQuickLogin('cardholder1')}
                variant="outlined"
                color="primary"
                sx={{ justifyContent: 'flex-start', py: 2, fontWeight: 500 }}
              />
              <Chip
                label="Fraud Analyst (analyst1)"
                clickable
                onClick={() => handleQuickLogin('analyst1')}
                variant="outlined"
                color="secondary"
                sx={{ justifyContent: 'flex-start', py: 2, fontWeight: 500 }}
              />
              <Chip
                label="Fraud Risk Manager (manager1)"
                clickable
                onClick={() => handleQuickLogin('manager1')}
                variant="outlined"
                color="warning"
                sx={{ justifyContent: 'flex-start', py: 2, fontWeight: 500 }}
              />
            </Box>
          </Paper>
        </Container>
      </Box>
    </Box>
  );
};
