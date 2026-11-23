import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { PermissionSummary, RoleSummary } from '../models/role.models';

export interface UserProfileSelection {userId:number;login:string;primaryRoleId:number|null;profileIds:number[];}

@Injectable({ providedIn: 'root' })
export class RoleService {
  private http = inject(HttpClient);

  findAll(): Observable<RoleSummary[]> {
    return this.http.get<RoleSummary[]>(url.orchestrator(ENDPOINTS.roles.base));
  }

  updatePermissions(id:number,expectedIds:number[],selectedIds:number[]):Observable<number[]>{
    return this.http.put<number[]>(url.orchestrator(ENDPOINTS.roles.base+'/'+id+'/permissions'),{expectedIds,selectedIds});
  }
  userProfiles(id:number):Observable<UserProfileSelection>{
    return this.http.get<UserProfileSelection>(url.orchestrator(ENDPOINTS.roles.base+'/users/'+id+'/profiles'));
  }
  updateUserProfiles(id:number,expectedIds:number[],selectedIds:number[]):Observable<UserProfileSelection>{
    return this.http.put<UserProfileSelection>(url.orchestrator(ENDPOINTS.roles.base+'/users/'+id+'/profiles'),{expectedIds,selectedIds});
  }
  permissions(): Observable<PermissionSummary[]> {
    return this.http.get<PermissionSummary[]>(url.orchestrator(ENDPOINTS.roles.permissions));
  }
}
