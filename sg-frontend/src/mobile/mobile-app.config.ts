import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { mobileAuthInterceptor } from './services/mobile-auth.interceptor';
import { mobileRoutes } from './mobile.routes';

export const mobileAppConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideIonicAngular({ mode: 'md' }),
    provideRouter(mobileRoutes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([mobileAuthInterceptor])),
  ],
};
