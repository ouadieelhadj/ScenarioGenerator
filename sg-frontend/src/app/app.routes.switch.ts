import { Routes } from '@angular/router';
import { productGuard } from './core/guards/product.guard';
import { moduleScreenGuard } from './core/guards/module-screen.guard';
import { createPortalRoutes } from './app.routes.shared';
import { SCREEN_LOADER } from './core/navigation/screen-loader';
import { switchScreenLoader } from './core/navigation/screen-registry.switch';

const SWITCH_ROUTES: Routes = [
  {
    path: 'product/acquiring',
    canActivate: [productGuard],
    data: { products: ['SWITCH'] },
    loadComponent: () => import('./features/switch-acquiring/switch-acquiring.component').then(m => m.SwitchAcquiringComponent),
  },
  ...[
    { path: 'product/issuing', domain: 'issuing', title: 'Issuing membre', description: 'Produits, contrats, cartes, interfaces et autorisations issuing.', icon: 'pi pi-credit-card' },
    { path: 'product/networks', domain: 'networks', title: 'Réseaux temps réel', description: 'Mastercard DMAS/SMS, SWAM et Visa Online côté membre.', icon: 'pi pi-sitemap' },
    { path: 'product/clearing', domain: 'clearing', title: 'Clearing, rapprochement & settlement', description: 'Mastercard DMCS, SWAM LIS et Visa Base II côté membre.', icon: 'pi pi-file-import' },
    { path: 'product/ecommerce', domain: 'ecommerce', title: 'E-commerce & 3DS membre', description: 'ACS, 3DS Server, preuves et autorisations e-commerce.', icon: 'pi pi-shopping-bag' },
    { path: 'product/industrialization', domain: 'industrialization', title: 'Industrialisation du Switch', description: 'Déploiement, licences, observabilité, audit et sauvegardes.', icon: 'pi pi-shield' },
  ].map(item => ({
    path: item.path,
    canActivate: [productGuard],
    data: { products: ['SWITCH'], domain: item.domain, title: item.title, description: item.description, icon: item.icon },
    loadComponent: () => import('./features/switch-member-domain/switch-member-domain.component').then(m => m.SwitchMemberDomainComponent),
  })),
  {
    path: 'product/transactions',
    canActivate: [productGuard],
    data: { products: ['SWITCH'], titleKey: 'menu.transactions', icon: 'pi pi-arrow-right-arrow-left' },
    loadComponent: () => import('./features/product-area/product-area.component').then(m => m.ProductAreaComponent),
  },
  {
    path: 'product/operations',
    canActivate: [productGuard],
    data: { products: ['SWITCH'], titleKey: 'menu.operations', icon: 'pi pi-desktop' },
    loadComponent: () => import('./features/product-area/product-area.component').then(m => m.ProductAreaComponent),
  },
  {
    path: 'product/interfaces',
    canActivate: [productGuard],
    data: { products: ['SWITCH'], titleKey: 'menu.interfaces', icon: 'pi pi-link' },
    loadComponent: () => import('./features/switch-interfaces/switch-interfaces.component').then(m => m.SwitchInterfacesComponent),
  },
  {
    path: 'modules/:moduleCode/:screen',
    canActivate: [productGuard, moduleScreenGuard],
    data: { products: ['SWITCH'] },
    providers: [{ provide: SCREEN_LOADER, useValue: switchScreenLoader }],
    loadComponent: () => import('./features/dynamic-screen/dynamic-screen-host.component')
      .then(m => m.DynamicScreenHostComponent),
  },
];

export const switchRoutes: Routes = createPortalRoutes(SWITCH_ROUTES);
