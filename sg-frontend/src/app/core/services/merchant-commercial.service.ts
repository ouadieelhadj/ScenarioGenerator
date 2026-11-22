import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { url } from '../config/api.config';
import { CommercialCommand, CommercialConfiguration, CommercialConfirmation, CommercialCriteria, CommercialHistory, CommercialPhase, CommercialPreview, CommercialReference, CommercialReferenceKind, CommercialResult, CommercialWorkspace } from '../models/merchant-commercial.models';

@Injectable({ providedIn: 'root' })
export class MerchantCommercialService {
  private readonly http = inject(HttpClient);
  private path(id: string, suffix = ''): string {
    return url.onboarding('/api/merchant-onboarding/v1/dossiers/' + encodeURIComponent(id) + '/commercial' + suffix);
  }
  workspace(id: string) { return this.http.get<CommercialWorkspace>(this.path(id, '/workspace')); }
  queue(acquirer:string,phase:CommercialPhase|'',offset:number,limit:number) {
    return this.http.get<{items:Array<{id:string;reference:string;legalName:string|null;phase:CommercialPhase;origin:string}>;total:number;offset:number;limit:number}>(
      url.onboarding('/api/merchant-onboarding/v1/acquirers/'+encodeURIComponent(acquirer)+'/commercial/dossiers'),
      {params:{offset,limit,...(phase?{phase}:{})}});
  }
  history(id: string) { return this.http.get<CommercialHistory[]>(this.path(id, '/history')); }
  confirmation(id: string) { return this.http.get<CommercialConfirmation|null>(this.path(id, '/confirmation')); }
  private configurationPath(acquirer:string,suffix:string) {
    return url.onboarding('/api/merchant-onboarding/v1/acquirers/'+encodeURIComponent(acquirer)+'/commercial'+suffix);
  }
  references(acquirer: string, kind: CommercialReferenceKind,activeOnly=true) {
    return this.http.get<CommercialReference[]>(this.configurationPath(acquirer,'/references/'+kind),{params:{activeOnly}});
  }
  configuration(acquirer:string) {return this.http.get<CommercialConfiguration>(this.configurationPath(acquirer,'/configuration'));}
  saveReference(acquirer:string,kind:CommercialReferenceKind,code:string,value:{label:string;extra:string|null;active:boolean;expectedVersion:number}) {
    return this.http.put<void>(this.configurationPath(acquirer,'/references/'+kind+'/'+encodeURIComponent(code)),value);
  }
  saveConfiguration(acquirer:string,resource:'teams'|'members'|'rules',id:string,value:object) {
    return this.http.put<void>(this.configurationPath(acquirer,'/'+resource+'/'+encodeURIComponent(id)),value);
  }
  saveFallback(acquirer:string,memberId:string|null,expectedVersion:number) {
    return this.http.put<void>(this.configurationPath(acquirer,'/fallback'),{memberId,expectedVersion});
  }
  preview(acquirer:string,value:CommercialCriteria) {
    return this.http.post<CommercialPreview>(this.configurationPath(acquirer,'/preview'),value);
  }
  command(id: string, action: string, command: CommercialCommand) {
    const paths: Record<string, string> = { ADOPT_HISTORY: 'adopt-history', SUBMIT: 'submit', PREPARE_CONFIRMATION: 'prepare-confirmation',
      RENEW_CONFIRMATION: 'renew-confirmation', COMPLEMENTS: 'complements', REASSIGN: 'reassign', READY: 'ready', TRANSMIT: 'transmit' };
    if (!paths[action]) throw new Error('Action commerciale inconnue.');
    return this.http.post<CommercialResult>(this.path(id, '/' + paths[action]), command);
  }
  confirm(id: string, command: CommercialCommand, confirmationId: string, token: string) {
    return this.http.post<CommercialResult>(this.path(id, '/confirm'), { command, confirmationId, token });
  }
}
