import { Routes } from '@angular/router';
import { Permission } from './core/models/auth.models';
import { permissionGuard } from './core/guards/permission.guard';
import { productGuard } from './core/guards/product.guard';
import { moduleScreenGuard } from './core/guards/module-screen.guard';
import { createPortalRoutes } from './app.routes.shared';
import { SCREEN_LOADER } from './core/navigation/screen-loader';
import { switchLabScreenLoader } from './core/navigation/screen-registry.switchlab';

const SWITCHLAB_ROUTES: Routes = [
  {
    path: 'campaign-generation',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['SWITCHLAB'], permissions: [Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_GENERATE] },
    loadComponent: () => import('./features/campaign-generation/campaign-generation.component').then(m => m.CampaignGenerationComponent),
  },
  {
    path: 'campaign-orchestration',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['SWITCHLAB'], permissions: [Permission.TPS_RUN, Permission.CAMPAIGN_REPLAY] },
    loadComponent: () => import('./features/campaign-orchestration/campaign-orchestration.component').then(m => m.CampaignOrchestrationComponent),
  },
  {
    path: 'executions',
    canActivate: [productGuard, permissionGuard],
    data: { products: ['SWITCHLAB'], permissions: [Permission.EXECUTION_VIEW, Permission.CAMPAIGN_VIEW] },
    loadComponent: () => import('./features/execution-view/execution-view.component').then(m => m.ExecutionViewComponent),
  },
  {
    path: 'product/operations',
    canActivate: [productGuard],
    data: { products: ['SWITCHLAB'], titleKey: 'menu.operations', icon: 'pi pi-desktop' },
    loadComponent: () => import('./features/product-area/product-area.component').then(m => m.ProductAreaComponent),
  },
  {
    path: 'lab/pos',
    canActivate: [productGuard], data: { products: ['SWITCHLAB'] },
    loadComponent: () => import('./features/switchlab-pos/switchlab-pos.component').then(m => m.SwitchLabPosComponent),
  },
  {
    path: 'lab/test-center',
    canActivate: [productGuard], data: { products: ['SWITCHLAB'] },
    loadComponent: () => import('./features/switchlab-test-center/switchlab-test-center.component').then(m => m.SwitchLabTestCenterComponent),
  },
  {
    path: 'lab/fraud-monitoring', canActivate: [productGuard],
    data: { products: ['SWITCHLAB'], workspace: 'SWITCHLAB' },
    loadComponent: () => import('./features/fraud-workspace/fraud-workspace.component').then(m => m.FraudWorkspaceComponent),
  },
  {
    path: 'lab/online',
    canActivate: [productGuard], data: { products: ['SWITCHLAB'] },
    loadComponent: () => import('./features/switchlab-online/switchlab-online.component').then(m => m.SwitchLabOnlineComponent),
  },
  {
    path: 'lab/clearing',
    canActivate: [productGuard], data: { products: ['SWITCHLAB'] },
    loadComponent: () => import('./features/switchlab-clearing/switchlab-clearing.component').then(m => m.SwitchLabClearingComponent),
  },
  {
    path: 'lab/ecommerce',
    canActivate: [productGuard], data: { products: ['SWITCHLAB'] },
    loadComponent: () => import('./features/switchlab-ecommerce/switchlab-ecommerce.component').then(m => m.SwitchLabEcommerceComponent),
  },
  {
    path: 'lab/industrialization',
    canActivate: [productGuard], data: { products: ['SWITCHLAB'] },
    loadComponent: () => import('./features/switchlab-industrialization/switchlab-industrialization.component').then(m => m.SwitchLabIndustrializationComponent),
  },
  {
    path: 'lab/:moduleCode/:screen',
    canActivate: [productGuard, moduleScreenGuard],
    data: { products: ['SWITCHLAB'] },
    providers: [{ provide: SCREEN_LOADER, useValue: switchLabScreenLoader }],
    loadComponent: () => import('./features/dynamic-screen/dynamic-screen-host.component')
      .then(m => m.DynamicScreenHostComponent),
  },
];

export const switchLabRoutes: Routes = createPortalRoutes(SWITCHLAB_ROUTES);
