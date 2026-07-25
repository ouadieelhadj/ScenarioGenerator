import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url, TEST_DEFAULTS } from '../config/api.config';
import { Card, CardRequest, AuthRequest } from '../models/dmas.models';

@Injectable({ providedIn: 'root' })
export class DmasService {
  private http = inject(HttpClient);
  private ep = ENDPOINTS.dmas;

  // ---- RESEAU (acquereur 8084) ----
  signon(): Observable<unknown> {
    return this.http.post(url.acquirer(this.ep.network.signon), {});
  }
  signoff(): Observable<unknown> {
    return this.http.post(url.acquirer(this.ep.network.signoff), {});
  }
  networkStatus(): Observable<unknown> {
    return this.http.get(url.acquirer(this.ep.network.status));
  }

  // ---- CLES (acquereur 8084) ----
  bootstrapKek(memberGroupId: string, kekClear: string): Observable<unknown> {
    return this.http.post(url.acquirer(this.ep.kek.bootstrap), { memberGroupId, kekClear });
  }
  exchangePek(memberGroupId: string = TEST_DEFAULTS.memberGroupId): Observable<unknown> {
    const params = new HttpParams().set('memberGroupId', memberGroupId);
    return this.http.post(url.acquirer(this.ep.keyexchange.pek), {}, { params });
  }

  // ---- CARTES (issuer 8501) ----
  createCard(req: CardRequest): Observable<Card> {
    return this.http.post<Card>(url.issuer(this.ep.cards.base), req);
  }
  getCard(pan: string): Observable<Card> {
    return this.http.get<Card>(url.issuer(this.ep.cards.byPan(pan)));
  }
  setBalance(pan: string, balance: number): Observable<Card> {
    return this.http.post<Card>(url.issuer(this.ep.cards.balance(pan)), { balance });
  }

  // ---- TEST 0100 (orchestrateur 8080) ----
  authorize(req: AuthRequest): Observable<unknown> {
    return this.http.post(url.orchestrator(this.ep.authorize), req);
  }
}

