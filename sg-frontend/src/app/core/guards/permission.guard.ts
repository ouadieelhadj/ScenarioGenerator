import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

// Usage : { path: 'x', canActivate: [permissionGuard], data: { permissions: ['CAMPAIGN_CREATE'] } }
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const requiredPermissions = (route.data?.['permissions'] as string[]) ?? [];
  const requiredRoles = (route.data?.['roles'] as string[]) ?? [];
  const hasPermission = requiredPermissions.length > 0 && auth.hasAnyPermission(requiredPermissions);
  const hasRole = requiredRoles.length > 0 && requiredRoles.some(role => auth.hasRole(role));
  if ((requiredPermissions.length === 0 && requiredRoles.length === 0) || hasPermission || hasRole) return true;
  router.navigate(['/forbidden']);
  return false;
};
