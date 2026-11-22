import { catchError, map, Observable, throwError } from 'rxjs';
import type { MerchantDossier } from '../models/merchant-onboarding.models';
import type { MerchantOnboardingService } from './merchant-onboarding.service';

type DossierReader = Pick<MerchantOnboardingService, 'dossier' | 'myDossier' | 'verifiedDossier'>;

/** Personal routes bind the verified identity before reading a new dossier. */
export function dossierSource(service: DossierReader, id: string | null, verifiedResume: boolean): Observable<MerchantDossier> {
  if (!verifiedResume) return id ? service.dossier(id) : service.myDossier();
  return service.verifiedDossier().pipe(map(dossier => {
    if (id && dossier.id !== id) throw new Error('Dossier inaccessible.');
    return dossier;
  }));
}

/** Legacy accounts keep their existing read; a denial may require the stronger identity proof. */
export function mobileDossierSummary(service: DossierReader): Observable<MerchantDossier> {
  return service.myDossier().pipe(catchError(error =>
    error?.status === 403 ? service.verifiedDossier() : throwError(() => error)));
}
