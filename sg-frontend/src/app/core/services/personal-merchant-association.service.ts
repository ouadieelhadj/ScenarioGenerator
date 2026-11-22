import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { url } from '../config/api.config';
import type { PersonalContextSession } from '../auth/personal-context-session';
import type { PortalScopeRole } from './merchant-hierarchy.service';

export interface PersonalAssociation {
  id: string; acquirerCode: string; hierarchyNodeId: string; hierarchyLabel: string;
  role: PortalScopeRole; state: 'PENDING' | 'ACTIVE' | 'REVOKED'; version: number;
  acceptedAt: string | null; revokedAt: string | null;
}
export interface PersonalContext { id: string; code: string; label: string; }

@Injectable({ providedIn: 'root' })
export class PersonalMerchantAssociationService {
  private readonly http = inject(HttpClient);
  private readonly base = url.onboarding('/api/merchant-portal/v1');
  mine(page = 0): Observable<PersonalAssociation[]> {
    return this.http.get<PersonalAssociation[]>(this.base + '/me/personal-associations', { params: { page, size: 50 } });
  }
  contexts(): Observable<PersonalContext[]> { return this.http.get<PersonalContext[]>(this.base + '/me/contexts'); }
  accept(association: PersonalAssociation): Observable<PersonalAssociation> {
    return this.http.post<PersonalAssociation>(this.base + '/me/personal-associations/' + encodeURIComponent(association.id) + '/accept',
      { expectedVersion: association.version });
  }
  select(code: string): Observable<PersonalContextSession> {
    return this.http.post<PersonalContextSession>(this.base + '/me/context', { acquirerCode: code });
  }
  request(code: string, node: string, command: string, personalLogin: string, role: PortalScopeRole): Observable<PersonalAssociation> {
    return this.http.post<PersonalAssociation>(this.base + '/acquirers/' + encodeURIComponent(code) + '/hierarchy/'
      + encodeURIComponent(node) + '/personal-associations/' + encodeURIComponent(command), { personalLogin, role });
  }
}
