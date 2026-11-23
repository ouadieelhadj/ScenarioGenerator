import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import {
  AcquirerChangeRequest, AcquirerUserRole, PlatformAcquirer,
  PlatformAcquirerRequest, PlatformAcquirerUser,
} from '../models/merchant-onboarding.models';

@Injectable({ providedIn: 'root' })
export class AcquirerAdministrationService {
  private readonly http = inject(HttpClient);
  list(): Observable<PlatformAcquirer[]> { return this.http.get<PlatformAcquirer[]>(url.onboarding(ENDPOINTS.merchantPortal.acquirers)); }
  create(value: PlatformAcquirerRequest): Observable<PlatformAcquirer> { return this.http.post<PlatformAcquirer>(url.onboarding(ENDPOINTS.merchantPortal.acquirers), value); }
  update(id: string, value: PlatformAcquirerRequest): Observable<AcquirerChangeRequest> { return this.http.put<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirer(id)), value); }
  activate(id: string): Observable<AcquirerChangeRequest> { return this.http.post<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirerAction(id, 'activate')), null); }
  deactivate(id: string): Observable<AcquirerChangeRequest> { return this.http.post<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirerAction(id, 'deactivate')), null); }
  users(id: string): Observable<PlatformAcquirerUser[]> { return this.http.get<PlatformAcquirerUser[]>(url.onboarding(ENDPOINTS.merchantPortal.acquirerUsers(id))); }
  addUser(id: string, value: { identitySubject: string; login: string; role: AcquirerUserRole }): Observable<AcquirerChangeRequest> { return this.http.post<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirerUsers(id)), value); }
  updateUser(id: string, userId: string, value: { role: AcquirerUserRole; active: boolean }): Observable<AcquirerChangeRequest> { return this.http.put<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirerUser(id, userId)), value); }
  pending(): Observable<AcquirerChangeRequest[]> { return this.http.get<AcquirerChangeRequest[]>(url.onboarding(ENDPOINTS.merchantPortal.acquirerPendingChanges)); }
  approve(id: string): Observable<AcquirerChangeRequest> { return this.http.post<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirerChangeAction(id, 'approve')), null); }
  reject(id: string, reason: string): Observable<AcquirerChangeRequest> { return this.http.post<AcquirerChangeRequest>(url.onboarding(ENDPOINTS.merchantPortal.acquirerChangeAction(id, 'reject')), { reason }); }
}
