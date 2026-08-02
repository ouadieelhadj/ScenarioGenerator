import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { NavigationService } from '../services/navigation.service';

/** Refuse tout écran dynamique absent de la navigation effective de l'utilisateur. */
export const moduleScreenGuard: CanActivateFn = route => {
  const navigation = inject(NavigationService);
  const router = inject(Router);
  const moduleCode = route.paramMap.get('moduleCode') ?? '';
  const screenCode = route.paramMap.get('screen') ?? '';

  return navigation.ensureLoaded().pipe(map(() =>
    navigation.findScreen(moduleCode, screenCode) ? true : router.createUrlTree(['/forbidden'])
  ));
};
