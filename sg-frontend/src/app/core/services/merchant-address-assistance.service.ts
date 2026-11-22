import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { url } from '../config/api.config';
import { AddressPrediction, AddressSuggestion } from '../models/merchant-address-selection';

export interface AddressAssistanceCapability { available: boolean; provider: string; }
@Injectable({ providedIn: 'root' })
export class MerchantAddressAssistanceService {
  private readonly http = inject(HttpClient);
  private path(caseId: string): string {
    return url.onboarding('/api/merchant-onboarding/v2/dossiers/' + encodeURIComponent(caseId) + '/address-assistance');
  }
  capability(caseId: string): Observable<AddressAssistanceCapability> {
    return this.http.get<AddressAssistanceCapability>(this.path(caseId));
  }
  search(caseId: string, query: string, country: string, sessionId: string): Observable<{ suggestions: AddressSuggestion[]; predictions?: AddressPrediction[] }> {
    // No dossier, KYC attachment, token, bank reference or identity is in the provider payload.
    // POST keeps the address search out of access-log query strings.
    return this.http.post<{ suggestions: AddressSuggestion[]; predictions?: AddressPrediction[] }>(this.path(caseId), { query, country, sessionId });
  }
  resolve(caseId: string, id: string, country: string, sessionId: string): Observable<AddressSuggestion> {
    return this.http.post<AddressSuggestion>(this.path(caseId) + '/selection', { id, country, sessionId });
  }
}
