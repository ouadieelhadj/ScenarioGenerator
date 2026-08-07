import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import {
  CreateMerchantProspectRequest,
  MerchantActivationResponse,
  MerchantDossier,
  MerchantProspect,
} from '../models/merchant-onboarding.models';

@Injectable({ providedIn: 'root' })
export class MerchantOnboardingService {
  private readonly http = inject(HttpClient);

  activate(token: string, password: string): Observable<MerchantActivationResponse> {
    return this.http.post<MerchantActivationResponse>(
      url.orchestrator(ENDPOINTS.merchantPortal.activate), { token, password });
  }

  createProspect(request: CreateMerchantProspectRequest): Observable<MerchantProspect> {
    return this.http.post<MerchantProspect>(
      url.onboarding(ENDPOINTS.merchantPortal.prospects), request);
  }

  dossier(id: string): Observable<MerchantDossier> {
    return this.http.get<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.dossier(id)));
  }
}
