import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { FraudAlertView, FraudCaseView, FraudDecisionPolicy, FraudLabScenarioResult, FraudOperationsDashboard, FraudOverview, FraudStory } from '../models/product-contracts.models';

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

  dashboard(): Observable<FraudOperationsDashboard> {
    return this.http.get<FraudOperationsDashboard>(url.orchestrator(this.platformPath('SWITCH', '/operations/dashboard')));
  }

  story(assessmentId: string): Observable<FraudStory> {
    return this.http.get<FraudStory>(url.orchestrator(this.platformPath('SWITCH', `/risk/assessments/${encodeURIComponent(assessmentId)}/story`)));
  }

  decisionPolicy(): Observable<FraudDecisionPolicy> {
    return this.http.get<FraudDecisionPolicy>(url.orchestrator(this.platformPath('SWITCH', '/decision-policy')));
  }

  updateDecisionPolicy(policy: Omit<FraudDecisionPolicy, 'updatedAt'>): Observable<FraudDecisionPolicy> {
    return this.http.put<FraudDecisionPolicy>(url.orchestrator(this.platformPath('SWITCH', '/decision-policy')), policy);
  }

  runLabScenario(scenario: string, transactionCount: number): Observable<FraudLabScenarioResult> {
    return this.http.post<FraudLabScenarioResult>(url.orchestrator('/api/switchlab/v1/fraud/gateway/lab/scenarios:run'), { scenario, transactionCount });
  }

  private platformPath(workspace: 'SWITCH' | 'SWITCHLAB', suffix: string): string {
    return workspace === 'SWITCH'
      ? `/api/switch/v1/fraud/platform${suffix}`
      : `/api/switchlab/v1/fraud/platform${suffix}`;
  }}
