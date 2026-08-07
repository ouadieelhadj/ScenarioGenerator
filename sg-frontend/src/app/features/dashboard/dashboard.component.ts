import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { EMPTY, finalize, switchMap } from 'rxjs';
import { PORTAL_PRODUCT } from '../../core/product/product.config';
import { SwitchLabOverview } from '../../core/models/product-contracts.models';
import { SwitchLabEnvironmentService } from '../../core/services/switchlab-environment.service';
import { SwitchLabOperationsService } from '../../core/services/switchlab-operations.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header">
      <div>
        <h1><i class="pi pi-home"></i> Tableau de bord</h1>
        @if (isSwitchLab) {
          <p>Disponibilité des simulateurs pour l'environnement sélectionné.</p>
        }
      </div>
      @if (isSwitchLab) {
        <div class="actions">
          <label>Environnement
            <select [value]="environment.selectedId() ?? ''" (change)="changeEnvironment($any($event.target).value)">
              @for (item of environment.environments(); track item.id) {
                <option [value]="item.id">{{ item.label }} ({{ item.type }})</option>
              }
            </select>
          </label>
          <button class="refresh" type="button" (click)="refresh()" [disabled]="loading() || !environment.selectedId()">
            <i class="pi" [class.pi-spin]="loading()" [class.pi-spinner]="loading()" [class.pi-refresh]="!loading()"></i>
            Actualiser
          </button>
        </div>
      }
    </div>

    @if (!isSwitchLab) {
      <div class="empty">Le tableau de bord de ce produit sera développé dans son lot dédié.</div>
    } @else if (loading()) {
      <div class="empty"><i class="pi pi-spin pi-spinner"></i> Chargement de l'état des simulateurs…</div>
    } @else if (error()) {
      <div class="notice error"><i class="pi pi-exclamation-triangle"></i>{{ error() }}</div>
    } @else if (!environment.selected()) {
      <div class="notice"><i class="pi pi-info-circle"></i>Aucun environnement SwitchLab actif n'est disponible.</div>
    } @else {
      @if (overview(); as state) {
      <section class="summary" aria-label="Résumé de disponibilité">
        <article><span>État global</span><strong [class]="statusClass(state.overallStatus)">{{ state.overallStatus }}</strong></article>
        <article><span>Disponibles</span><strong>{{ state.availableComponents }}</strong></article>
        <article><span>Dégradés</span><strong>{{ state.degradedComponents }}</strong></article>
        <article><span>Indisponibles</span><strong>{{ state.unavailableComponents }}</strong></article>
      </section>

      <section class="panel">
        <div class="panel-title">
          <div><h2>Composants simulés</h2><p>{{ state.environment.label }} · contrôle {{ state.checkedAt | date:'medium' }}</p></div>
          <code>{{ state.correlationId }}</code>
        </div>
        <div class="components">
          @for (component of state.components; track component.code) {
            <article class="component-card">
              <div class="component-head">
                <strong>{{ component.code }}</strong>
                <span [class]="statusClass(component.status)">{{ component.status }}</span>
              </div>
              <small>Vérifié {{ component.checkedAt | date:'short' }}</small>
              <div class="capabilities">
                @for (capability of component.capabilities; track capability) { <span>{{ capability }}</span> }
                @if (!component.capabilities.length) { <em>Aucune capacité publiée</em> }
              </div>
            </article>
          }
        </div>
      </section>
      }
    }
  `,
  styles: [`
    .page-header { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }
    .page-header h1 { margin:0; font-size:20px; color:var(--sg-text-primary); display:flex; align-items:center; gap:10px; }
    .page-header h1 i { color:var(--sg-color-primary); }
    .page-header p, .panel-title p { margin:6px 0 0; color:var(--sg-text-muted); }
    .actions { display:flex; align-items:flex-end; gap:10px; }
    .actions label { display:flex; flex-direction:column; gap:5px; color:var(--sg-text-muted); font-size:12px; }
    .actions select { min-width:210px; border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:8px; background:var(--sg-bg-surface); color:var(--sg-text-primary); }
    .refresh { border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:9px 14px; background:var(--sg-bg-surface); color:var(--sg-text-primary); cursor:pointer; }
    .refresh:disabled { opacity:.55; cursor:not-allowed; }
    .summary { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; margin:22px 0; }
    .summary article, .panel, .empty, .notice { background:var(--sg-bg-surface); border:1px solid var(--sg-border); border-radius:var(--sg-radius); }
    .summary article { padding:18px; display:flex; flex-direction:column; gap:9px; }
    .summary span { color:var(--sg-text-muted); font-size:13px; }
    .summary strong { font-size:22px; }
    .panel { padding:20px; }
    .panel-title { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
    .panel-title h2 { margin:0; font-size:17px; }
    .panel-title code { font-size:11px; color:var(--sg-text-muted); }
    .components { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:12px; margin-top:18px; }
    .component-card { border:1px solid var(--sg-border); border-radius:var(--sg-radius); padding:14px; }
    .component-head { display:flex; justify-content:space-between; gap:8px; }
    .component-card small { display:block; margin:8px 0; color:var(--sg-text-muted); }
    .capabilities { display:flex; flex-wrap:wrap; gap:5px; }
    .capabilities span { background:var(--sg-bg-muted); border-radius:999px; padding:3px 8px; font-size:11px; }
    .capabilities em { color:var(--sg-text-muted); font-size:12px; }
    .status-up { color:#16803c; } .status-degraded { color:#a15c00; } .status-down { color:#b42318; } .status-unknown { color:var(--sg-text-muted); }
    .empty, .notice { margin-top:22px; padding:28px; text-align:center; color:var(--sg-text-muted); }
    .notice i { margin-right:8px; } .notice.error { color:#b42318; border-color:#f2b8b5; }
    @media (max-width:800px) { .summary { grid-template-columns:repeat(2,1fr); } .panel-title { flex-direction:column; } }
  `],
})
export class DashboardComponent implements OnInit {
  readonly product = inject(PORTAL_PRODUCT);
  readonly environment = inject(SwitchLabEnvironmentService);
  private readonly operations = inject(SwitchLabOperationsService);
  readonly overview = signal<SwitchLabOverview | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly isSwitchLab = this.product.code === 'SWITCHLAB';

  ngOnInit(): void {
    if (!this.isSwitchLab) return;
    this.loading.set(true);
    this.environment.load().pipe(
      switchMap(() => {
        const id = this.environment.selectedId();
        return id ? this.operations.overview(id) : EMPTY;
      }),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: state => this.overview.set(state),
      error: () => this.error.set("L'état des simulateurs n'a pas pu être chargé."),
    });
  }

  refresh(): void {
    const id = this.environment.selectedId();
    if (!id) return;
    this.loading.set(true);
    this.error.set(null);
    this.operations.overview(id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: state => this.overview.set(state),
      error: () => this.error.set("L'état des simulateurs n'a pas pu être chargé."),
    });
  }

  changeEnvironment(id: string): void {
    this.environment.select(id || null);
    this.overview.set(null);
    this.refresh();
  }

  statusClass(status: string): string {
    return `status-${status.toLowerCase()}`;
  }
}
