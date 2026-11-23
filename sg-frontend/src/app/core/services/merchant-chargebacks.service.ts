import {Injectable,inject} from '@angular/core';import {HttpClient} from '@angular/common/http';
import {url} from '../config/api.config';import {PortalPage} from '../models/merchant-activity.models';
import {PortalChargeback,ChargebackResponse,ChargebackDocument,ChargebackDelivery} from '../models/merchant-chargebacks.models';
@Injectable({providedIn:'root'})export class MerchantChargebacksService {
  private readonly http=inject(HttpClient);
  private base(code:string){return url.onboarding('/api/merchant-portal/v1/acquirers/'+encodeURIComponent(code)+'/chargebacks');}
  list(code:string,from:string,to:string,state:string,page:number){return this.http.get<PortalPage<PortalChargeback>>(this.base(code),{params:{from,to,page,size:50,...(state?{state}:{})}});}
  detail(code:string,id:string){return this.http.get<PortalChargeback>(this.base(code)+'/'+encodeURIComponent(id));}
  notifications(code:string,id:string){return this.http.get<ChargebackDelivery[]>(this.base(code)+'/'+encodeURIComponent(id)+'/notifications');}
  retryNotification(code:string,id:string,delivery:ChargebackDelivery,justification:string){return this.http.post<ChargebackDelivery[]>(this.base(code)+'/'+encodeURIComponent(id)+'/notifications/'+encodeURIComponent(delivery.id)+'/retry-after-relay-check',{justification,expectedVersion:delivery.version});}
  createResponse(code:string,id:string,response:string,comment:string,key:string){return this.http.post<PortalChargeback>(this.base(code)+'/'+encodeURIComponent(id)+'/responses',{response,comment},{headers:{'Idempotency-Key':key}});}
  private responseBase(code:string,id:string,response:string){return this.base(code)+'/'+encodeURIComponent(id)+'/responses/'+encodeURIComponent(response);}
  submit(code:string,id:string,response:ChargebackResponse){return this.http.post<PortalChargeback>(this.responseBase(code,id,response.id)+'/submit',{expectedVersion:response.version});}
  merchantDecision(code:string,id:string,response:ChargebackResponse,approve:boolean,justification:string){return this.http.post<PortalChargeback>(this.responseBase(code,id,response.id)+'/merchant-decision',{approve,justification,expectedVersion:response.version});}
  backofficeDecision(code:string,id:string,response:ChargebackResponse,decision:string,justification:string){return this.http.post<PortalChargeback>(this.responseBase(code,id,response.id)+'/backoffice-decision',{decision,justification,expectedVersion:response.version});}
  withdraw(code:string,id:string,response:ChargebackResponse){return this.http.post<PortalChargeback>(this.responseBase(code,id,response.id)+'/withdraw-stale',{expectedVersion:response.version});}
  invalidate(code:string,id:string,response:ChargebackResponse,justification:string){return this.http.post<PortalChargeback>(this.responseBase(code,id,response.id)+'/invalidate-handoff',{expectedVersion:response.version,justification});}
  upload(code:string,id:string,response:ChargebackResponse,file:File){const body=new FormData();body.append('file',file);return this.http.post<PortalChargeback>(this.responseBase(code,id,response.id)+'/documents',body);}
  download(code:string,id:string,response:ChargebackResponse,doc:ChargebackDocument){return this.http.get(this.responseBase(code,id,response.id)+'/documents/'+encodeURIComponent(doc.id),{responseType:'blob'});}
}
