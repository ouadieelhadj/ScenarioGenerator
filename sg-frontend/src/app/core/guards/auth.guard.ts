import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { CommercialConfirmationHandoffService } from '../auth/commercial-confirmation-handoff.service';

export const authGuard: CanActivateFn = (_route,state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  inject(CommercialConfirmationHandoffService).capture(state.url);
  if (auth.isAuthenticated()) return true;
  return router.createUrlTree(['/login']);
};
