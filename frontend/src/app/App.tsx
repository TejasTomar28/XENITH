import React, { useMemo, useState } from 'react';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SnackbarProvider } from 'notistack';
import { RouterProvider } from 'react-router-dom';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { getAppTheme } from './theme';
import { createRouter } from './router';
import { AuthProvider } from '../shared/hooks/useAuth';
import { ErrorBoundary } from '../shared/components/ErrorBoundary';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30000,
      gcTime: 300000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export const App: React.FC = () => {
  const [themeMode, setThemeMode] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('fraudshield_theme_mode');
    return saved === 'dark' ? 'dark' : 'light';
  });

  const toggleTheme = () => {
    setThemeMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem('fraudshield_theme_mode', next);
      return next;
    });
  };

  const theme = useMemo(() => getAppTheme(themeMode), [themeMode]);
  const router = useMemo(() => createRouter(themeMode, toggleTheme), [themeMode]);

  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <LocalizationProvider dateAdapter={AdapterDayjs}>
          <ThemeProvider theme={theme}>
            <CssBaseline />
            <SnackbarProvider
              maxSnack={3}
              autoHideDuration={4000}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
              <AuthProvider>
                <RouterProvider router={router} />
              </AuthProvider>
            </SnackbarProvider>
          </ThemeProvider>
        </LocalizationProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
};

export default App;
