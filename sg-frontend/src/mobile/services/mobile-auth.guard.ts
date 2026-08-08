import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MobileAuthService } from './mobile-auth.service';

export const mobileAuthGuard: CanActivateFn = async () => {
  const auth = inject(MobileAuthService);
  const router = inject(Router);
  return auth.authenticated() || await auth.restore() ? true : router.createUrlTree(['/login']);
};
