import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { createAppConfig } from './app/app.config';
import { switchLabRoutes } from './app/app.routes.switchlab';
import { SWITCHLAB_PRODUCT } from './app/core/product/product.config';

bootstrapApplication(AppComponent, createAppConfig(SWITCHLAB_PRODUCT, switchLabRoutes))
  .catch(err => console.error(err));
