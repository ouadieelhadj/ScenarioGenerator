import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchDomainOverview } from '../models/product-contracts.models';

@Injectable({ providedIn: 'root' })
export class SwitchMemberDomainService {
  private readonly http = inject(HttpClient);

  overview(domain: string): Observable<SwitchDomainOverview> {
    return this.http.get<SwitchDomainOverview>(url.orchestrator(ENDPOINTS.switch.domainOverview(domain)));
  }
}
