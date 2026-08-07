import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchLabEcommerceComponent, SwitchLabEcommerceScenario } from '../models/product-contracts.models';
@Injectable({providedIn:'root'})
export class SwitchLabEcommerceService{private readonly http=inject(HttpClient);private readonly ep=ENDPOINTS.switchLab.ecommerce;components():Observable<SwitchLabEcommerceComponent[]>{return this.http.get<SwitchLabEcommerceComponent[]>(url.orchestrator(this.ep.components))}scenarios():Observable<SwitchLabEcommerceScenario[]>{return this.http.get<SwitchLabEcommerceScenario[]>(url.orchestrator(this.ep.scenarios))}}
