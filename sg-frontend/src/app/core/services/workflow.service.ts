import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { WorkflowRequestSummary } from '../models/workflow.models';
import { PORTAL_PRODUCT } from '../product/product.config';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private http = inject(HttpClient);
  private product = inject(PORTAL_PRODUCT);

  private endpoint(path: string): string {
    return this.product.code === 'MERCHANT_PORTAL' ? url.onboarding(path) : url.orchestrator(path);
  }

  myOperations(): Observable<WorkflowRequestSummary[]> {
    return this.http.get<WorkflowRequestSummary[]>(this.endpoint(ENDPOINTS.workflow.myOperations));
  }

  myApprovals(): Observable<WorkflowRequestSummary[]> {
    return this.http.get<WorkflowRequestSummary[]>(this.endpoint(ENDPOINTS.workflow.myApprovals));
  }
}
