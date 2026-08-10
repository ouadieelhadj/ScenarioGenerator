import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import {
  CreateMerchantProspectRequest,
  MerchantActivationResponse,
  MerchantDossier,
  MerchantDossierUpdate,
  MerchantDossierV2,
  MerchantDossierV2Update,
  MerchantDocument,
  MerchantDocumentType,
  MerchantProvisioningView,
  MerchantProspect,
  MerchantReferenceValue,
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

  myDossier(): Observable<MerchantDossier> {
    return this.http.get<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.myDossier));
  }

  updateDossier(id: string, request: MerchantDossierUpdate): Observable<MerchantDossier> {
    return this.http.put<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.dossier(id)), request);
  }

  dossierV2(id: string): Observable<MerchantDossierV2> {
    return this.http.get<MerchantDossierV2>(url.onboarding(ENDPOINTS.merchantPortal.dossierV2(id)));
  }

  updateDossierV2(id: string, request: MerchantDossierV2Update): Observable<MerchantDossierV2> {
    return this.http.put<MerchantDossierV2>(
      url.onboarding(ENDPOINTS.merchantPortal.dossierV2(id)), request);
  }

  references(category: string): Observable<MerchantReferenceValue[]> {
    return this.http.get<MerchantReferenceValue[]>(
      url.onboarding(ENDPOINTS.merchantPortal.referencesV2(category)));
  }

  documents(id: string): Observable<MerchantDocument[]> {
    return this.http.get<MerchantDocument[]>(url.onboarding(ENDPOINTS.merchantPortal.documents(id)));
  }

  uploadDocument(id: string, type: MerchantDocumentType, file: File): Observable<MerchantDocument> {
    const body = new FormData();
    body.append('type', type);
    body.append('file', file, file.name);
    return this.http.post<MerchantDocument>(url.onboarding(ENDPOINTS.merchantPortal.documentFiles(id)), body);
  }

  submitKyc(id: string): Observable<MerchantDossier> {
    return this.http.post<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.submitKyc(id)), null);
  }

  submit(id: string): Observable<unknown> {
    return this.http.post(url.onboarding(ENDPOINTS.merchantPortal.submit(id)), null);
  }

  reviewQueue(): Observable<MerchantDossier[]> {
    return this.http.get<MerchantDossier[]>(url.onboarding(ENDPOINTS.merchantPortal.reviewQueue));
  }

  reviewDossier(id: string): Observable<MerchantDossier> {
    return this.http.get<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.reviewDossier(id)));
  }

  reviewDocuments(id: string): Observable<MerchantDocument[]> {
    return this.http.get<MerchantDocument[]>(url.onboarding(ENDPOINTS.merchantPortal.reviewDocuments(id)));
  }

  reviewDocumentContent(documentId: string): Observable<Blob> {
    return this.http.get(url.onboarding(ENDPOINTS.merchantPortal.reviewDocumentContent(documentId)), {
      responseType: 'blob',
    });
  }

  reviewDocument(documentId: string, accepted: boolean, reason: string | null): Observable<MerchantDocument> {
    return this.http.post<MerchantDocument>(url.onboarding(ENDPOINTS.merchantPortal.reviewDocument(documentId)), { accepted, reason });
  }

  validateKyc(id: string): Observable<MerchantDossier> {
    return this.http.post<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.validateKyc(id)), null);
  }

  requestComplements(id: string, reason: string): Observable<MerchantDossier> {
    return this.http.post<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.kycComplements(id)), { reason });
  }

  rejectKyc(id: string, reason: string): Observable<MerchantDossier> {
    return this.http.post<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.rejectKyc(id)), { reason });
  }

  provision(id: string, mode: 'IMMEDIATE' | 'BATCH'): Observable<MerchantProvisioningView> {
    return this.http.post<MerchantProvisioningView>(url.onboarding(ENDPOINTS.merchantPortal.provision(id, mode)), null, {
      headers: { 'X-Correlation-ID': `merchant-portal-${crypto.randomUUID()}` },
    });
  }

  runBatch(): Observable<MerchantProvisioningView[]> {
    return this.http.post<MerchantProvisioningView[]>(url.onboarding(`${ENDPOINTS.merchantPortal.runBatch}?limit=100&retryFailed=false`), null, {
      headers: { 'X-Correlation-ID': `merchant-portal-batch-${crypto.randomUUID()}` },
    });
  }
}
