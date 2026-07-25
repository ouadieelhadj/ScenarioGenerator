import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MessageTypeRef } from '../models/campaign.models';
import { ENDPOINTS, url } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class MessageTypeService {
  private http = inject(HttpClient);

  /** Types de message d'un reseau (pour peupler le selecteur de categorie). */
  findByNetwork(network: string): Observable<MessageTypeRef[]> {
    const path = `${ENDPOINTS.messageTypes.base}?network=${encodeURIComponent(network)}`;
    return this.http.get<MessageTypeRef[]>(url.orchestrator(path));
  }
}
