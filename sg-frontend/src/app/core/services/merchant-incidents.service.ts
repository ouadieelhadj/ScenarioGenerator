import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { url } from '../config/api.config';
import { IncidentAttachmentPolicy } from './incident-attachment-policy';
import { IncidentReference,IncidentReferenceKind,IncidentReferenceOption } from './incident-business-references';

export type IncidentState = 'DRAFT'|'OPEN'|'IN_PROGRESS'|'WAITING_MERCHANT'|'WAITING_THIRD_PARTY'|'RESOLVED'|'CLOSED'|'CANCELLED';
export type IncidentAction = 'EDIT_DRAFT'|'SUBMIT'|'REPLY'|'INTERNAL_NOTE'|'TAKE'|'ASSIGN'|'QUALIFY'|'WAIT_MERCHANT'|'WAIT_THIRD_PARTY'|'RESOLVE'|'CONFIRM'|'CLOSE'|'REOPEN'|'CANCEL'|'LINK'|'BIND_MERCHANT';
export interface IncidentContext { dossierId: string|null; nodeId: string|null; }
export interface IncidentView {
 id:string;reference:string;context:IncidentContext;author:string;authorKind:'MERCHANT'|'COMMERCIAL';channel:string;
 category:string;subject:string;description:string;impact:string;status:IncidentState;priority:string;teamId:string|null;
 assignee:string|null;createdAt:string;openedAt:string|null;updatedAt:string;firstResponseAt:string|null;resolvedAt:string|null;
 version:number;actions:IncidentAction[];permissions:string[];fields?:Record<string,string>;
}
export interface IncidentMessage {id:string;author:string;internal:boolean;text:string;createdAt:string;}
export interface IncidentAttachment {id:string;contentType:string;scanState:'QUARANTINED'|'CLEAN'|'REJECTED';createdAt:string;}
export interface IncidentCategory {code:string;label:string;fields_json:string;parent_code?:string|null;parent_label?:string|null;}
export interface IncidentTarget {id:string;label:string;members:Array<{subject:string;label:string}>;}
export interface IncidentMetrics {ageSeconds:number;firstResponseBusinessSeconds:number|null;resolutionBusinessSeconds:number|null;firstResponseTargetSeconds:number|null;resolutionTargetSeconds:number|null;firstResponseOverdue:boolean;resolutionOverdue:boolean;calendarConfigured:boolean;updateBusinessSeconds?:number|null;updateTargetSeconds?:number|null;updateOverdue?:boolean;}
export interface IncidentSummary {volume:number;active:number;unassigned:number;reopenings:number;averageActiveAgeWallSeconds:number|null;averageFirstResponseWallSeconds:number|null;averageResolutionWallSeconds:number|null;}
export interface IncidentNotice {version:number;event_type:string;state:string;failure_code:string|null;subject:string|null;recipient_fingerprint:string|null;attempt_id:string|null;delivery_state:string|null;updated_at:string|null;}
export interface IncidentHistory {version:number;action:string;actor:string;reason:string|null;createdAt:string;changes:{before?:Record<string,unknown>;after?:Record<string,unknown>};}
export interface IncidentCollectiveHead {id:string;subject:string;state:'OPEN'|'CLOSED';version:number;updatedAt:string;}
export interface IncidentBroadcast {id:string;preparedVersion:number;text:string;state:'READY'|'STALE'|'SENT';targets:Array<{incidentId:string;expectedVersion:number}>;author:string;createdAt:string;}
export interface IncidentCollective {id:string;subject:string;description:string;state:'OPEN'|'CLOSED';version:number;members:Array<{incidentId:string;reference:string;subject:string;status:string;version:number}>;history:Array<{version:number;action:string;actor:string;reason:string|null;createdAt:string}>;broadcasts:IncidentBroadcast[];canSend:boolean;}
export interface IncidentCreation {requestId:string;context:IncidentContext;category:string;subject:string;description:string;impact:string;fields:Record<string,string>;draft:boolean;references?:IncidentReference[];}
export type IncidentDraftEdit=Omit<IncidentCreation,'context'|'draft'>&{expectedVersion:number};
@Injectable({providedIn:'root'})
export class MerchantIncidentsService {
 private readonly http=inject(HttpClient);
 private base(code:string,mobile:boolean){return url.onboarding(`/api/${mobile?'merchant-onboarding':'merchant-portal'}/v1/acquirers/${encodeURIComponent(code)}/incidents`);}
 list(code:string,mobile:boolean,offset=0,status='',filters:Record<string,string>={}){return this.http.get<{items:IncidentView[];total:number}>(this.base(code,mobile),{params:{offset,limit:25,...(status?{status}:{}),...Object.fromEntries(Object.entries(filters).filter(([,value])=>value!==''))}});}
 catalog(code:string,mobile:boolean){return this.http.get<IncidentCategory[]>(this.base(code,mobile)+'/catalog');}
 attachmentPolicy(code:string,mobile:boolean){return this.http.get<IncidentAttachmentPolicy>(this.base(code,mobile)+'/attachment-policy');}
 contexts(code:string,mobile:boolean,offset=0){return this.http.get<Array<{context:IncidentContext;label:string}>>(this.base(code,mobile)+'/contexts',{params:{offset,limit:100}});}
 referenceOptions(code:string,mobile:boolean,context:IncidentContext,kind:IncidentReferenceKind,offset=0){return this.http.get<IncidentReferenceOption[]>(this.base(code,mobile)+'/business-reference-options',{params:{kind,offset,limit:25,...(context.dossierId?{dossierId:context.dossierId}:{}),...(context.nodeId?{nodeId:context.nodeId}:{})}});}
 references(code:string,mobile:boolean,id:string){return this.http.get<IncidentReferenceOption[]>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/business-references');}
 create(code:string,mobile:boolean,input:IncidentCreation){return this.http.post<IncidentView>(this.base(code,mobile)+(mobile?'/android':''),input);}
 editDraft(code:string,mobile:boolean,id:string,input:IncidentDraftEdit){return this.http.put<IncidentView>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/draft',input);}
 detail(code:string,mobile:boolean,id:string){return this.http.get<IncidentView>(this.base(code,mobile)+'/'+encodeURIComponent(id));}
 targets(code:string,mobile:boolean,id:string){return this.http.get<IncidentTarget[]>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/assignment-targets');}
 levels(code:string,mobile:boolean,id:string){return this.http.get<IncidentMetrics>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/service-levels');}
 summary(code:string,mobile:boolean){return this.http.get<IncidentSummary>(this.base(code,mobile)+'/summary');}
 statistics(code:string,mobile:boolean){return this.http.get<Array<{status:string;category:string;priority:string;volume:number}>>(this.base(code,mobile)+'/statistics');}
 notices(code:string,mobile:boolean,id:string){return this.http.get<IncidentNotice[]>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/notices');}
 history(code:string,mobile:boolean,id:string){return this.http.get<IncidentHistory[]>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/history');}
 reconcileNotice(code:string,mobile:boolean,id:string,input:object){return this.http.post<void>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/notices/reconcile',input);}
 export(code:string,mobile:boolean){return this.http.get(this.base(code,mobile)+'/export',{responseType:'blob'});}
 messages(code:string,mobile:boolean,id:string){return this.http.get<IncidentMessage[]>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/messages');}
 command(code:string,mobile:boolean,ticket:IncidentView,action:IncidentAction,input:object){return this.http.post<IncidentView>(this.base(code,mobile)+'/'+encodeURIComponent(ticket.id)+'/actions/'+action,input);}
 attachments(code:string,mobile:boolean,id:string){return this.http.get<IncidentAttachment[]>(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/attachments');}
 upload(code:string,mobile:boolean,ticket:IncidentView,file:File,requestId:string){const form=new FormData();form.append('requestId',requestId);form.append('expectedVersion',String(ticket.version));form.append('file',file);return this.http.post<IncidentAttachment>(this.base(code,mobile)+'/'+encodeURIComponent(ticket.id)+'/attachments',form);}
 download(code:string,mobile:boolean,id:string,fileId:string){return this.http.get(this.base(code,mobile)+'/'+encodeURIComponent(id)+'/attachments/'+encodeURIComponent(fileId),{responseType:'blob'});}
 configuration(code:string,mobile:boolean){return this.http.get<Record<string,unknown>>(this.base(code,mobile)+'/configuration');}
 configure(code:string,mobile:boolean,path:string,value:object){return this.http.put<void>(this.base(code,mobile)+'/configuration/'+path,value);}
 collectiveCapabilities(code:string){return this.http.get<{manage:boolean;send:boolean}>(this.base(code,false)+'/collectives/capabilities');}
 collectives(code:string,offset=0){return this.http.get<IncidentCollectiveHead[]>(this.base(code,false)+'/collectives',{params:{offset}});}
 collective(code:string,id:string){return this.http.get<IncidentCollective>(this.base(code,false)+'/collectives/'+encodeURIComponent(id));}
 createCollective(code:string,input:object){return this.http.post<IncidentCollective>(this.base(code,false)+'/collectives',input);}
 collectiveMembership(code:string,id:string,input:object){return this.http.post<IncidentCollective>(this.base(code,false)+'/collectives/'+encodeURIComponent(id)+'/membership',input);}
 collectiveState(code:string,id:string,input:object){return this.http.post<IncidentCollective>(this.base(code,false)+'/collectives/'+encodeURIComponent(id)+'/state',input);}
 prepareBroadcast(code:string,id:string,input:object){return this.http.post<IncidentBroadcast>(this.base(code,false)+'/collectives/'+encodeURIComponent(id)+'/broadcasts',input);}
 sendBroadcast(code:string,id:string,draft:string,input:object){return this.http.post<IncidentBroadcast>(this.base(code,false)+'/collectives/'+encodeURIComponent(id)+'/broadcasts/'+encodeURIComponent(draft)+'/send',input);}
}
