import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { url } from '../config/api.config';
import { PortalCredit, PortalCreditCorrection, PortalSettlementReconciliation, PortalReconciliationMatch, ReconciliationFilter, ReconciliationPage, ReconciliationPermissions } from '../models/merchant-reconciliation.models';
@Injectable({ providedIn: 'root' })
export class MerchantReconciliationService {
  private readonly http = inject(HttpClient);
  private base(code: string) { return url.onboarding('/api/merchant-portal/v1/acquirers/' + encodeURIComponent(code) + '/reconciliation'); }
  access(code:string) {return this.http.get<{bankOperations:boolean}>(this.base(code)+'/access');}
  settlements(code: string, filter: ReconciliationFilter, page: number) {
    return this.http.get<ReconciliationPage<PortalSettlementReconciliation>>(this.base(code) + '/settlements', {params: this.params(filter, page)});
  }
  credits(code: string, filter: ReconciliationFilter, page: number) {
    return this.http.get<ReconciliationPage<PortalCredit>>(this.base(code) + '/credits', {params: this.params(filter, page)});
  }
  permissions(code: string, nodeId: string) { return this.http.get<ReconciliationPermissions>(this.base(code) + '/permissions', { params: { hierarchyNodeId: nodeId } }); }
  importStatement(code: string, file: File) {
    const data = new FormData(); data.append('file', file);
    return this.http.post<{created: number; alreadyPresent: number; documentDigest: string}>(this.base(code) + '/bank-statement', data);
  }
  propose(code: string, settlementId: string, creditId: string, justification: string) {
    return this.http.post<PortalReconciliationMatch>(this.base(code) + '/proposals', {settlementId, creditId, justification});
  }
  corrections(code: string, creditId: string) {
    return this.http.get<PortalCreditCorrection[]>(this.base(code)+'/credits/'+encodeURIComponent(creditId)+'/corrections');
  }
  proposeCorrection(code: string, credit: PortalCredit, requestId: string, file: File, justification: string) {
    const data = new FormData(); data.append('file',file); data.append('requestId',requestId); data.append('justification',justification);
    if (credit.revisionId) data.append('expectedRevisionId',credit.revisionId);
    return this.http.post<PortalCreditCorrection>(this.base(code)+'/credits/'+encodeURIComponent(credit.id)+'/corrections',data);
  }
  decideCorrection(code: string, correction: PortalCreditCorrection, approve: boolean, justification: string) {
    return this.http.post<PortalCreditCorrection>(this.base(code)+'/corrections/'+encodeURIComponent(correction.id)+'/decision',
      {approve,justification,expectedVersion:correction.version});
  }
  decide(code: string, match: PortalReconciliationMatch, approve: boolean, justification: string) {
    return this.http.post<PortalReconciliationMatch>(this.base(code) + '/proposals/' + encodeURIComponent(match.id) + '/decision',
      {approve, justification, expectedVersion: match.version});
  }
  private params(filter: ReconciliationFilter, page: number) {
    let params = new HttpParams().set('page', page).set('size', 50);
    for (const [key,value] of Object.entries(filter)) if (value) params = params.set(key, value);
    return params;
  }
}
