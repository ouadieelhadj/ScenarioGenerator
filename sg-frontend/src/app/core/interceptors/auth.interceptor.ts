import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { baseUrl } from '../config/api.config';
import { apiTokenDestinationAllowed } from './api-token-destination';
import { AuthService } from '../auth/auth.service';
import { PERSONAL_ACCOUNT_PUBLIC_REQUEST } from '../services/personal-account-actions.service';

// Add the application bearer only to explicitly configured API destinations.
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if(req.context.get(PERSONAL_ACCOUNT_PUBLIC_REQUEST))return next(req.clone({headers:req.headers.delete('Authorization')}));
  const auth = inject(AuthService);
  const token = auth.getToken();
  const pageUrl=typeof window==='undefined'?'':window.location.href;
  const trusted=apiTokenDestinationAllowed(req.url,pageUrl,[baseUrl('orchestrator'),baseUrl('onboarding'),baseUrl('acquirer'),baseUrl('issuer')]);
  if (token && trusted) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
