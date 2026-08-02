import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { WorkflowRequestSummary } from '../models/workflow.models';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private http = inject(HttpClient);

  myOperations(): Observable<WorkflowRequestSummary[]> {
    return this.http.get<WorkflowRequestSummary[]>(url.orchestrator(ENDPOINTS.workflow.myOperations));
  }

  myApprovals(): Observable<WorkflowRequestSummary[]> {
    return this.http.get<WorkflowRequestSummary[]>(url.orchestrator(ENDPOINTS.workflow.myApprovals));
  }
}
