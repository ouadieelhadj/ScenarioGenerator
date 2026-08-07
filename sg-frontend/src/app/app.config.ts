import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER } from '@angular/core';
import { provideRouter, Routes } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeng/themes/aura';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { LanguageService } from './core/i18n/language.service';
import { PORTAL_PRODUCT, PortalProductConfig } from './core/product/product.config';

export function createAppConfig(product: PortalProductConfig, routes: Routes): ApplicationConfig {
  return {
  providers: [
    { provide: PORTAL_PRODUCT, useValue: product },
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: { preset: Aura, options: { darkModeSelector: '[data-theme="dark"]' } },
    }),
    provideTranslateService({
      loader: provideTranslateHttpLoader({ prefix: './assets/i18n/', suffix: '.json' }),
      fallbackLang: 'fr',
    }),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: (lang: LanguageService) => () => lang.init(),
      deps: [LanguageService],
    },
  ],
  };
}
