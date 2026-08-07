import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { SwitchAcquiringOverview } from '../models/product-contracts.models';

@Injectable({ providedIn: 'root' })
export class SwitchAcquiringService {
  private readonly http = inject(HttpClient);

  overview(): Observable<SwitchAcquiringOverview> {
    return this.http.get<SwitchAcquiringOverview>(url.orchestrator(ENDPOINTS.switch.acquiringOverview));
  }
}
