import { Routes } from '@angular/router';
import { mobileAuthGuard } from './services/mobile-auth.guard';

export const mobileRoutes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/mobile-login.page').then(m => m.MobileLoginPage) },
  { path: 'activation', loadComponent: () => import('./pages/mobile-activation.page').then(m => m.MobileActivationPage) },
  { path: 'home', canActivate: [mobileAuthGuard], loadComponent: () => import('./pages/mobile-home.page').then(m => m.MobileHomePage) },
  { path: 'merchant/dossier', canActivate: [mobileAuthGuard], loadComponent: () => import('./pages/mobile-merchant-dossier.page').then(m => m.MobileMerchantDossierPage) },
  { path: 'commercial/prospect', canActivate: [mobileAuthGuard], loadComponent: () => import('./pages/mobile-commercial-prospect.page').then(m => m.MobileCommercialProspectPage) },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
