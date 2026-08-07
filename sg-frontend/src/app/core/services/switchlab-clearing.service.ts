import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchLabClearingArtifact, SwitchLabClearingEodRequest, SwitchLabClearingEodResult, SwitchLabClearingNetwork } from '../models/product-contracts.models';

@Injectable({providedIn:'root'})
export class SwitchLabClearingService{
  private readonly http=inject(HttpClient);private readonly ep=ENDPOINTS.switchLab.clearing;
  networks():Observable<SwitchLabClearingNetwork[]>{return this.http.get<SwitchLabClearingNetwork[]>(url.orchestrator(this.ep.networks))}
  artifacts():Observable<SwitchLabClearingArtifact[]>{return this.http.get<SwitchLabClearingArtifact[]>(url.orchestrator(this.ep.artifacts))}
  upload(code:string,file:File):Observable<SwitchLabClearingArtifact>{const body=new FormData();body.append('file',file,file.name);return this.http.post<SwitchLabClearingArtifact>(url.orchestrator(this.ep.upload(code)),body)}
  eod(request:SwitchLabClearingEodRequest):Observable<SwitchLabClearingEodResult>{return this.http.post<SwitchLabClearingEodResult>(url.orchestrator(this.ep.eod),request)}
}
