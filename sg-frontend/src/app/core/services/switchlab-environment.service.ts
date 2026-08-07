import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, Observable, of, tap } from 'rxjs';
import { SwitchLabEnvironmentReference } from '../models/product-contracts.models';
import { SwitchLabOperationsService } from './switchlab-operations.service';

const STORAGE_KEY = 'futurpayment-switchlab-environment';

@Injectable({ providedIn: 'root' })
export class SwitchLabEnvironmentService {
  private readonly operations = inject(SwitchLabOperationsService);
  readonly environments = signal<SwitchLabEnvironmentReference[]>([]);
  readonly selectedId = signal<string | null>(localStorage.getItem(STORAGE_KEY));
  readonly loading = signal(false);
  readonly selected = computed(() =>
    this.environments().find(item => item.id === this.selectedId()) ?? null,
  );

  load(): Observable<SwitchLabEnvironmentReference[]> {
    this.loading.set(true);
    return this.operations.environments().pipe(
      tap(items => {
        const active = items.filter(item => item.active);
        this.environments.set(active);
        if (!active.some(item => item.id === this.selectedId())) this.select(active[0]?.id ?? null);
      }),
      catchError(() => {
        this.environments.set([]);
        this.select(null);
        return of([]);
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  select(id: string | null): void {
    this.selectedId.set(id);
    if (id) localStorage.setItem(STORAGE_KEY, id);
    else localStorage.removeItem(STORAGE_KEY);
  }
}
