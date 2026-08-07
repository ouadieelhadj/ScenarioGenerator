import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { createAppConfig } from './app/app.config';
import { routes } from './app/app.routes';
import { LEGACY_PRODUCT } from './app/core/product/product.config';

bootstrapApplication(AppComponent, createAppConfig(LEGACY_PRODUCT, routes))
  .catch(err => console.error(err));
