import { Routes } from '@angular/router';
import { Permission } from './core/models/auth.models';
import { permissionGuard } from './core/guards/permission.guard';
import { productGuard } from './core/guards/product.guard';
import { moduleScreenGuard } from './core/guards/module-screen.guard';
import { createPortalRoutes } from './app.routes.shared';
import { SCREEN_LOADER } from './core/navigation/screen-loader';
import { legacyScreenLoader } from './core/navigation/screen-registry';

const LEGACY_ROUTES: Routes = [
  {
    path: 'campaign-generation',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['LEGACY'], permissions: [Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_GENERATE] },
    loadComponent: () => import('./features/campaign-generation/campaign-generation.component').then(m => m.CampaignGenerationComponent),
  },
  {
    path: 'campaign-orchestration',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['LEGACY'], permissions: [Permission.TPS_RUN, Permission.CAMPAIGN_REPLAY] },
    loadComponent: () => import('./features/campaign-orchestration/campaign-orchestration.component').then(m => m.CampaignOrchestrationComponent),
  },
  {
    path: 'executions',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['LEGACY'], permissions: [Permission.EXECUTION_VIEW, Permission.CAMPAIGN_VIEW] },
    loadComponent: () => import('./features/execution-view/execution-view.component').then(m => m.ExecutionViewComponent),
  },
  {
    path: 'dmas',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['LEGACY'], permissions: [Permission.CARD_PROVISION] },
    loadComponent: () => import('./features/dmas/dmas.component').then(m => m.DmasComponent),
  },
  {
    path: 'modules/:moduleCode/:screen',
    canActivate: [productGuard, moduleScreenGuard],
    data: { products: ['LEGACY'] },
    providers: [{ provide: SCREEN_LOADER, useValue: legacyScreenLoader }],
    loadComponent: () => import('./features/dynamic-screen/dynamic-screen-host.component')
      .then(m => m.DynamicScreenHostComponent),
  },
  {
    path: 'lab/:moduleCode/:screen',
    canActivate: [productGuard, moduleScreenGuard],
    data: { products: ['LEGACY'] },
    providers: [{ provide: SCREEN_LOADER, useValue: legacyScreenLoader }],
    loadComponent: () => import('./features/dynamic-screen/dynamic-screen-host.component')
      .then(m => m.DynamicScreenHostComponent),
  },
];

export const routes: Routes = createPortalRoutes(LEGACY_ROUTES);
