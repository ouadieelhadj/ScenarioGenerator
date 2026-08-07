import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchLabEnvironmentReference, SwitchLabMtipSentinelRequest, SwitchLabOverview, SwitchLabPosExecution, SwitchLabPosScenarioDefinition, SwitchLabTraceEvent } from '../models/product-contracts.models';

@Injectable({ providedIn: 'root' })
export class SwitchLabOperationsService {
  private readonly http = inject(HttpClient);

  environments(): Observable<SwitchLabEnvironmentReference[]> {
    return this.http.get<SwitchLabEnvironmentReference[]>(url.orchestrator(ENDPOINTS.switchLab.environments));
  }

  overview(environmentId: string): Observable<SwitchLabOverview> {
    const params = new HttpParams().set('environmentId', environmentId);
    return this.http.get<SwitchLabOverview>(url.orchestrator(ENDPOINTS.switchLab.overview), { params });
  }

  traces(environmentId: string, limit = 100): Observable<SwitchLabTraceEvent[]> {
    const params = new HttpParams().set('environmentId', environmentId).set('limit', limit);
    return this.http.get<SwitchLabTraceEvent[]>(url.orchestrator(ENDPOINTS.switchLab.traces), { params });
  }

  posCatalog(): Observable<SwitchLabPosScenarioDefinition[]> {
    return this.http.get<SwitchLabPosScenarioDefinition[]>(url.orchestrator(ENDPOINTS.switchLab.pos.catalog));
  }

  posHistory(limit = 50): Observable<SwitchLabPosExecution[]> {
    return this.http.get<SwitchLabPosExecution[]>(url.orchestrator(ENDPOINTS.switchLab.pos.history), { params: new HttpParams().set('limit', limit) });
  }

  sendPosTransaction(request: Record<string, unknown>): Observable<SwitchLabPosExecution> {
    return this.http.post<SwitchLabPosExecution>(url.orchestrator(ENDPOINTS.switchLab.pos.transactions), request);
  }

  sendPosFieldMap(request: Record<string, unknown>): Observable<SwitchLabPosExecution> {
    return this.http.post<SwitchLabPosExecution>(url.orchestrator(ENDPOINTS.switchLab.pos.fieldMap), request);
  }

  repeatPos(terminalId: string, macEnabled: boolean): Observable<SwitchLabPosExecution> {
    const params = new HttpParams().set('terminalId', terminalId).set('macEnabled', macEnabled);
    return this.http.post<SwitchLabPosExecution>(url.orchestrator(ENDPOINTS.switchLab.pos.repeat), {}, { params });
  }

  startPosRki(confirm: boolean): Observable<SwitchLabPosExecution> {
    return this.http.post<SwitchLabPosExecution>(url.orchestrator(ENDPOINTS.switchLab.pos.rki), {}, { params: new HttpParams().set('confirm', confirm) });
  }

  confirmPosRki(): Observable<SwitchLabPosExecution> {
    return this.http.post<SwitchLabPosExecution>(url.orchestrator(ENDPOINTS.switchLab.pos.rkiConfirm), {});
  }

  runMtipSentinel(request: SwitchLabMtipSentinelRequest): Observable<SwitchLabPosExecution> {
    return this.http.post<SwitchLabPosExecution>(url.orchestrator(ENDPOINTS.switchLab.pos.sentinel), request);
  }
}
