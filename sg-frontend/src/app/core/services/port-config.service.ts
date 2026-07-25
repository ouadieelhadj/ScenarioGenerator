import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENDPOINTS, url, setPort, getPort } from '../config/api.config';

type ServiceKey = 'orchestrator' | 'acquirer' | 'issuer';

export interface PortInfo { service: string; currentPort: number; }
export interface PortChangeResult { message: string; oldPort?: number; newPort?: number; restarting: boolean; }

@Injectable({ providedIn: 'root' })
export class PortConfigService {
  private http = inject(HttpClient);

  // URL selon le service
  private urlFor(service: ServiceKey, path: string): string {
    return service === 'orchestrator' ? url.orchestrator(path)
         : service === 'acquirer' ? url.acquirer(path)
         : url.issuer(path);
  }

  // Port actuel connu du front (localStorage/defaut)
  frontPort(service: ServiceKey): number {
    return getPort(service);
  }

  // GET le port reel du service
  getServerPort(service: ServiceKey): Observable<PortInfo> {
    return this.http.get<PortInfo>(this.urlFor(service, ENDPOINTS.config.port));
  }

  // POST le nouveau port (declenche le restart cote back)
  changePort(service: ServiceKey, port: number): Observable<PortChangeResult> {
    return this.http.post<PortChangeResult>(this.urlFor(service, ENDPOINTS.config.port), { port });
  }

  // Met a jour la config front (localStorage) apres un changement reussi
  updateFrontPort(service: ServiceKey, port: number): void {
    setPort(service, port);
  }
}

