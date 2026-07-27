import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { Permission } from './core/models/auth.models';

export const routes: Routes = [
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
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'campaign-generation',
        canActivate: [permissionGuard],
        data: { permissions: [Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_GENERATE] },
        loadComponent: () => import('./features/campaign-generation/campaign-generation.component').then(m => m.CampaignGenerationComponent),
      },
      {
        path: 'campaign-orchestration',
        canActivate: [permissionGuard],
        data: { permissions: [Permission.TPS_RUN, Permission.CAMPAIGN_REPLAY] },
        loadComponent: () => import('./features/campaign-orchestration/campaign-orchestration.component').then(m => m.CampaignOrchestrationComponent),
      },
      {
        path: 'executions',
        canActivate: [permissionGuard],
        data: { permissions: [Permission.EXECUTION_VIEW, Permission.CAMPAIGN_VIEW] },
        loadComponent: () => import('./features/execution-view/execution-view.component').then(m => m.ExecutionViewComponent),
      },
      {
        path: 'dmas',
        canActivate: [permissionGuard],
        data: { permissions: [Permission.CARD_PROVISION] },
        loadComponent: () => import('./features/dmas/dmas.component').then(m => m.DmasComponent),
      },
      {
        path: 'modules/:moduleCode/:screen',
        loadComponent: () => import('./features/clearing/clearing-workspace.component')
          .then(m => m.ClearingWorkspaceComponent),
      },
      {
        path: 'admin',
        canActivate: [permissionGuard],
        data: { permissions: [Permission.USER_MANAGE, Permission.ROLE_MANAGE, Permission.CATALOG_MANAGE] },
        loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent),
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
    ],
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./features/login/forbidden.component').then(m => m.ForbiddenComponent),
  },
  { path: '**', redirectTo: '' },
];
