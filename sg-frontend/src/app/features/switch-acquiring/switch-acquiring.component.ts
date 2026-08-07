import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { SwitchAcquiringOverview } from '../../core/models/product-contracts.models';
import { SwitchAcquiringService } from '../../core/services/switch-acquiring.service';

@Component({
  selector: 'app-switch-acquiring',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header">
      <div>
        <h1><i class="pi pi-shopping-cart"></i> Acquisition POS & e-commerce</h1>
        <p>Vue membre exclusivement alimentée par le BFF FuturPayment Switch.</p>
      </div>
      <button type="button" (click)="load()" [disabled]="loading()">
        <i [class]="loading() ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i> Actualiser
      </button>
    </div>

    @if (error()) {
      <section class="message error"><i class="pi pi-exclamation-triangle"></i>{{ error() }}</section>
    }
    @if (overview(); as state) {
      <section class="summary">
        <div><span>État global</span><strong [class]="statusClass(state.overallStatus)">{{ state.overallStatus }}</strong></div>
        <div><span>Dernière observation</span><strong>{{ state.checkedAt | date:'medium' }}</strong></div>
        <div><span>Corrélation</span><code>{{ state.correlationId }}</code></div>
      </section>

      <section class="card">
        <header><div><h2>Services membre</h2><p>Aucun simulateur SwitchLab n'est interrogé.</p></div></header>
        <div class="services">
          @for (service of state.services; track service.code) {
            <article>
              <div class="service-title"><div class="service-name"><strong>{{ service.label }}</strong><code>{{ service.code }}</code></div><span [class]="statusClass(service.status)">{{ service.status }}</span></div>
              <small>{{ service.configured ? 'URL membre configurée' : 'URL membre non configurée' }}</small>
              @if (service.capabilities.length) {
                <div class="tags">@for (capability of service.capabilities; track capability) { <span>{{ capability }}</span> }</div>
              }
              @if (service.limitation) { <p class="limitation">{{ service.limitation }}</p> }
            </article>
          }
        </div>
      </section>

      <section class="card">
        <header>
          <div><h2>Périmètre fonctionnel du lot 2</h2><p>Les commandes restent fermées lorsqu'une dépendance de consultation, d'identité ou de sécurité manque.</p></div>
        </header>
        <div class="feature-table">
          @for (feature of state.features; track feature.code) {
            <article>
              <div class="feature-name"><strong>{{ feature.label }}</strong><code>{{ feature.code }}</code></div>
              <span [class]="featureClass(feature.status)">{{ feature.status }}</span>
              <div class="flags">
                <span [class.enabled]="feature.backendEndpointAvailable">Backend</span>
                <span [class.enabled]="feature.consultationAvailable">Consultation</span>
                <span [class.enabled]="feature.actionAvailable">Actions</span>
                @if (feature.makerCheckerRequired) { <span>Maker/Checker requis</span> }
              </div>
              <p>{{ feature.limitation }}</p>
            </article>
          }
        </div>
      </section>

      <section class="message"><i class="pi pi-shield"></i><div><strong>Protection fail-closed</strong><p>Aucune donnée métier locale, aucun PAN et aucune action directe vers un backend membre ne sont utilisés pour contourner une API absente.</p></div></section>
    } @else if (loading()) {
      <section class="message"><i class="pi pi-spin pi-spinner"></i>Chargement des capacités Acquisition…</section>
    }
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; }
    h1 { margin:0; font-size:21px; color:var(--sg-text-primary); } h1 i { color:var(--sg-color-primary); margin-right:9px; }
    h2 { margin:0; font-size:17px; } p { color:var(--sg-text-muted); margin:6px 0 0; }
    button { border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:9px 14px; background:var(--sg-bg-surface); color:var(--sg-text-primary); }
    .summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; margin-top:18px; }
    .summary div, .card, .message { background:var(--sg-bg-surface); border:1px solid var(--sg-border); border-radius:var(--sg-radius); }
    .summary div { padding:15px; display:flex; flex-direction:column; gap:7px; } .summary span, small { color:var(--sg-text-muted); }
    .summary code { overflow:hidden; text-overflow:ellipsis; } .card { margin-top:18px; padding:18px; }
    .services { display:grid; grid-template-columns:repeat(auto-fit,minmax(300px,1fr)); gap:12px; margin-top:15px; }
    .services article { border:1px solid var(--sg-border); border-radius:var(--sg-radius); padding:14px; }
    .service-title { display:flex; justify-content:space-between; gap:10px; } .service-name { display:flex; flex-direction:column; gap:4px; } .service-name code { color:var(--sg-text-muted); font-size:11px; } .tags { display:flex; flex-wrap:wrap; gap:5px; margin-top:12px; }
    .tags span, .flags span { font-size:11px; border-radius:12px; padding:4px 8px; background:var(--sg-bg-subtle); color:var(--sg-text-muted); }
    .feature-table { margin-top:14px; } .feature-table article { display:grid; grid-template-columns:minmax(220px,1.2fr) 110px minmax(260px,1fr) 2fr; gap:14px; padding:13px 0; border-top:1px solid var(--sg-border); align-items:center; }
    .feature-name { display:flex; flex-direction:column; gap:4px; } .feature-name code { color:var(--sg-text-muted); font-size:11px; }
    .feature-table p { margin:0; font-size:12px; } .flags { display:flex; flex-wrap:wrap; gap:5px; } .flags .enabled { background:#e8f7ed; color:#16803c; }
    .message { display:flex; gap:12px; align-items:flex-start; padding:16px; margin-top:18px; } .message > i { color:var(--sg-color-primary); font-size:18px; } .message.error { color:#b42318; border-color:#f3b5af; }
    .status-up, .feature-available { color:#16803c; } .status-degraded, .feature-blocked { color:#a15c00; } .status-down, .feature-unavailable { color:#b42318; } .status-unknown { color:var(--sg-text-muted); }
    .limitation { font-size:12px; } @media (max-width:1050px) { .summary { grid-template-columns:1fr; } .feature-table article { grid-template-columns:1fr 110px; } .feature-table article p, .flags { grid-column:1 / -1; } }
  `],
})
export class SwitchAcquiringComponent implements OnInit {
  private readonly acquiring = inject(SwitchAcquiringService);
  readonly overview = signal<SwitchAcquiringOverview | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.acquiring.overview().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: value => this.overview.set(value),
      error: () => this.error.set('Le BFF Switch membre ou la session Switch est indisponible.'),
    });
  }

  statusClass(status: string): string { return `status-${status.toLowerCase()}`; }
  featureClass(status: string): string { return `feature-${status.toLowerCase()}`; }
}
