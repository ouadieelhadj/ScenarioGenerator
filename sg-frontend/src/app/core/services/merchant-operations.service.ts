import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { url } from '../config/api.config';
import { PortalPage } from '../models/merchant-activity.models';
import { OperationKind, OperationPayload, OperationCapabilities, PortalOperation, OperationDocument } from '../models/merchant-operations.models';
@Injectable({providedIn:'root'})
export class MerchantOperationsService {
  private readonly http = inject(HttpClient);
  private base(code: string) { return url.onboarding('/api/merchant-portal/v1/acquirers/' + encodeURIComponent(code) + '/operations'); }
  list(code: string, page: number) { return this.http.get<PortalPage<PortalOperation>>(this.base(code), {params:{page,size:50}}); }
  detail(code: string, id: string) { return this.http.get<PortalOperation>(this.base(code)+'/'+encodeURIComponent(id)); }
  permissions(code: string, node: string) { return this.http.get<OperationCapabilities>(this.base(code)+'/permissions',{params:{hierarchyNodeId:node}}); }
  create(code: string, node: string, kind: OperationKind, payload: OperationPayload, key: string) {
    return this.http.post<PortalOperation>(this.base(code),{hierarchyNodeId:node,kind,payload},{headers:{'Idempotency-Key':key}});
  }
  upload(code: string, id: string, file: File) { const body = new FormData(); body.append('file',file);
    return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(id)+'/documents',body); }
  document(code: string, id: string, documentId: string) {
    return this.http.get(this.base(code)+'/'+encodeURIComponent(id)+'/documents/'+encodeURIComponent(documentId),{responseType:'blob'});
  }
  submit(code: string, row: PortalOperation) { return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+'/submit',{expectedVersion:row.version}); }
  requestComplement(code: string, row: PortalOperation, reason: string) {
    return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+'/request-complements',{justification:reason,expectedVersion:row.version});
  }
  revise(code: string, row: PortalOperation, payload: OperationPayload, key: string) {
    return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+'/revise',{payload,expectedVersion:row.version},{headers:{'Idempotency-Key':key}});
  }
  decide(code: string, row: PortalOperation, approve: boolean, reason: string, backoffice: boolean) {
    return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+(backoffice?'/backoffice-decision':'/merchant-decision'),
      {approve,justification:reason,expectedVersion:row.version});
  }
  reviewDocument(code: string, row: PortalOperation, doc: OperationDocument, approve: boolean, reason: string) {
    return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+'/documents/'+encodeURIComponent(doc.id)+'/review',
      {approve,justification:reason,expectedVersion:doc.version});
  }
  execute(code: string, row: PortalOperation) { return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+'/execute',{expectedVersion:row.version}); }
  sourceStatus(code: string, row: PortalOperation) { return this.http.post<PortalOperation>(this.base(code)+'/'+encodeURIComponent(row.id)+'/source-status',{}); }
}
