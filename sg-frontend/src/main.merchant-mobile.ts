import { bootstrapApplication } from '@angular/platform-browser';
import { MobileAppComponent } from './mobile/mobile-app.component';
import { mobileAppConfig } from './mobile/mobile-app.config';

bootstrapApplication(MobileAppComponent, mobileAppConfig)
  .catch(error => console.error(error));
