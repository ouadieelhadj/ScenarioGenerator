import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'auth',
    loadChildren: () =>
      import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES)
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard(['SUPER_ADMIN'])],
    loadChildren: () =>
      import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES)
  },
  {
    path: 'tenant',
    canActivate: [authGuard, roleGuard(['ADMIN_TENANT', 'SUPER_ADMIN'])],
    loadChildren: () =>
      import('./features/tenant/tenant.routes').then(m => m.TENANT_ROUTES)
  },
  {
    path: 'scenarios',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/scenario/scenario.routes').then(m => m.SCENARIO_ROUTES)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component')
        .then(m => m.DashboardComponent)
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: '**', redirectTo: 'dashboard' }
];
