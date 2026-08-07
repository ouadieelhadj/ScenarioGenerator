import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { createAppConfig } from './app/app.config';
import { merchantPortalRoutes } from './app/app.routes.merchant-portal';
import { MERCHANT_PORTAL_PRODUCT } from './app/core/product/product.config';

bootstrapApplication(AppComponent, createAppConfig(MERCHANT_PORTAL_PRODUCT, merchantPortalRoutes))
  .catch(err => console.error(err));
