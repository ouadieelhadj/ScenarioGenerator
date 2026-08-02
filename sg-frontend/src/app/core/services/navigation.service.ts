import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { ModuleNavigation, NavigationItem, NavigationResponse } from '../models/navigation.models';

@Injectable({ providedIn: 'root' })
export class NavigationService {
  private http = inject(HttpClient);
  readonly modules = signal<ModuleNavigation[]>([]);
  readonly loaded = signal(false);
  readonly legacyFallback = signal(false);

  load(): Observable<NavigationResponse> {
    return this.http.get<NavigationResponse>(url.orchestrator(ENDPOINTS.me.navigation)).pipe(
      tap(response => {
        this.modules.set(response.modules);
        this.legacyFallback.set(response.legacyFallback);
        this.loaded.set(true);
      }),
      catchError(() => {
        this.modules.set([]);
        this.legacyFallback.set(true);
        this.loaded.set(true);
        return of({ modules: [], legacyFallback: true } satisfies NavigationResponse);
      }),
    );
  }

  ensureLoaded(): Observable<void> {
    return this.loaded() ? of(undefined) : this.load().pipe(map(() => undefined));
  }

  findScreen(moduleCode: string, screenCode: string): NavigationItem | null {
    const module = this.modules().find(item => item.code.toLowerCase() === moduleCode.toLowerCase());
    return module ? this.findScreenIn(module.children, screenCode) : null;
  }

  private findScreenIn(items: NavigationItem[], screenCode: string): NavigationItem | null {
    for (const item of items) {
      const routeCode = item.route?.split('/').filter(Boolean).at(-1);
      if (item.type === 'SCREEN' && (
        item.code.toLowerCase() === screenCode.toLowerCase()
        || routeCode?.toLowerCase() === screenCode.toLowerCase()
      )) return item;
      const child = this.findScreenIn(item.children ?? [], screenCode);
      if (child) return child;
    }
    return null;
  }
}
