import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { EMPTY, finalize, forkJoin, switchMap } from 'rxjs';
import { SwitchInterfaceCapability, SwitchInterfaceDefinition, SwitchLabOverview, SwitchLabTraceEvent } from '../../core/models/product-contracts.models';
import { PORTAL_PRODUCT } from '../../core/product/product.config';
import { SwitchLabEnvironmentService } from '../../core/services/switchlab-environment.service';
import { SwitchLabOperationsService } from '../../core/services/switchlab-operations.service';
import { SwitchInterfaceService } from '../../core/services/switch-interface.service';

@Component({
  selector: 'app-product-area',
  standalone: true,
  imports: [TranslatePipe, DatePipe],
  template: `
    <div class="page-header">
      <div><h1><i [class]="icon"></i> {{ titleKey | translate }}</h1><p>{{ product.brand }}</p></div>
      @if (isSwitchLab) { <button type="button" (click)="load()" [disabled]="loading()"><i class="pi pi-refresh"></i> Actualiser</button> }
    </div>

    @if (!isSwitchLab) {
      @if (switchCapability(); as cap) { <section class="foundation"><i class="pi pi-link"></i><div><strong>{{ cap.registryAvailable ? 'Registre membre disponible' : 'Registre membre bloqué' }}</strong><p>{{ cap.reason }}</p></div></section> }
      <section class="card"><header><div><h2>Connexions membre</h2><p>État consolidé depuis le registre d’interfaces Switch.</p></div><span>{{ switchInterfaces().length }} interface(s)</span></header><div class="components">@for (item of switchInterfaces(); track item.id) { <div><strong>{{ item.code }} · {{ item.network }}</strong><span [class]="statusClass(item.connectionStatus)">{{ item.connectionStatus }}</span></div> } @empty { <div class="empty">Aucune connexion réelle retournée par le backend membre.</div> }</div></section>
    } @else if (loading()) {
      <section class="state"><i class="pi pi-spin pi-spinner"></i> Chargement de l'exploitation SwitchLab…</section>
    } @else if (error()) {
      <section class="state error"><i class="pi pi-exclamation-triangle"></i> {{ error() }}</section>
    } @else {
      @if (overview(); as state) {
        <section class="card">
          <header><div><h2>Connexions et composants</h2><p>{{ state.environment.label }}</p></div><span [class]="statusClass(state.overallStatus)">{{ state.overallStatus }}</span></header>
          <div class="components">
            @for (component of state.components; track component.code) {
              <div><strong>{{ component.code }}</strong><span [class]="statusClass(component.status)">{{ component.status }}</span></div>
            }
          </div>
        </section>
      }
      <section class="card">
        <header><div><h2>Traces structurées</h2><p>Requêtes BFF corrélées, sans corps, secret ou donnée monétique.</p></div><span>{{ traces().length }} événement(s)</span></header>
        @if (!traces().length) { <div class="empty">Aucune trace disponible pour cette session BFF.</div> }
        @else {
          <div class="trace-list">
            @for (trace of traces(); track trace.id) {
              <article>
                <time>{{ trace.timestamp | date:'medium' }}</time>
                <span class="level" [class.warn]="trace.level === 'WARN'" [class.error]="trace.level === 'ERROR'">{{ trace.level }}</span>
                <strong>{{ trace.component }}</strong><span>{{ trace.message }}</span><code>{{ trace.correlationId }}</code>
              </article>
            }
          </div>
        }
      </section>
    }
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
    .page-header h1 { display:flex; align-items:center; gap:10px; margin:0; font-size:20px; color:var(--sg-text-primary); }
    .page-header h1 i { color:var(--sg-color-primary); } .page-header p, header p { color:var(--sg-text-muted); margin:6px 0 0; }
    button { border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:9px 14px; background:var(--sg-bg-surface); color:var(--sg-text-primary); }
    .card, .state, .foundation { margin-top:18px; padding:18px; background:var(--sg-bg-surface); border:1px solid var(--sg-border); border-radius:var(--sg-radius); }
    .card header { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; } .card h2 { margin:0; font-size:17px; }
    .components { display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:9px; margin-top:16px; }
    .components div { display:flex; justify-content:space-between; gap:8px; border:1px solid var(--sg-border); border-radius:var(--sg-radius); padding:11px; }
    .trace-list { margin-top:14px; } .trace-list article { display:grid; grid-template-columns:150px 60px 170px 1fr 290px; gap:9px; padding:9px 0; border-top:1px solid var(--sg-border); align-items:center; font-size:12px; }
    .trace-list code { overflow:hidden; text-overflow:ellipsis; color:var(--sg-text-muted); } .level { color:#16803c; } .level.warn { color:#a15c00; } .level.error, .state.error { color:#b42318; }
    .status-up { color:#16803c; } .status-degraded { color:#a15c00; } .status-down { color:#b42318; } .status-unknown { color:var(--sg-text-muted); }
    .empty { padding:24px; text-align:center; color:var(--sg-text-muted); } .foundation { display:flex; gap:12px; border-style:dashed; }
    @media (max-width:1000px) { .trace-list article { grid-template-columns:1fr 70px; } .trace-list code, .trace-list article > span:last-of-type { grid-column:1 / -1; } }
  `],
})
export class ProductAreaComponent implements OnInit {
  readonly product = inject(PORTAL_PRODUCT);
  readonly environment = inject(SwitchLabEnvironmentService);
  private readonly operations = inject(SwitchLabOperationsService);
  private readonly switchService = inject(SwitchInterfaceService);
  private route = inject(ActivatedRoute);
  readonly titleKey = this.route.snapshot.data['titleKey'] as string;
  readonly icon = this.route.snapshot.data['icon'] as string;
  readonly isSwitchLab = this.product.code === 'SWITCHLAB';
  readonly overview = signal<SwitchLabOverview | null>(null);
  readonly traces = signal<SwitchLabTraceEvent[]>([]);
  readonly switchCapability = signal<SwitchInterfaceCapability | null>(null);
  readonly switchInterfaces = signal<SwitchInterfaceDefinition[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void { if (this.isSwitchLab) this.load(); else this.loadSwitch(); }

  loadSwitch(): void { forkJoin({ capability: this.switchService.capability(), interfaces: this.switchService.interfaces() }).subscribe({ next: data => { this.switchCapability.set(data.capability); this.switchInterfaces.set(data.interfaces); }, error: () => this.error.set('Le backend Switch membre ou la session Switch est indisponible.') }); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.environment.load().pipe(
      switchMap(() => {
        const id = this.environment.selectedId();
        return id ? forkJoin({ overview: this.operations.overview(id), traces: this.operations.traces(id) }) : EMPTY;
      }),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: result => { this.overview.set(result.overview); this.traces.set(result.traces); },
      error: () => this.error.set("Les données d'exploitation SwitchLab ne sont pas disponibles."),
    });
  }

  statusClass(status: string): string { return `status-${status.toLowerCase()}`; }
}
