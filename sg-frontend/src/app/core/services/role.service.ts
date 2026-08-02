import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { PermissionSummary, RoleSummary } from '../models/role.models';

@Injectable({ providedIn: 'root' })
export class RoleService {
  private http = inject(HttpClient);

  findAll(): Observable<RoleSummary[]> {
    return this.http.get<RoleSummary[]>(url.orchestrator(ENDPOINTS.roles.base));
  }

  permissions(): Observable<PermissionSummary[]> {
    return this.http.get<PermissionSummary[]>(url.orchestrator(ENDPOINTS.roles.permissions));
  }
}
