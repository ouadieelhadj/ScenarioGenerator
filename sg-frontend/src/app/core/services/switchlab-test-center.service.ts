import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchLabCampaign, SwitchLabCampaignReport, SwitchLabCampaignRequest, SwitchLabCertificationManifest, SwitchLabEvidence, SwitchLabEvidenceRequest, SwitchLabProfileCapability, SwitchLabTestCatalogItem } from '../models/product-contracts.models';

@Injectable({ providedIn: 'root' })
export class SwitchLabTestCenterService {
  private readonly http = inject(HttpClient);
  private readonly ep = ENDPOINTS.switchLab.testCenter;
  catalog(): Observable<SwitchLabTestCatalogItem[]> { return this.http.get<SwitchLabTestCatalogItem[]>(url.orchestrator(this.ep.catalog)); }
  profiles(): Observable<SwitchLabProfileCapability[]> { return this.http.get<SwitchLabProfileCapability[]>(url.orchestrator(this.ep.profiles)); }
  campaigns(): Observable<SwitchLabCampaign[]> { return this.http.get<SwitchLabCampaign[]>(url.orchestrator(this.ep.campaigns)); }
  createCampaign(request: SwitchLabCampaignRequest): Observable<SwitchLabCampaign> { return this.http.post<SwitchLabCampaign>(url.orchestrator(this.ep.campaigns), request); }
  runCampaign(id: string, environmentId: string): Observable<SwitchLabCampaignReport> { return this.http.post<SwitchLabCampaignReport>(url.orchestrator(this.ep.runCampaign(id)), {}, { params: new HttpParams().set('environmentId', environmentId) }); }
  reports(): Observable<SwitchLabCampaignReport[]> { return this.http.get<SwitchLabCampaignReport[]>(url.orchestrator(this.ep.reports)); }
  exportReport(id: string, format: 'PDF' | 'XLSX'): Observable<Blob> { return this.http.get(url.orchestrator(this.ep.exportReport(id)), { params: new HttpParams().set('format', format), responseType: 'blob' }); }
  evidence(): Observable<SwitchLabEvidence[]> { return this.http.get<SwitchLabEvidence[]>(url.orchestrator(this.ep.evidence)); }
  importEvidence(request: SwitchLabEvidenceRequest): Observable<SwitchLabEvidence> { return this.http.post<SwitchLabEvidence>(url.orchestrator(this.ep.evidence), request); }
  analyzeCertification(manifest: SwitchLabCertificationManifest): Observable<SwitchLabEvidence> { return this.http.post<SwitchLabEvidence>(url.orchestrator(this.ep.analyzeCertification), manifest); }
}
