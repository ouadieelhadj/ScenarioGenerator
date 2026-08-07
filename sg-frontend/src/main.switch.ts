import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { createAppConfig } from './app/app.config';
import { switchRoutes } from './app/app.routes.switch';
import { SWITCH_PRODUCT } from './app/core/product/product.config';

bootstrapApplication(AppComponent, createAppConfig(SWITCH_PRODUCT, switchRoutes))
  .catch(err => console.error(err));
