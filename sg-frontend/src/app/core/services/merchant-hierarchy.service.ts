import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {url} from '../config/api.config';
import {PortalNode,PortalPage} from '../models/merchant-activity.models';
export type PortalScopeRole='ADMIN'|'MAKER'|'CHECKER'|'VIEWER'|'ACCOUNTING';
export interface PortalScopeGrant{id:string;identitySubject:string;hierarchyNodeId:string;role:PortalScopeRole;active:boolean;login?:string|null}
export interface PortalHierarchyPermissions{manageGrants:boolean;hierarchySourceManaged:boolean}
export interface PortalHierarchyIdentity{login:string;identitySubject:string}
@Injectable({providedIn:'root'})
export class MerchantHierarchyService{
  private readonly http=inject(HttpClient);
  private base(code:string):string{return url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(code));}
  nodes(code:string,page=0):Observable<PortalPage<PortalNode>>{return this.http.get<PortalPage<PortalNode>>(this.base(code)+'/hierarchy',{params:{page,size:50}});}
  permissions(code:string,node:string):Observable<PortalHierarchyPermissions>{return this.http.get<PortalHierarchyPermissions>(this.base(code)+'/hierarchy/'+encodeURIComponent(node)+'/permissions');}
  grants(code:string,node:string,page=0):Observable<PortalPage<PortalScopeGrant>>{return this.http.get<PortalPage<PortalScopeGrant>>(this.base(code)+'/grants',{params:{nodeId:node,page,size:50}});}
  identity(code:string,node:string,login:string):Observable<PortalHierarchyIdentity>{return this.http.get<PortalHierarchyIdentity>(this.base(code)+'/hierarchy/'+encodeURIComponent(node)+'/identity',{params:{login}});}
  grant(code:string,node:string,identity:PortalHierarchyIdentity,role:PortalScopeRole):Observable<PortalScopeGrant>{return this.http.post<PortalScopeGrant>(this.base(code)+'/grants',{hierarchyNodeId:node,identitySubject:identity.identitySubject,role});}
  revoke(code:string,grant:PortalScopeGrant):Observable<void>{return this.http.delete<void>(this.base(code)+'/grants/'+encodeURIComponent(grant.id));}
}
