import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PORTAL_PRODUCT, PortalProductCode } from '../product/product.config';

/** Refuse une route qui n'appartient pas au produit Angular courant. */
export const productGuard: CanActivateFn = route => {
  const product = inject(PORTAL_PRODUCT);
  const router = inject(Router);
  const allowedProducts = (route.data?.['products'] as PortalProductCode[] | undefined) ?? [];

  return allowedProducts.length === 0 || allowedProducts.includes(product.code)
    ? true
    : router.createUrlTree(['/forbidden']);
};
