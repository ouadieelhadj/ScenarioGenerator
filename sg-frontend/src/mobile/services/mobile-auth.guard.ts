import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MobileAuthService } from './mobile-auth.service';
import { CommercialConfirmationHandoffService } from '../../app/core/auth/commercial-confirmation-handoff.service';
import { commercialAccess, reviewAccess } from './mobile-presentation';

export const mobileAuthGuard: CanActivateFn = async (_route,state) => {
  const auth = inject(MobileAuthService);
  const router = inject(Router);
  inject(CommercialConfirmationHandoffService).capture(state.url);
  return auth.token() || await auth.restore() ? true : router.createUrlTree(['/login']);
};
export const mobilePersonalAccountGuard:CanActivateFn=async()=>{
 const auth=inject(MobileAuthService),router=inject(Router);
 if(!auth.token()&&!await auth.restore())return router.createUrlTree(['/login']);
 return auth.user()?.role==='MERCHANT'?true:router.createUrlTree(['/home']);
};
export const mobileEmailSupportGuard:CanActivateFn=async()=>{
 const auth=inject(MobileAuthService),router=inject(Router);
 if(!auth.token()&&!await auth.restore())return router.createUrlTree(['/login']);
 const user=auth.user();return user?.role==='BACK_OFFICE'&&user.permissions.includes('ONBOARDING_PERSONAL_EMAIL_SUPPORT')?true:router.createUrlTree(['/home']);
};
export const mobileCommercialGuard:CanActivateFn=async()=>{
 const auth=inject(MobileAuthService),router=inject(Router);
 if(!auth.token()&&!await auth.restore())return router.createUrlTree(['/login']);
 return commercialAccess(auth.user())?true:router.createUrlTree(['/home']);
};
export const mobileReviewGuard:CanActivateFn=async()=>{
 const auth=inject(MobileAuthService),router=inject(Router);
 if(!auth.token()&&!await auth.restore())return router.createUrlTree(['/login']);
 return reviewAccess(auth.user())?true:router.createUrlTree(['/home']);
};
