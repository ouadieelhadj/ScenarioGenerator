import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MobileAuthService } from './mobile-auth.service';

export const mobileAuthGuard: CanActivateFn = () => {
  const auth = inject(MobileAuthService);
  return auth.authenticated() ? true : inject(Router).createUrlTree(['/login']);
};
