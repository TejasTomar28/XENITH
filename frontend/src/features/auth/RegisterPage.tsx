import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Container,
  Divider,
  IconButton,
  InputAdornment,
  LinearProgress,
  Link,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import SecurityIcon from '@mui/icons-material/Security';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth.api';

const registerSchema = z
  .object({
    fullName: z.string().min(2, 'Full name is required'),
    email: z.string().email('Enter a valid email address'),
    mobile: z.string().regex(/^[6-9]\d{9}$/, 'Must be a valid 10-digit Indian mobile number'),
    username: z.string().min(4, 'Username must be at least 4 characters'),
    last4Digits: z.string().regex(/^\d{4}$/, 'Must be the exact last 4 digits of your card'),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(8, 'Please confirm your password'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

export const RegisterPage: React.FC = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: '',
      email: '',
      mobile: '',
      username: '',
      last4Digits: '',
      password: '',
      confirmPassword: '',
    },
  });

  const passwordVal = watch('password', '');
  const getPasswordStrength = (pwd: string): { score: number; label: string; color: 'error' | 'warning' | 'success' } => {
    let score = 0;
    if (pwd.length >= 8) score += 25;
    if (/[A-Z]/.test(pwd)) score += 25;
    if (/[0-9]/.test(pwd)) score += 25;
    if (/[^A-Za-z0-9]/.test(pwd)) score += 25;

    if (score <= 25) return { score, label: 'Weak', color: 'error' };
    if (score <= 50) return { score, label: 'Fair', color: 'warning' };
    if (score <= 75) return { score, label: 'Good', color: 'warning' };
    return { score, label: 'Strong', color: 'success' };
  };

  const strength = getPasswordStrength(passwordVal);

  const onSubmit = async (data: RegisterFormData) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await authApi.register({
        username: data.username,
        password: data.password,
        fullName: data.fullName,
        email: data.email,
        mobile: data.mobile,
        last4Digits: data.last4Digits,
      });
      setSuccessMsg('Registration successful! Redirecting to customer dashboard...');
      setTimeout(() => {
        navigate('/customer');
      }, 1500);
    } catch (err: any) {
      setErrorMsg(err.message || 'Registration failed. Please check your details.');
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        bgcolor: 'background.default',
        py: 6,
        px: 2,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Container maxWidth="sm">
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1, mb: 1 }}>
            <SecurityIcon color="primary" fontSize="large" />
            <Typography variant="h5" fontWeight={800}>
              FraudShield
            </Typography>
          </Box>
          <Typography variant="body2" color="text.secondary">
            Kaveri National Bank Cardholder Self-Service Registration
          </Typography>
        </Box>

        <Paper
          elevation={0}
          sx={{
            p: { xs: 3, sm: 4 },
            borderRadius: 3,
            border: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Typography variant="h6" fontWeight={700} gutterBottom>
            Register Cardholder Account
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Note: Bank staff accounts (Fraud Analyst / Risk Manager) cannot be self-registered and
            are provisioned by enterprise security.
          </Typography>

          {errorMsg && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {errorMsg}
            </Alert>
          )}

          {successMsg && (
            <Alert severity="success" sx={{ mb: 2 }}>
              {successMsg}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <TextField
              margin="normal"
              fullWidth
              label="Full Name"
              {...register('fullName')}
              error={Boolean(errors.fullName)}
              helperText={errors.fullName?.message}
            />

            <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
              <TextField
                margin="normal"
                fullWidth
                label="Email Address"
                {...register('email')}
                error={Boolean(errors.email)}
                helperText={errors.email?.message}
              />
              <TextField
                margin="normal"
                fullWidth
                label="Mobile (10 digits)"
                placeholder="9876543210"
                {...register('mobile')}
                error={Boolean(errors.mobile)}
                helperText={errors.mobile?.message}
              />
            </Box>

            <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
              <TextField
                margin="normal"
                fullWidth
                label="Desired Username"
                {...register('username')}
                error={Boolean(errors.username)}
                helperText={errors.username?.message}
              />
              <TextField
                margin="normal"
                fullWidth
                label="Last 4 Digits of Card"
                placeholder="1234"
                inputProps={{ maxLength: 4 }}
                {...register('last4Digits')}
                error={Boolean(errors.last4Digits)}
                helperText={errors.last4Digits?.message || 'Identity verification'}
              />
            </Box>

            <TextField
              margin="normal"
              fullWidth
              label="Password"
              type={showPassword ? 'text' : 'password'}
              {...register('password')}
              error={Boolean(errors.password)}
              helperText={errors.password?.message}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword(!showPassword)}
                      edge="end"
                    >
                      {showPassword ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />

            {passwordVal && (
              <Box sx={{ mt: 1, mb: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    Password strength
                  </Typography>
                  <Typography variant="caption" color={`${strength.color}.main`} fontWeight={600}>
                    {strength.label}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={strength.score}
                  color={strength.color}
                  sx={{ height: 6, borderRadius: 3 }}
                />
              </Box>
            )}

            <TextField
              margin="normal"
              fullWidth
              label="Confirm Password"
              type={showPassword ? 'text' : 'password'}
              {...register('confirmPassword')}
              error={Boolean(errors.confirmPassword)}
              helperText={errors.confirmPassword?.message}
            />

            <Button
              type="submit"
              fullWidth
              variant="contained"
              size="large"
              disabled={isSubmitting}
              sx={{ mt: 3, mb: 2, py: 1.25, fontWeight: 700 }}
            >
              {isSubmitting ? 'Registering...' : 'Complete Registration'}
            </Button>

            <Divider sx={{ my: 2 }} />

            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                Already registered?{' '}
                <Link component={RouterLink} to="/login" underline="hover" fontWeight={600}>
                  Sign in here
                </Link>
              </Typography>
            </Box>
          </Box>
        </Paper>
      </Container>
    </Box>
  );
};
