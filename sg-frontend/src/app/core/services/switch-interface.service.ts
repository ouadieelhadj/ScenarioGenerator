import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchInterfaceCapability, SwitchInterfaceDefinition } from '../models/product-contracts.models';
@Injectable({providedIn:'root'})
export class SwitchInterfaceService{private readonly http=inject(HttpClient);capability():Observable<SwitchInterfaceCapability>{return this.http.get<SwitchInterfaceCapability>(url.orchestrator(ENDPOINTS.switch.interfaceCapabilities))}interfaces():Observable<SwitchInterfaceDefinition[]>{return this.http.get<SwitchInterfaceDefinition[]>(url.orchestrator(ENDPOINTS.switch.interfaces))}}
