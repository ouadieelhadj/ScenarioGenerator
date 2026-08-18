import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { url } from '../config/api.config';
import { SoftPosActivation, SoftPosDevice, SoftPosDeviceStatus, SoftPosRoute, SoftPosTransaction } from '../models/softpos.models';

@Injectable({ providedIn: 'root' })
export class SoftPosAdminService {
  private readonly http = inject(HttpClient);
  private endpoint(path:string):string { return url.orchestrator(`/api/switch/v1/softpos${path}`); }
  devices():Observable<SoftPosDevice[]> { return this.http.get<SoftPosDevice[]>(this.endpoint('/devices')); }
  routes():Observable<SoftPosRoute[]> { return this.http.get<SoftPosRoute[]>(this.endpoint('/poserver-routes')); }
  transactions():Observable<SoftPosTransaction[]> { return this.http.get<SoftPosTransaction[]>(this.endpoint('/transactions')); }
  issueActivation(merchantId:string,outletId:string,terminalId:string):Observable<SoftPosActivation> { return this.http.post<SoftPosActivation>(this.endpoint('/activations'),{merchantId,outletId,terminalId}); }
  updateDevice(deviceId:string,status:SoftPosDeviceStatus):Observable<SoftPosDevice> { return this.http.patch<SoftPosDevice>(this.endpoint(`/devices/${deviceId}/status`),{status}); }
  saveRoute(route:SoftPosRoute):Observable<SoftPosRoute> { return this.http.put<SoftPosRoute>(this.endpoint(`/poserver-routes/${encodeURIComponent(route.environment)}`),route); }
}
