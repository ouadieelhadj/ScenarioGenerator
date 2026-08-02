import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import {
  DeploymentCatalog, DeploymentClient, DeploymentEnvironment, DeploymentExecution,
  DeploymentLicense, PreflightReport,
} from '../models/deployment.models';

@Injectable({ providedIn: 'root' })
export class DeploymentService {
  private http = inject(HttpClient);

  catalog(): Observable<DeploymentCatalog> {
    return this.http.get<DeploymentCatalog>(url.orchestrator(ENDPOINTS.deployments.catalog));
  }

  clients(): Observable<DeploymentClient[]> {
    return this.http.get<DeploymentClient[]>(url.orchestrator(ENDPOINTS.deployments.clients));
  }

  createClient(client: DeploymentClient): Observable<DeploymentClient> {
    return this.http.post<DeploymentClient>(url.orchestrator(ENDPOINTS.deployments.clients), client);
  }

  environments(clientId: number): Observable<DeploymentEnvironment[]> {
    const params = new HttpParams().set('clientId', clientId);
    return this.http.get<DeploymentEnvironment[]>(url.orchestrator(ENDPOINTS.deployments.environments), { params });
  }

  createEnvironment(environment: DeploymentEnvironment): Observable<DeploymentEnvironment> {
    return this.http.post<DeploymentEnvironment>(url.orchestrator(ENDPOINTS.deployments.environments), environment);
  }

  preflight(environmentId: number): Observable<PreflightReport> {
    return this.http.post<PreflightReport>(url.orchestrator(ENDPOINTS.deployments.preflight(environmentId)), {});
  }

  licenses(environmentId?: number): Observable<DeploymentLicense[]> {
    const params = environmentId ? new HttpParams().set('environmentId', environmentId) : undefined;
    return this.http.get<DeploymentLicense[]>(url.orchestrator(ENDPOINTS.deployments.licenses), { params });
  }

  createLicense(request: { environmentId: number; validFrom: string; validUntil: string; bundleVersion: string }): Observable<DeploymentLicense> {
    return this.http.post<DeploymentLicense>(url.orchestrator(ENDPOINTS.deployments.licenses), request);
  }

  approveLicense(id: string): Observable<DeploymentLicense> {
    return this.http.post<DeploymentLicense>(url.orchestrator(ENDPOINTS.deployments.approveLicense(id)), {});
  }

  executions(environmentId?: number): Observable<DeploymentExecution[]> {
    const params = environmentId ? new HttpParams().set('environmentId', environmentId) : undefined;
    return this.http.get<DeploymentExecution[]>(url.orchestrator(ENDPOINTS.deployments.executions), { params });
  }

  createExecution(request: { environmentId: number; action: string }): Observable<DeploymentExecution> {
    return this.http.post<DeploymentExecution>(url.orchestrator(ENDPOINTS.deployments.executions), request);
  }

  approveExecution(id: string): Observable<DeploymentExecution> {
    return this.http.post<DeploymentExecution>(url.orchestrator(ENDPOINTS.deployments.approveExecution(id)), {});
  }
}
