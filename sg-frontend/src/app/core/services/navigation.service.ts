import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of, tap } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { ModuleNavigation, NavigationResponse } from '../models/navigation.models';

@Injectable({ providedIn: 'root' })
export class NavigationService {
  private http = inject(HttpClient);
  readonly modules = signal<ModuleNavigation[]>([]);
  readonly loaded = signal(false);

  load() {
    return this.http.get<NavigationResponse>(url.orchestrator(ENDPOINTS.me.navigation)).pipe(
      tap(response => {
        this.modules.set(response.modules);
        this.loaded.set(true);
      }),
      catchError(() => {
        this.modules.set([]);
        this.loaded.set(true);
        return of({ modules: [], legacyFallback: true } satisfies NavigationResponse);
      }),
    );
  }
}
