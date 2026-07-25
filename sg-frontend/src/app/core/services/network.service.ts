import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NetworkRef } from '../models/campaign.models';
import { ENDPOINTS, url } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class NetworkService {
  private http = inject(HttpClient);

  findAll(): Observable<NetworkRef[]> {
    return this.http.get<NetworkRef[]>(url.orchestrator(ENDPOINTS.networks.base));
  }
}
