import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Campaign, CampaignRequest, CampaignExecution } from '../models/campaign.models';
import { ENDPOINTS, url } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class CampaignService {
  private http = inject(HttpClient);
  private ep = ENDPOINTS.campaigns;

  findAll(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(url.orchestrator(this.ep.base));
  }

  findById(id: number): Observable<Campaign> {
    return this.http.get<Campaign>(url.orchestrator(this.ep.byId(id)));
  }

  create(req: CampaignRequest): Observable<Campaign> {
    return this.http.post<Campaign>(url.orchestrator(this.ep.base), req);
  }

  update(id: number, req: CampaignRequest): Observable<Campaign> {
    return this.http.put<Campaign>(url.orchestrator(this.ep.byId(id)), req);
  }

  delete(id: number): Observable<unknown> {
    return this.http.delete(url.orchestrator(this.ep.byId(id)));
  }

  run(id: number): Observable<{ campaignExecutionId: number }> {
    return this.http.post<{ campaignExecutionId: number }>(url.orchestrator(this.ep.run(id)), {});
  }

  findExecution(executionId: number): Observable<CampaignExecution> {
    return this.http.get<CampaignExecution>(url.orchestrator(this.ep.execution(executionId)));
  }

  findExecutionsByCampaign(id: number): Observable<CampaignExecution[]> {
    return this.http.get<CampaignExecution[]>(url.orchestrator(this.ep.executionsByCampaign(id)));
  }
}

