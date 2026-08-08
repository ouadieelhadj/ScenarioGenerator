import { Routes } from '@angular/router';
import { Permission } from './core/models/auth.models';
import { permissionGuard } from './core/guards/permission.guard';
import { productGuard } from './core/guards/product.guard';
import { authGuard } from './core/guards/auth.guard';

const MERCHANT_ROUTES: Routes = [
  {
    path: 'merchant/dossier',
    canActivate: [productGuard],
    data: { products: ['MERCHANT_PORTAL'] },
    loadComponent: () => import('./features/merchant-dossier/merchant-dossier.component')
      .then(m => m.MerchantDossierComponent),
  },
  {
    path: 'merchant/dashboard',
    canActivate: [productGuard],
    data: { products: ['MERCHANT_PORTAL'] },
    loadComponent: () => import('./features/merchant-dashboard/merchant-dashboard.component')
      .then(m => m.MerchantDashboardComponent),
  },
  {
    path: 'backoffice/onboarding',
    canActivate: [permissionGuard],
    data: { permissions: [Permission.ONBOARDING_KYC_REVIEW], roles: ['BACK_OFFICE', 'CHECKER', 'ADMIN'] },
    loadComponent: () => import('./features/merchant-review/merchant-review.component').then(m => m.MerchantReviewComponent),
  },
  {
    path: 'merchant/dossier/:id',
    canActivate: [productGuard],
    data: { products: ['MERCHANT_PORTAL'] },
    loadComponent: () => import('./features/merchant-dossier/merchant-dossier.component')
      .then(m => m.MerchantDossierComponent),
  },
  {
    path: 'commercial/prospects/new',
    canActivate: [productGuard, permissionGuard],
    data: {
      products: ['MERCHANT_PORTAL'],
      permissions: [Permission.ONBOARDING_PROSPECT_CREATE],
      roles: ['COMMERCIAL', 'ADMIN'],
    },
    loadComponent: () => import('./features/merchant-prospect/merchant-prospect.component')
      .then(m => m.MerchantProspectComponent),
  },
  {
    path: 'workflow/my-operations',
    data: { mode: 'operations' },
    loadComponent: () => import('./features/workflow/workflow-inbox.component')
      .then(m => m.WorkflowInboxComponent),
  },
  {
    path: 'workflow/my-approvals',
    canActivate: [permissionGuard],
    data: {
      mode: 'approvals',
      permissions: [Permission.ONBOARDING_APPROVE, Permission.ONBOARDING_KYC_REVIEW],
      roles: ['CHECKER', 'BACK_OFFICE', 'ADMIN'],
    },
    loadComponent: () => import('./features/workflow/workflow-inbox.component')
      .then(m => m.WorkflowInboxComponent),
  },
  {
    path: 'help',
    loadComponent: () => import('./features/help/help.component').then(m => m.HelpComponent),
  },
];

export const merchantPortalRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'activation',
    loadComponent: () => import('./features/merchant-activation/merchant-activation.component')
      .then(m => m.MerchantActivationComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/main-layout.component').then(m => m.MainLayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'merchant/dashboard' },
      ...MERCHANT_ROUTES,
    ],
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./features/login/forbidden.component').then(m => m.ForbiddenComponent),
  },
  { path: '**', redirectTo: '' },
];
