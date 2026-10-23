import React from 'react';
import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { AuthGuard } from '../features/auth/AuthGuard';
import { AppShell } from '../shared/components/AppShell';
import { ForbiddenPage } from '../shared/components/ForbiddenPage';
import { NotFoundPage } from '../shared/components/NotFoundPage';
import { cardholderRoutes } from '../features/cardholder/routes';
import { analystRoutes } from '../features/analyst/routes';
import { managerRoutes } from '../features/manager/routes';
import { useAuth } from '../shared/hooks/useAuth';

// Role redirection helper
const RoleHomeRedirect: React.FC = () => {
  const { role } = useAuth();
  if (role === 'CARDHOLDER') return <Navigate to="/customer" replace />;
  if (role === 'FRAUD_ANALYST') return <Navigate to="/analyst" replace />;
  if (role === 'FRAUD_RISK_MANAGER') return <Navigate to="/manager" replace />;
  return <Navigate to="/login" replace />;
};

export const createRouter = (
  themeMode: 'light' | 'dark',
  onToggleTheme: () => void
) =>
  createBrowserRouter([
    // Public Auth Routes
    {
      path: '/login',
      element: <LoginPage />,
    },
    {
      path: '/register',
      element: <RegisterPage />,
    },
    {
      path: '/403',
      element: <ForbiddenPage />,
    },

    // Authenticated App Shell Root
    {
      path: '/',
      element: (
        <AuthGuard>
          <AppShell mode={themeMode} onToggleTheme={onToggleTheme}>
            <Outlet />
          </AppShell>
        </AuthGuard>
      ),
      children: [
        {
          index: true,
          element: <RoleHomeRedirect />,
        },
        // Cardholder Portal Routes
        {
          element: <AuthGuard allowedRoles={['CARDHOLDER']}><Outlet /></AuthGuard>,
          children: cardholderRoutes,
        },
        // Fraud Analyst Portal Routes
        {
          element: <AuthGuard allowedRoles={['FRAUD_ANALYST']}><Outlet /></AuthGuard>,
          children: analystRoutes,
        },
        // Fraud Risk Manager Portal Routes
        {
          element: <AuthGuard allowedRoles={['FRAUD_RISK_MANAGER']}><Outlet /></AuthGuard>,
          children: managerRoutes,
        },
      ],
    },

    // Fallback 404 Not Found
    {
      path: '*',
      element: <NotFoundPage />,
    },
  ]);
