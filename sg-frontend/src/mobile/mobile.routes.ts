import { Routes } from '@angular/router';
import { mobileAuthGuard,mobilePersonalAccountGuard,mobileEmailSupportGuard,mobileCommercialGuard,mobileReviewGuard } from './services/mobile-auth.guard';

export const mobileRoutes: Routes = [
  {path:'merchant/incidents',canActivate:[mobileAuthGuard],loadComponent:()=>import('./pages/mobile-incidents.page').then(m=>m.MobileIncidentsPage)},
  {path:'account',canActivate:[mobileAuthGuard],loadComponent:()=>import('./pages/mobile-account.page').then(m=>m.MobileAccountPage)},
  {path:'backoffice/dossiers',canActivate:[mobileReviewGuard],loadComponent:()=>import('./pages/mobile-backoffice.page').then(m=>m.MobileBackofficePage)},
  {path:'backoffice/dossiers/:id',canActivate:[mobileReviewGuard],loadComponent:()=>import('./pages/mobile-backoffice.page').then(m=>m.MobileBackofficePage)},
  {path:'backoffice/email-assistance',canActivate:[mobileEmailSupportGuard],loadComponent:()=>import('./pages/mobile-email-assistance.page').then(m=>m.MobileEmailAssistancePage)},
  {path:'account/recovery',data:{flow:'PASSWORD_RECOVERY'},loadComponent:()=>import('./pages/mobile-personal-account.page').then(m=>m.MobilePersonalAccountPage)},
  {path:'account/verify-email',data:{flow:'EMAIL_CHANGE'},loadComponent:()=>import('./pages/mobile-personal-account.page').then(m=>m.MobilePersonalAccountPage)},
  {path:'merchant/personal-account',canActivate:[mobilePersonalAccountGuard],loadComponent:()=>import('./pages/mobile-personal-account.page').then(m=>m.MobilePersonalEmailSettingsPage)},
  { path:'merchant/dossier/:id',canActivate:[mobileAuthGuard],data:{verifiedIdentityResume:true},loadComponent:()=>import('./pages/mobile-personal-dossier.page').then(m=>m.MobilePersonalDossierPage) },
  { path:'commercial/dossiers',canActivate:[mobileCommercialGuard],loadComponent:()=>import('./pages/mobile-commercial-dossiers.page').then(m=>m.MobileCommercialDossiersPage) },
  { path:'commercial/dossiers/:id',canActivate:[mobileCommercialGuard],loadComponent:()=>import('./pages/mobile-commercial-dossier.page').then(m=>m.MobileCommercialDossierPage) },
  { path:'onboarding/acquirers/:code',loadComponent:()=>import('./pages/mobile-onboarding.page').then(m=>m.MobileOnboardingPage) },
  { path:'onboarding',loadComponent:()=>import('./pages/mobile-onboarding.page').then(m=>m.MobileOnboardingPage) },
  { path: 'login', loadComponent: () => import('./pages/mobile-login.page').then(m => m.MobileLoginPage) },
  { path: 'activation', loadComponent: () => import('./pages/mobile-activation.page').then(m => m.MobileActivationPage) },
  { path: 'home', canActivate: [mobileAuthGuard], loadComponent: () => import('./pages/mobile-home.page').then(m => m.MobileHomePage) },
  { path: 'merchant/associations', canActivate: [mobileAuthGuard], loadComponent: () => import('./pages/mobile-personal-associations.page').then(m => m.MobilePersonalAssociationsPage) },
  { path: 'merchant/dossier', canActivate: [mobileAuthGuard],data:{verifiedIdentityResume:true}, loadComponent: () => import('./pages/mobile-personal-dossier.page').then(m => m.MobilePersonalDossierPage) },
  { path: 'commercial/prospect', canActivate: [mobileCommercialGuard], loadComponent: () => import('./pages/mobile-commercial-prospect.page').then(m => m.MobileCommercialProspectPage) },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
