import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MobileAuthService } from './mobile-auth.service';

export const mobileAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(MobileAuthService).token();
  return next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request);
};
