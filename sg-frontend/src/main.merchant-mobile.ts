import { bootstrapApplication } from '@angular/platform-browser';
import { MobileAppComponent } from './mobile/mobile-app.component';
import { mobileAppConfig } from './mobile/mobile-app.config';

bootstrapApplication(MobileAppComponent, mobileAppConfig)
  .catch(()=>{
    const root=document.querySelector('app-root');
    if(root)root.textContent='L’application ne peut pas démarrer. Vérifiez la configuration HTTPS auprès de votre administrateur.';
  });
