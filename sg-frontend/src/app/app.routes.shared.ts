import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { Permission } from './core/models/auth.models';

const COMMON_CHILD_ROUTES: Routes = [
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'workflow/my-operations',
    data: { mode: 'operations' },
    loadComponent: () => import('./features/workflow/workflow-inbox.component').then(m => m.WorkflowInboxComponent),
  },
  {
    path: 'workflow/my-approvals',
    data: { mode: 'approvals' },
    loadComponent: () => import('./features/workflow/workflow-inbox.component').then(m => m.WorkflowInboxComponent),
  },
  { path: 'admin', pathMatch: 'full', redirectTo: 'administration/users' },
  {
    path: 'administration/users',
    canActivate: [permissionGuard],
    data: { permissions: [Permission.USER_MANAGE] },
    loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent),
  },
  {
    path: 'administration/roles',
    canActivate: [permissionGuard],
    data: { permissions: [Permission.ROLE_MANAGE] },
    loadComponent: () => import('./features/roles/roles.component').then(m => m.RolesComponent),
  },
  {
    path: 'administration/deployments',
    canActivate: [permissionGuard], data: { permissions: [Permission.DEPLOYMENT_VIEW], section: 'overview' },
    loadComponent: () => import('./features/deployment/deployment.component').then(m => m.DeploymentComponent),
  },
  {
    path: 'administration/deployments/clients',
    canActivate: [permissionGuard], data: { permissions: [Permission.DEPLOYMENT_VIEW], section: 'clients' },
    loadComponent: () => import('./features/deployment/deployment.component').then(m => m.DeploymentComponent),
  },
  {
    path: 'administration/deployments/environments',
    canActivate: [permissionGuard], data: { permissions: [Permission.DEPLOYMENT_VIEW], section: 'environments' },
    loadComponent: () => import('./features/deployment/deployment.component').then(m => m.DeploymentComponent),
  },
  {
    path: 'administration/deployments/modules',
    canActivate: [permissionGuard], data: { permissions: [Permission.DEPLOYMENT_VIEW], section: 'modules' },
    loadComponent: () => import('./features/deployment/deployment.component').then(m => m.DeploymentComponent),
  },
  {
    path: 'administration/deployments/licenses',
    canActivate: [permissionGuard], data: { permissions: [Permission.DEPLOYMENT_VIEW], section: 'licenses' },
    loadComponent: () => import('./features/deployment/deployment.component').then(m => m.DeploymentComponent),
  },
  {
    path: 'administration/deployments/executions',
    canActivate: [permissionGuard], data: { permissions: [Permission.DEPLOYMENT_VIEW], section: 'executions' },
    loadComponent: () => import('./features/deployment/deployment.component').then(m => m.DeploymentComponent),
  },
  {
    path: 'profile',
    loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent),
  },
  {
    path: 'config',
    canActivate: [permissionGuard],
    data: { permissions: [Permission.USER_MANAGE] },
    loadComponent: () => import('./features/config/config.component').then(m => m.ConfigComponent),
  },
  {
    path: 'help',
    loadComponent: () => import('./features/help/help.component').then(m => m.HelpComponent),
  },
];

export function createPortalRoutes(productRoutes: Routes): Routes {
  return [
    {
      path: 'login',
      loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
    },
    {
      path: '',
      loadComponent: () => import('./layout/main-layout.component').then(m => m.MainLayoutComponent),
      canActivate: [authGuard],
      children: [
        { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
        ...COMMON_CHILD_ROUTES,
        ...productRoutes,
      ],
    },
    {
      path: 'forbidden',
      loadComponent: () => import('./features/login/forbidden.component').then(m => m.ForbiddenComponent),
    },
    { path: '**', redirectTo: '' },
  ];
}
