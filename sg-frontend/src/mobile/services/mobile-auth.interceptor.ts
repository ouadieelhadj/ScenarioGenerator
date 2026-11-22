import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { MobileAuthService } from './mobile-auth.service';
import { baseUrl } from '../../app/core/config/api.config';
import { mobileApiTokenAllowed } from './mobile-session-security';
import { shouldEndMobileSession } from './mobile-session-revocation';
import { PERSONAL_ACCOUNT_PUBLIC_REQUEST } from '../../app/core/services/personal-account-actions.service';

export const mobileAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(MobileAuthService);
  const router = inject(Router);
  const token = auth.token();
  const trusted = mobileApiTokenAllowed(request.url, window.location.href, baseUrl('orchestrator'), baseUrl('onboarding'));
  const publicAction=request.context.get(PERSONAL_ACCOUNT_PUBLIC_REQUEST);
  return next(publicAction?request.clone({headers:request.headers.delete('Authorization')}):token && trusted ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request).pipe(
    catchError(error => {
      if (!publicAction && error instanceof HttpErrorResponse && shouldEndMobileSession(error.status, trusted, token, auth.token())) {
        auth.logout();
        void router.navigate(['/login'], { replaceUrl: true });
      }
      return throwError(() => error);
    }),
  );
};
