import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { FraudAlertView, FraudCaseView, FraudOverview } from '../models/product-contracts.models';

@Injectable({ providedIn: 'root' })
export class FraudWorkspaceService {
  private readonly http = inject(HttpClient);

  overview(workspace: 'SWITCH' | 'SWITCHLAB'): Observable<FraudOverview> {
    const endpoint = workspace === 'SWITCH'
      ? ENDPOINTS.switch.fraudOverview
      : ENDPOINTS.switchLab.fraudOverview;
    return this.http.get<FraudOverview>(url.orchestrator(endpoint));
  }

  alerts(workspace: 'SWITCH' | 'SWITCHLAB'): Observable<FraudAlertView[]> {
    return this.http.get<FraudAlertView[]>(url.orchestrator(this.platformPath(workspace, '/alerts')));
  }

  cases(workspace: 'SWITCH' | 'SWITCHLAB'): Observable<FraudCaseView[]> {
    return this.http.get<FraudCaseView[]>(url.orchestrator(this.platformPath(workspace, '/cases')));
  }

  private platformPath(workspace: 'SWITCH' | 'SWITCHLAB', suffix: string): string {
    return workspace === 'SWITCH'
      ? `/api/switch/v1/fraud/platform${suffix}`
      : `/api/switchlab/v1/fraud/platform${suffix}`;
  }}
