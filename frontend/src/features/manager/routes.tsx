import { RouteObject } from 'react-router-dom';
import { ManagerDashboard } from './ManagerDashboard';
import { ApprovalsPage } from './ApprovalsPage';
import { AuditPage } from './AuditPage';
import { RuleConfigPage } from './RuleConfigPage';
import { DemoToolsPage } from './DemoToolsPage';

export const managerRoutes: RouteObject[] = [
  {
    path: '/manager',
    element: <ManagerDashboard />,
  },
  {
    path: '/manager/approvals',
    element: <ApprovalsPage />,
  },
  {
    path: '/manager/audit',
    element: <AuditPage />,
  },
  {
    path: '/manager/rules',
    element: <RuleConfigPage />,
  },
  {
    path: '/manager/demo-tools',
    element: <DemoToolsPage />,
  },
];
