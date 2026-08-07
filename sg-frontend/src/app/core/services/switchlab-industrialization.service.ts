import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchLabIndustrialReadiness } from '../models/product-contracts.models';
@Injectable({providedIn:'root'})
export class SwitchLabIndustrializationService{private readonly http=inject(HttpClient);private readonly ep=ENDPOINTS.switchLab.industrialization;readiness():Observable<SwitchLabIndustrialReadiness[]>{return this.http.get<SwitchLabIndustrialReadiness[]>(url.orchestrator(this.ep.readiness))}backup():Observable<Blob>{return this.http.get(url.orchestrator(this.ep.backup),{responseType:'blob'})}}
