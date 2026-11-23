import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { url } from '../config/api.config';
import { PortalPage, PortalNode, PortalFinancialView, PortalFinancialRevision, PortalFinancialFilter, PortalFinancialTotal, PortalFinancialSummary, PortalSettlementComposition } from '../models/merchant-activity.models';

@Injectable({ providedIn: 'root' })
export class MerchantActivityService {
  private readonly http = inject(HttpClient);
  private base(acquirer: string): string {
    return url.onboarding('/api/merchant-portal/v1/acquirers/' + encodeURIComponent(acquirer));
  }
  hierarchy(acquirer: string, page = 0): Observable<PortalPage<PortalNode>> {
    return this.http.get<PortalPage<PortalNode>>(this.base(acquirer) + '/hierarchy', { params: { page, size: 200 } });
  }
  records(acquirer: string, filter: PortalFinancialFilter, page = 0): Observable<PortalPage<PortalFinancialView>> {
    return this.http.get<PortalPage<PortalFinancialView>>(this.base(acquirer) + '/financial-records', {
      params: this.params(filter).set('page', page).set('size', 50),
    });
  }
  totals(acquirer: string, filter: PortalFinancialFilter): Observable<PortalFinancialTotal[]> {
    return this.http.get<PortalFinancialTotal[]>(this.base(acquirer) + '/financial-records/totals', { params: this.params(filter) });
  }
  detail(acquirer: string, id: string): Observable<PortalFinancialView> {
    return this.http.get<PortalFinancialView>(this.base(acquirer) + '/financial-records/' + encodeURIComponent(id));
  }
  summary(acquirer: string, filter: PortalFinancialFilter): Observable<PortalFinancialSummary> {
    return this.http.get<PortalFinancialSummary>(this.base(acquirer) + '/financial-records/summary', { params: this.params(filter) });
  }
  related(acquirer: string, id: string, page = 0): Observable<PortalPage<PortalFinancialView>> {
    return this.http.get<PortalPage<PortalFinancialView>>(this.base(acquirer) + '/financial-records/' + encodeURIComponent(id) + '/related', {
      params: { page, size: 50 },
    });
  }
  history(acquirer: string, id: string, page = 0): Observable<PortalPage<PortalFinancialView>> {
    return this.http.get<PortalPage<PortalFinancialView>>(this.base(acquirer) + '/financial-records/' + encodeURIComponent(id) + '/history', {
      params: { page, size: 50 },
    });
  }
  revisions(acquirer: string,id: string,page=0): Observable<PortalPage<PortalFinancialRevision>> {
    return this.http.get<PortalPage<PortalFinancialRevision>>(this.base(acquirer)+'/financial-records/'+encodeURIComponent(id)+'/revisions',{params:{page,size:50}});
  }
  statement(acquirer: string, filter: PortalFinancialFilter, format: 'PDF' | 'XLSX'): Observable<Blob> {
    return this.http.get(this.base(acquirer) + '/statements/export', {
      params: this.params(filter).set('format', format), responseType: 'blob',
    });
  }
  composition(acquirer: string, id: string, page = 0): Observable<PortalSettlementComposition> {
    return this.http.get<PortalSettlementComposition>(this.base(acquirer) + '/financial-records/' + encodeURIComponent(id) + '/composition', {params: {page, size: 50}});
  }
  private params(filter: PortalFinancialFilter): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) if (value != null && value !== '') params = params.set(key, value);
    return params;
  }
}
