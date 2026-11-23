import { inject } from '@angular/core';
import { CanActivateFn,Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** UI separation only: every dossier API still enforces its own identity and owner. */
export const merchantOnboardingGuard:CanActivateFn=()=>{
  const auth=inject(AuthService);const router=inject(Router);
  if(!auth.isAuthenticated())return router.createUrlTree(['/onboarding/login']);
  return ['MERCHANT','COMMERCANT'].includes(auth.user()?.role||'')?true:router.createUrlTree(['/forbidden']);
};
