import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchLabOnlineKeyStatus, SwitchLabOnlineNetwork, SwitchLabOnlineScenario, SwitchLabOnlineScenarioResult, SwitchLabOnlineSession } from '../models/product-contracts.models';

@Injectable({ providedIn: 'root' })
export class SwitchLabOnlineService {
  private readonly http=inject(HttpClient); private readonly ep=ENDPOINTS.switchLab.online;
  networks():Observable<SwitchLabOnlineNetwork[]>{return this.http.get<SwitchLabOnlineNetwork[]>(url.orchestrator(this.ep.networks))}
  session(code:string):Observable<SwitchLabOnlineSession>{return this.http.get<SwitchLabOnlineSession>(url.orchestrator(this.ep.session(code)))}
  keys(code:string):Observable<SwitchLabOnlineKeyStatus>{return this.http.get<SwitchLabOnlineKeyStatus>(url.orchestrator(this.ep.keys(code)))}
  scenarios():Observable<SwitchLabOnlineScenario[]>{return this.http.get<SwitchLabOnlineScenario[]>(url.orchestrator(this.ep.scenarios))}
  run(code:string):Observable<SwitchLabOnlineScenarioResult>{return this.http.post<SwitchLabOnlineScenarioResult>(url.orchestrator(this.ep.runScenario(code)),{})}
}
