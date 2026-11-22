import { inject, Injectable } from '@angular/core';
import { HttpClient,HttpContext } from '@angular/common/http';
import { PERSONAL_ACCOUNT_PUBLIC_REQUEST } from './personal-account-actions.service';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import {
  CreateMerchantProspectRequest,
  MerchantActivationResponse,
  MerchantDossier,
  MerchantDossierUpdate,
  MerchantDossierV2,
  MerchantDossierV2Update,
  ProvisioningDestination,
  MerchantDocument,
  MerchantDocumentType,
  MerchantKycChecklist, MerchantMoroccoRules,
  MerchantProvisioningView,
  MerchantProspect,
  MerchantReferenceValue,
  Way4ExportCandidate,
  Way4BatchResult,
  FuturPaymentCandidate,
  AllowedAcquirer,
} from '../models/merchant-onboarding.models';

@Injectable({ providedIn: 'root' })
export class MerchantOnboardingService {
  private readonly http = inject(HttpClient);

  activate(token: string, password: string): Observable<MerchantActivationResponse> {
    return this.http.post<MerchantActivationResponse>(
      url.orchestrator(ENDPOINTS.merchantPortal.activate), { token, password });
  }

  allowedAcquirers(): Observable<AllowedAcquirer[]> {
    return this.http.get<AllowedAcquirer[]>(url.onboarding(ENDPOINTS.merchantPortal.allowedAcquirers));
  }

  createProspect(request: CreateMerchantProspectRequest): Observable<MerchantProspect> {
    return this.http.post<MerchantProspect>(
      url.onboarding(ENDPOINTS.merchantPortal.prospects), request);
  }
  createPersonalProspect(id:string,request:{email:string;acquirerCode:string;consent:boolean}):Observable<PersonalProspectAccepted>{
    return this.http.post<PersonalProspectAccepted>(url.onboarding('/api/merchant-onboarding/v2/personal-prospects'),request,{headers:{'Idempotency-Key':id}});
  }
  personalProspectTracking(id:string):Observable<PersonalProspectTracking>{
    return this.http.get<PersonalProspectTracking>(url.onboarding('/api/merchant-onboarding/v2/personal-prospects/'+encodeURIComponent(id)));
  }

  dossier(id: string): Observable<MerchantDossier> {
    return this.http.get<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.dossier(id)));
  }

  myDossier(): Observable<MerchantDossier> {
    return this.http.get<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.myDossier));
  }
  verifiedDossier(): Observable<MerchantDossier> {
    return this.http.get<MerchantDossier>(url.onboarding('/api/merchant-onboarding/v1/dossiers/verified-mine'));
  }
  publicAcquirers(): Observable<Array<{code:string;name:string}>> {
    return this.http.get<Array<{code:string;name:string}>>(url.onboarding('/api/merchant-onboarding/public/v1/acquirers'),{context:new HttpContext().set(PERSONAL_ACCOUNT_PUBLIC_REQUEST,true)});
  }
  publicAcquirer(code:string):Observable<{code:string;name:string}>{
    return this.http.get<{code:string;name:string}>(url.onboarding('/api/merchant-onboarding/public/v1/acquirers/'+encodeURIComponent(code)),{context:new HttpContext().set(PERSONAL_ACCOUNT_PUBLIC_REQUEST,true)});
  }
  registerPublic(id:string,request:{email:string;acquirerCode:string;consent:boolean},entryCode?:string):Observable<{status:string}>{
    const path=entryCode===undefined?'/api/merchant-onboarding/public/v2/registrations':'/api/merchant-onboarding/public/v2/acquirers/'+encodeURIComponent(entryCode)+'/registrations';
    return this.http.post<{status:string}>(url.onboarding(path),request,{context:new HttpContext().set(PERSONAL_ACCOUNT_PUBLIC_REQUEST,true),headers:{'Idempotency-Key':id}});
  }
  publicRegistrations(acquirer:string):Observable<PublicRegistrationTracking[]>{
    return this.http.get<PublicRegistrationTracking[]>(url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(acquirer)+'/registrations'));
  }
  retryRegistrationMail(acquirer:string,id:string,reason:string,version:number):Observable<void>{
    return this.http.post<void>(url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(acquirer)+'/registrations/'+encodeURIComponent(id)+'/retry-mail'),{reason,version});
  }
  renewRegistrationInvitation(acquirer:string,id:string,requestId:string,reason:string,version:number):Observable<void>{
    return this.http.post<void>(url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(acquirer)+'/registrations/'+encodeURIComponent(id)+'/renew-invitation'),{requestId,reason,version});
  }

  updateDossier(id: string, request: MerchantDossierUpdate): Observable<MerchantDossier> {
    return this.http.put<MerchantDossier>(url.onboarding(ENDPOINTS.merchantPortal.dossier(id)), request);
  }

  selectDestination(id: string, destination: ProvisioningDestination): Observable<MerchantDossier> {
    return this.http.put<MerchantDossier>(
      url.onboarding(`${ENDPOINTS.merchantPortal.dossier(id)}/destination`), { destination });
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
  kycRequirements(id:string,selection?:{merchantType:string;legalNature:string;country:string}):Observable<MerchantKycChecklist>{
    const params=new URLSearchParams();
    if(selection){params.set('preview','true');if(selection.merchantType)params.set('merchantType',selection.merchantType);if(selection.legalNature)params.set('legalNature',selection.legalNature);if(selection.country)params.set('country',selection.country);}
    return this.http.get<MerchantKycChecklist>(url.onboarding('/api/merchant-onboarding/v2/dossiers/'+encodeURIComponent(id)+'/kyc-requirements')+(params.size?'?'+params.toString():''));
  }

  moroccoRules(id:string):Observable<MerchantMoroccoRules>{
    return this.http.get<MerchantMoroccoRules>(url.onboarding('/api/merchant-onboarding/v2/dossiers/'+encodeURIComponent(id)+'/morocco-rules'));
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

  way4Candidates(): Observable<Way4ExportCandidate[]> {
    return this.http.get<Way4ExportCandidate[]>(url.onboarding(ENDPOINTS.merchantPortal.way4Candidates));
  }

  generateWay4Batch(caseIds: string[]): Observable<Way4BatchResult> {
    const operationId = crypto.randomUUID();
    return this.http.post<Way4BatchResult>(url.onboarding(ENDPOINTS.merchantPortal.way4Batches), { caseIds }, {
      headers: {
        'X-Correlation-ID': `portal-way4-batch-${operationId}`,
      },
    });
  }

  futurPaymentCandidates(): Observable<FuturPaymentCandidate[]> {
    return this.http.get<FuturPaymentCandidate[]>(url.onboarding(ENDPOINTS.merchantPortal.futurPaymentCandidates));
  }

  resendFuturPayment(eventId: string): Observable<{ eventId: string; status: string; attempts: number }> {
    return this.http.post<{ eventId: string; status: string; attempts: number }>(
      url.onboarding(ENDPOINTS.merchantPortal.futurPaymentResend(eventId)), null, {
        headers: { 'X-Correlation-ID': `merchant-portal-resend-${crypto.randomUUID()}` },
      });
  }
}

export interface PublicRegistrationTracking {
  id:string;state:string;createdAt:string;expiresAt:string|null;identityAttempts:number;version:number;renewable:boolean;invitationGeneration:number;
}
export interface PersonalProspectAccepted {id:string;status:string;}
export interface PersonalProspectTracking {
  id:string;acquirerCode:string;state:string;caseId:string|null;caseReference:string|null;personalId:string|null;
  accountStatus:string|null;expiresAt:string|null;version:number;
}
