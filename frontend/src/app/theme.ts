import { createTheme, PaletteMode } from '@mui/material';

export const STATUS_COLORS: Record<string, { main: string; bg: string; text: string }> = {
  ACTIVE: { main: '#2e7d32', bg: '#e8f5e9', text: '#1b5e20' },
  APPROVED: { main: '#2e7d32', bg: '#e8f5e9', text: '#1b5e20' },
  BLOCKED: { main: '#d32f2f', bg: '#ffebee', text: '#b71c1c' },
  STEP_UP_PENDING: { main: '#ed6c02', bg: '#fff3e0', text: '#e65100' },
  IN_REVIEW: { main: '#0288d1', bg: '#e1f5fe', text: '#01579b' },
  OPEN: { main: '#757575', bg: '#f5f5f5', text: '#424242' },
  CONFIRMED_FRAUD: { main: '#d32f2f', bg: '#ffebee', text: '#b71c1c' },
  FALSE_POSITIVE: { main: '#00897b', bg: '#e0f2f1', text: '#004d40' },
  PENDING_MANAGER_APPROVAL: { main: '#7b1fa2', bg: '#f3e5f5', text: '#4a148c' },
  SLA_BREACHED: { main: '#b71c1c', bg: '#ffebee', text: '#b71c1c' },
  RECEIVED: { main: '#9e9e9e', bg: '#eeeeee', text: '#616161' },
  EXPIRED: { main: '#9e9e9e', bg: '#eeeeee', text: '#616161' },
};

export const PRIORITY_COLORS: Record<'HIGH' | 'MEDIUM', { main: string; bg: string; text: string }> = {
  HIGH: { main: '#d32f2f', bg: '#ffebee', text: '#b71c1c' },
  MEDIUM: { main: '#f57c00', bg: '#fff3e0', text: '#e65100' },
};

export const getAppTheme = (mode: PaletteMode) =>
  createTheme({
    palette: {
      mode,
      primary: {
        main: '#0A2342', // Deep navy
        light: '#23395B',
        dark: '#051329',
        contrastText: '#ffffff',
      },
      secondary: {
        main: '#1B5E8F', // Steel blue
        light: '#3C8DCD',
        dark: '#113B59',
        contrastText: '#ffffff',
      },
      warning: {
        main: '#E8A000', // Gold accent
        contrastText: '#000000',
      },
      background: {
        default: mode === 'light' ? '#f8fafc' : '#0B132B',
        paper: mode === 'light' ? '#ffffff' : '#1C2541',
      },
      text: {
        primary: mode === 'light' ? '#0F172A' : '#F8FAFC',
        secondary: mode === 'light' ? '#475569' : '#94A3B8',
      },
    },
    typography: {
      fontFamily: [
        'Inter',
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        'sans-serif',
      ].join(','),
      h5: {
        fontWeight: 600,
      },
      h6: {
        fontWeight: 600,
      },
      subtitle1: {
        fontWeight: 500,
      },
      button: {
        textTransform: 'none',
        fontWeight: 600,
      },
    },
    shape: {
      borderRadius: 8,
    },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            boxShadow: 'none',
            '&:hover': {
              boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            boxShadow:
              mode === 'light'
                ? '0 1px 3px 0 rgba(0, 0, 0, 0.08), 0 1px 2px -1px rgba(0, 0, 0, 0.08)'
                : '0 1px 3px 0 rgba(0, 0, 0, 0.3)',
            border: mode === 'light' ? '1px solid #E2E8F0' : '1px solid #334155',
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
        },
      },
    },
  });
