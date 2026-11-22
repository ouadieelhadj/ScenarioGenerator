import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { mobileAuthInterceptor } from './services/mobile-auth.interceptor';
import { mobileRoutes } from './mobile.routes';
import { MobileNativeGatewayService,mobileNativeGatewayInterceptor } from './services/mobile-native-gateway.service';

export const mobileAppConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideIonicAngular(),
    provideRouter(mobileRoutes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([mobileAuthInterceptor,mobileNativeGatewayInterceptor])),
    {provide:APP_INITIALIZER,multi:true,deps:[MobileNativeGatewayService],
      useFactory:(gateway:MobileNativeGatewayService)=>()=>gateway.initialize()},
  ],
};
