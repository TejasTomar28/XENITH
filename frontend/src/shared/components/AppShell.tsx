import React, { useState } from 'react';
import {
  AppBar,
  Avatar,
  Box,
  Breadcrumbs,
  Chip,
  CssBaseline,
  Divider,
  Drawer,
  IconButton,
  Link,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import SecurityIcon from '@mui/icons-material/Security';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import CreditCardOutlinedIcon from '@mui/icons-material/CreditCardOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import VpnKeyOutlinedIcon from '@mui/icons-material/VpnKeyOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import BarChartOutlinedIcon from '@mui/icons-material/BarChartOutlined';
import HowToRegOutlinedIcon from '@mui/icons-material/HowToRegOutlined';
import HistoryEduOutlinedIcon from '@mui/icons-material/HistoryEduOutlined';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import BuildCircleOutlinedIcon from '@mui/icons-material/BuildCircleOutlined';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const DRAWER_WIDTH = 260;

export interface AppShellProps {
  children: React.ReactNode;
  mode: 'light' | 'dark';
  onToggleTheme: () => void;
}

export const AppShell: React.FC<AppShellProps> = ({ children, mode, onToggleTheme }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<null | HTMLElement>(null);

  const { user, role, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const handleUserMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setUserMenuAnchor(event.currentTarget);
  };

  const handleUserMenuClose = () => {
    setUserMenuAnchor(null);
  };

  const handleLogout = () => {
    handleUserMenuClose();
    logout();
    navigate('/login');
  };

  // Nav items based on role
  interface NavItem {
    label: string;
    path: string;
    icon: React.ReactNode;
  }

  const getNavItems = (): NavItem[] => {
    if (role === 'CARDHOLDER') {
      return [
        { label: 'Dashboard', path: '/customer', icon: <DashboardOutlinedIcon /> },
        { label: 'My Cards', path: '/customer/cards', icon: <CreditCardOutlinedIcon /> },
        { label: 'Transactions', path: '/customer/transactions', icon: <ReceiptLongOutlinedIcon /> },
        { label: 'Step-up Verifications', path: '/customer/step-ups', icon: <VpnKeyOutlinedIcon /> },
      ];
    }
    if (role === 'FRAUD_ANALYST') {
      return [
        { label: 'Alert Queue', path: '/analyst/alerts', icon: <WarningAmberOutlinedIcon /> },
        { label: 'My Stats', path: '/analyst/stats', icon: <BarChartOutlinedIcon /> },
      ];
    }
    if (role === 'FRAUD_RISK_MANAGER') {
      const items: NavItem[] = [
        { label: 'Dashboard', path: '/manager', icon: <DashboardOutlinedIcon /> },
        { label: 'Approvals', path: '/manager/approvals', icon: <HowToRegOutlinedIcon /> },
        { label: 'Audit Trail', path: '/manager/audit', icon: <HistoryEduOutlinedIcon /> },
        { label: 'Rule Configuration', path: '/manager/rules', icon: <TuneOutlinedIcon /> },
      ];
      if (import.meta.env.VITE_ENABLE_DEMO_TOOLS === 'true') {
        items.push({
          label: 'Demo Tools',
          path: '/manager/demo-tools',
          icon: <BuildCircleOutlinedIcon />,
        });
      }
      return items;
    }
    return [];
  };

  const navItems = getNavItems();

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Brand Header */}
      <Box
        sx={{
          p: 2.5,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
        }}
      >
        <SecurityIcon sx={{ color: 'warning.main', fontSize: 32 }} />
        <Box>
          <Typography variant="h6" fontWeight={700} sx={{ letterSpacing: 0.5, lineHeight: 1.1 }}>
            FraudShield
          </Typography>
          <Typography variant="caption" sx={{ opacity: 0.8, fontSize: '0.65rem' }}>
            Kaveri National Bank
          </Typography>
        </Box>
      </Box>

      <Divider />

      {/* Nav List */}
      <List sx={{ px: 1.5, py: 2, flex: 1 }}>
        {navItems.map((item) => {
          const isSelected =
            location.pathname === item.path ||
            (item.path !== '/customer' &&
              item.path !== '/manager' &&
              location.pathname.startsWith(item.path));

          return (
            <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={RouterLink}
                to={item.path}
                selected={isSelected}
                onClick={() => isMobile && setMobileOpen(false)}
                sx={{
                  borderRadius: 2,
                  px: 2,
                  py: 1,
                  '&.Mui-selected': {
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    '& .MuiListItemIcon-root': {
                      color: 'primary.contrastText',
                    },
                    '&:hover': {
                      bgcolor: 'primary.dark',
                    },
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    minWidth: 40,
                    color: isSelected ? 'inherit' : 'text.secondary',
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={item.label}
                  primaryTypographyProps={{
                    fontSize: '0.875rem',
                    fontWeight: isSelected ? 600 : 500,
                  }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>

      <Divider />

      {/* Role Footer */}
      <Box sx={{ p: 2, bgcolor: 'background.default' }}>
        <Typography variant="caption" color="text.secondary" display="block">
          Active Role
        </Typography>
        <Chip
          label={role?.replace(/_/g, ' ') || 'Guest'}
          size="small"
          color="primary"
          variant="outlined"
          sx={{ mt: 0.5, fontWeight: 600, fontSize: '0.7rem' }}
        />
      </Box>
    </Box>
  );

  // Generate breadcrumb items from URL
  const pathParts = location.pathname.split('/').filter(Boolean);
  const breadcrumbItems = pathParts.map((part, index) => {
    const url = `/${pathParts.slice(0, index + 1).join('/')}`;
    const isLast = index === pathParts.length - 1;
    const formatted = part.replace(/-/g, ' ').toUpperCase();

    return isLast ? (
      <Typography key={url} color="text.primary" fontSize="0.85rem" fontWeight={600}>
        {formatted}
      </Typography>
    ) : (
      <Link
        key={url}
        component={RouterLink}
        to={url}
        underline="hover"
        color="inherit"
        fontSize="0.85rem"
      >
        {formatted}
      </Link>
    );
  });

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <CssBaseline />

      {/* Top AppBar */}
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          bgcolor: 'background.paper',
          color: 'text.primary',
          boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Toolbar sx={{ justifyContent: 'space-between', px: { xs: 2, md: 3 } }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <IconButton
              color="inherit"
              edge="start"
              onClick={handleDrawerToggle}
              sx={{ display: { md: 'none' } }}
            >
              <MenuIcon />
            </IconButton>

            {/* Breadcrumbs */}
            <Breadcrumbs separator="/" aria-label="breadcrumb">
              <Link component={RouterLink} to="/" underline="hover" color="inherit" fontSize="0.85rem">
                HOME
              </Link>
              {breadcrumbItems}
            </Breadcrumbs>
          </Box>

          {/* Right Action Icons & User Menu */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {/* Theme Toggle */}
            <Tooltip title={`Switch to ${mode === 'light' ? 'Dark' : 'Light'} Mode`}>
              <IconButton onClick={onToggleTheme} color="inherit" size="small">
                {mode === 'light' ? <DarkModeOutlinedIcon /> : <LightModeOutlinedIcon />}
              </IconButton>
            </Tooltip>

            {/* User Profile */}
            <Box
              onClick={handleUserMenuOpen}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1.25,
                cursor: 'pointer',
                p: 0.5,
                borderRadius: 2,
                '&:hover': { bgcolor: 'action.hover' },
              }}
            >
              <Avatar
                sx={{
                  width: 34,
                  height: 34,
                  bgcolor: 'secondary.main',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                }}
              >
                {user?.username?.charAt(0).toUpperCase() || 'U'}
              </Avatar>
              <Box sx={{ display: { xs: 'none', sm: 'block' }, textAlign: 'left' }}>
                <Typography variant="body2" fontWeight={600} lineHeight={1.2}>
                  {user?.username || 'User'}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {role?.replace(/_/g, ' ')}
                </Typography>
              </Box>
            </Box>

            <Menu
              anchorEl={userMenuAnchor}
              open={Boolean(userMenuAnchor)}
              onClose={handleUserMenuClose}
              transformOrigin={{ horizontal: 'right', vertical: 'top' }}
              anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            >
              <Box sx={{ px: 2, py: 1 }}>
                <Typography variant="subtitle2" fontWeight={600}>
                  {user?.username}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  ID: {user?.user_id}
                </Typography>
              </Box>
              <Divider />
              <MenuItem onClick={handleLogout} sx={{ color: 'error.main', gap: 1 }}>
                <LogoutOutlinedIcon fontSize="small" />
                Sign Out
              </MenuItem>
            </Menu>
          </Box>
        </Toolbar>
      </AppBar>

      {/* Sidebar Navigation */}
      <Box
        component="nav"
        sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}
        aria-label="navigation"
      >
        {/* Mobile Drawer */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: DRAWER_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>

        {/* Desktop Permanent Drawer */}
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: DRAWER_WIDTH,
              borderRight: '1px solid',
              borderColor: 'divider',
            },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      {/* Main Content Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2, sm: 3 },
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          mt: '64px',
          minHeight: 'calc(100vh - 64px)',
        }}
      >
        {children}
      </Box>
    </Box>
  );
};
