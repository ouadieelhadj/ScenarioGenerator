import { Routes } from '@angular/router';
import { Permission } from './core/models/auth.models';
import { permissionGuard } from './core/guards/permission.guard';
import { productGuard } from './core/guards/product.guard';
import { authGuard } from './core/guards/auth.guard';
import { merchantOnboardingGuard } from './core/guards/merchant-onboarding.guard';

const MERCHANT_ROUTES: Routes = [
  {path:'administration/merchant-lifecycle',canActivate:[permissionGuard],data:{permissions:['MERCHANT_LIFECYCLE_VIEW','MERCHANT_SUSPEND']},loadComponent:()=>import('./features/merchant-lifecycle/merchant-lifecycle.component').then(m=>m.MerchantLifecycleComponent)},
  {path:'administration/incidents',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],permissions:['INCIDENT_CONFIGURE']},loadComponent:()=>import('./features/merchant-incidents/incident-administration.component').then(m=>m.IncidentAdministrationComponent)},
  {path:'merchant/incidents',canActivate:[productGuard],data:{products:['MERCHANT_PORTAL']},loadComponent:()=>import('./features/merchant-incidents/merchant-incidents.component').then(m=>m.MerchantIncidentsComponent)},
  {path:'administration/roles',canActivate:[permissionGuard],data:{permissions:['ROLE_MANAGE']},loadComponent:()=>import('./features/roles/roles.component').then(m=>m.RolesComponent)},
  {path:'backoffice/email-assistance',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],permissions:['ONBOARDING_PERSONAL_EMAIL_SUPPORT']},loadComponent:()=>import('./features/personal-account/personal-email-assistance.component').then(m=>m.PersonalEmailAssistancePageComponent)},
  {path:'merchant/personal-account',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['MERCHANT']},loadComponent:()=>import('./features/personal-account/personal-account-forms.component').then(m=>m.PersonalEmailSettingsComponent)},
  { path:'administration/commercial',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['ADMIN','PLATFORM_ADMIN']},
    loadComponent:()=>import('./features/merchant-commercial/merchant-commercial-configuration.component').then(m=>m.MerchantCommercialConfigurationComponent) },
  { path:'commercial/dossiers',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['COMMERCIAL','ADMIN','PLATFORM_ADMIN'],permissions:['ONBOARDING_PROSPECT_CREATE','ONBOARDING_COMMERCIAL_REASSIGN']},
    loadComponent:()=>import('./features/merchant-commercial/merchant-commercial-queue.component').then(m=>m.MerchantCommercialQueueComponent) },
  { path:'commercial/dossiers/:id',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['COMMERCIAL','ADMIN','PLATFORM_ADMIN'],permissions:['ONBOARDING_PROSPECT_CREATE','ONBOARDING_COMMERCIAL_REASSIGN']},
    loadComponent:()=>import('./features/merchant-dossier/merchant-dossier.component').then(m=>m.MerchantDossierComponent) },
  {
    path:'merchant/associations',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['MERCHANT','COMMERCANT']},
    loadComponent:()=>import('./features/personal-merchant-associations/personal-merchant-associations.component').then(m=>m.PersonalMerchantAssociationsComponent),
  },
  {
    path:'backoffice/public-registrations',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['PLATFORM_ADMIN','ADMIN','BACK_OFFICE']},
    loadComponent:()=>import('./features/merchant-onboarding-entry/merchant-public-registrations.component').then(m=>m.MerchantPublicRegistrationsComponent),
  },
  {
    path:'backoffice/synchronizations',canActivate:[productGuard,permissionGuard],data:{products:['MERCHANT_PORTAL'],roles:['PLATFORM_ADMIN','ADMIN','BACK_OFFICE']},
    loadComponent:()=>import('./features/merchant-synchronizations/merchant-synchronizations.component').then(m=>m.MerchantSynchronizationsComponent),
  },
  {
    path:'merchant/hierarchy',canActivate:[productGuard],data:{products:['MERCHANT_PORTAL']},
    loadComponent:()=>import('./features/merchant-hierarchy/merchant-hierarchy.component').then(m=>m.MerchantHierarchyComponent),
  },
  {
    path: 'merchant/chargebacks', canActivate: [productGuard], data: { products: ['MERCHANT_PORTAL'] },
    loadComponent: () => import('./features/merchant-chargebacks/merchant-chargebacks.component').then(m => m.MerchantChargebacksComponent),
  },
  {
    path: 'merchant/requests', canActivate: [productGuard], data: { products: ['MERCHANT_PORTAL'] },
    loadComponent: () => import('./features/merchant-operations/merchant-operations.component').then(m => m.MerchantOperationsComponent),
  },
  {
    path: 'merchant/reconciliation', canActivate: [productGuard], data: { products: ['MERCHANT_PORTAL'] },
    loadComponent: () => import('./features/merchant-reconciliation/merchant-reconciliation.component').then(m => m.MerchantReconciliationComponent),
  },
  {
    path: 'merchant/statements', canActivate: [productGuard],
    data: { products: ['MERCHANT_PORTAL'], activityTitle: 'Relevés commerçants', exportEnabled: true },
    loadComponent: () => import('./features/merchant-activity/merchant-activity.component').then(m => m.MerchantActivityComponent),
  },
  ...[
    { path: 'merchant/activity', activityTitle: "Tableau de bord d'activité", kind: undefined },
    { path: 'merchant/authorizations', activityTitle: 'Autorisations', kind: 'AUTHORIZATION' },
    { path: 'merchant/transactions', activityTitle: 'Transactions', kind: 'TRANSACTION' },
    { path: 'merchant/fees', activityTitle: 'Commissions et frais appliqués', kind: 'FEE' },
    { path: 'merchant/settlements', activityTitle: 'Règlements', kind: 'SETTLEMENT' },
  ].map(page => ({
    path: page.path, canActivate: [productGuard],
    data: { products: ['MERCHANT_PORTAL'], activityTitle: page.activityTitle, kind: page.kind },
    loadComponent: () => import('./features/merchant-activity/merchant-activity.component').then(m => m.MerchantActivityComponent),
  })),
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
    path: 'backoffice/provisioning',
    canActivate: [permissionGuard],
    data: { permissions: [Permission.ONBOARDING_PROVISION], roles: ['BACK_OFFICE', 'ADMIN'] },
    loadComponent: () => import('./features/merchant-provisioning/merchant-provisioning.component')
      .then(m => m.MerchantProvisioningComponent),
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
    path: 'administration/acquirers',
    canActivate: [permissionGuard],
    data: { permissions: [Permission.ACQUIRER_PLATFORM_ADMIN, Permission.ACQUIRER_PLATFORM_CHECKER] },
    loadComponent: () => import('./features/acquirer-administration/acquirer-administration.component')
      .then(m => m.AcquirerAdministrationComponent),
  },
  {
    path: 'help',
    loadComponent: () => import('./features/help/help.component').then(m => m.HelpComponent),
  },
];

export const merchantPortalRoutes: Routes = [
  {
    path:'onboarding/acquirers/:code',
    loadComponent:()=>import('./features/merchant-onboarding-entry/merchant-onboarding-entry.component').then(m=>m.MerchantOnboardingEntryComponent),
  },
  {
    path:'onboarding',pathMatch:'full',
    loadComponent:()=>import('./features/merchant-onboarding-entry/merchant-onboarding-entry.component').then(m=>m.MerchantOnboardingEntryComponent),
  },
  {
    path:'onboarding/login',data:{onboardingLogin:true},
    loadComponent:()=>import('./features/login/login.component').then(m=>m.LoginComponent),
  },
  {
    path:'onboarding/dossier',canActivate:[productGuard,merchantOnboardingGuard],data:{products:['MERCHANT_PORTAL']},
    loadComponent:()=>import('./features/merchant-onboarding-entry/merchant-onboarding-entry.component').then(m=>m.MerchantOnboardingLayoutComponent),
    children:[{path:'',pathMatch:'full',data:{verifiedIdentityResume:true},loadComponent:()=>import('./features/merchant-dossier/merchant-dossier.component').then(m=>m.MerchantDossierComponent)}],
  },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'activation',
    loadComponent: () => import('./features/merchant-activation/merchant-activation.component')
      .then(m => m.MerchantActivationComponent),
  },
  {path:'account/recovery',data:{flow:'PASSWORD_RECOVERY'},loadComponent:()=>import('./features/personal-account/personal-account-page.component').then(m=>m.PersonalAccountPageComponent)},
  {path:'account/verify-email',data:{flow:'EMAIL_CHANGE'},loadComponent:()=>import('./features/personal-account/personal-account-page.component').then(m=>m.PersonalAccountPageComponent)},
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
