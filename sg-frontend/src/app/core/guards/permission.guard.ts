import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

// Usage : { path: 'x', canActivate: [permissionGuard], data: { permissions: ['CAMPAIGN_CREATE'] } }
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data?.['permissions'] as string[]) ?? [];
  if (required.length === 0 || auth.hasAnyPermission(required)) return true;
  router.navigate(['/forbidden']);
  return false;
};
