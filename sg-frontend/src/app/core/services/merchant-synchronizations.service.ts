import {inject,Injectable} from '@angular/core';import {HttpClient} from '@angular/common/http';import {Observable} from 'rxjs';
import {url} from '../config/api.config';import {PortalPage,PortalRecordKind} from '../models/merchant-activity.models';
export type PortalSyncFamily=PortalRecordKind|'HIERARCHY'|'CHARGEBACK';
export interface PortalSourceBinding{id:string;sourceSystem:string;kind:PortalSyncFamily;mappingVersion:string}
export interface PortalSyncSettings{sourceSystem:string;kind:PortalSyncFamily;bindingId:string;mappingVersion:string;enabled:boolean;pageSize:number;pollSeconds:number;timeoutSeconds:number;leaseSeconds:number;freshnessSeconds:number}
export interface PortalSyncProfile{id:string;settings:PortalSyncSettings;activeRunId:string|null;lastCompletedAt:string|null;lastCleanCompletedAt:string|null;nextPollAt:string;lastFailureCode:string|null;freshness:string;unresolvedRejections:boolean;version:number}
export interface PortalSyncRun{id:string;startedAt:string;finishedAt:string|null;state:string;failureCode:string|null;pages:number;received:number;accepted:number;changed:number;rejected:number;repairSourcePageId:string|null}
export interface PortalSyncRejection{id:string;pageId:string;rowNumber:number;reason:string;createdAt:string;resolvedAt:string|null}
export interface PortalSyncPermissions{manageProfiles:boolean;operate:boolean}
export interface PortalSourceFreshness{sourceSystem:string;kind:PortalSyncFamily;lastCompletedAt:string|null;freshness:string;lastAttemptFailed:boolean}
@Injectable({providedIn:'root'}) export class MerchantSynchronizationsService{
  private readonly http=inject(HttpClient);private base(code:string):string{return url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(code)+'/synchronizations');}
  permissions(c:string):Observable<PortalSyncPermissions>{return this.http.get<PortalSyncPermissions>(this.base(c)+'/permissions');}
  bindings(c:string):Observable<PortalSourceBinding[]>{return this.http.get<PortalSourceBinding[]>(this.base(c)+'/bindings');}
  profiles(c:string,p=0):Observable<PortalPage<PortalSyncProfile>>{return this.http.get<PortalPage<PortalSyncProfile>>(this.base(c),{params:{page:p,size:50}});}
  configure(c:string,s:PortalSyncSettings):Observable<PortalSyncProfile>{return this.http.post<PortalSyncProfile>(this.base(c),s);}
  start(c:string,id:string):Observable<{runId:string}>{return this.http.post<{runId:string}>(this.base(c)+'/'+encodeURIComponent(id)+'/start',{});}
  stop(c:string,id:string):Observable<void>{return this.http.post<void>(this.base(c)+'/'+encodeURIComponent(id)+'/stop',{});}
  retryPage(c:string,id:string,page:string):Observable<{runId:string}>{return this.http.post<{runId:string}>(this.base(c)+'/'+encodeURIComponent(id)+'/pages/'+encodeURIComponent(page)+'/retry',{});}
  runs(c:string,id:string,p=0):Observable<PortalPage<PortalSyncRun>>{return this.http.get<PortalPage<PortalSyncRun>>(this.base(c)+'/'+encodeURIComponent(id)+'/runs',{params:{page:p,size:50}});}
  rejections(c:string,id:string,p=0):Observable<PortalPage<PortalSyncRejection>>{return this.http.get<PortalPage<PortalSyncRejection>>(this.base(c)+'/'+encodeURIComponent(id)+'/rejections',{params:{page:p,size:50}});}
  freshness(c:string):Observable<PortalSourceFreshness[]>{return this.http.get<PortalSourceFreshness[]>(url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(c)+'/financial-records/freshness'));}
}
