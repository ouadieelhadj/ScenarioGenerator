import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';
import { SwitchDomainOverview } from '../../core/models/product-contracts.models';
import { SwitchMemberDomainService } from '../../core/services/switch-member-domain.service';

@Component({
  selector: 'app-switch-member-domain',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header">
      <div><h1><i [class]="icon"></i>{{ title }}</h1><p>{{ description }}</p></div>
      <button type="button" (click)="load()" [disabled]="loading()"><i [class]="loading() ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i> Actualiser</button>
    </div>
    @if (error()) { <section class="message error"><i class="pi pi-exclamation-triangle"></i>{{ error() }}</section> }
    @if (overview(); as state) {
      <section class="summary">
        <div><span>Domaine</span><strong>{{ state.domain }}</strong></div>
        <div><span>État des dépendances</span><strong [class]="statusClass(state.overallStatus)">{{ state.overallStatus }}</strong></div>
        <div><span>Observation</span><strong>{{ state.checkedAt | date:'medium' }}</strong></div>
      </section>
      <section class="card">
        <header><div><h2>Modules backend membre</h2><p>Liste blanche propre au BFF Switch ; aucun simulateur n'est interrogé.</p></div><code>{{ state.correlationId }}</code></header>
        <div class="services">
          @for (service of state.services; track service.code) {
            <article>
              <div><div class="service-name"><strong>{{ service.label }}</strong><code>{{ service.code }}</code></div><span [class]="statusClass(service.status)">{{ service.status }}</span></div>
              <small>{{ service.configured ? 'Configuré' : 'Non configuré' }}</small>
              @if (service.capabilities.length) { <div class="tags">@for (capability of service.capabilities; track capability) { <span>{{ capability }}</span> }</div> }
              @if (service.limitation) { <p>{{ service.limitation }}</p> }
            </article>
          }
        </div>
      </section>
      <section class="card">
        <header><div><h2>Fonctions du lot</h2><p>Une fonction bloquée reste visible avec la dépendance exacte à fournir.</p></div></header>
        <div class="features">
          @for (feature of state.features; track feature.code) {
            <article>
              <div class="name"><strong>{{ feature.label }}</strong><code>{{ feature.code }}</code></div>
              <span [class]="featureClass(feature.status)">{{ feature.status }}</span>
              <div class="flags">
                <span [class.on]="feature.backendEndpointAvailable">Backend</span>
                <span [class.on]="feature.consultationAvailable">Consultation</span>
                <span [class.on]="feature.actionAvailable">Action</span>
                @if (feature.makerCheckerRequired) { <span>Maker/Checker</span> }
              </div>
              <p>{{ feature.limitation }}</p>
            </article>
          }
        </div>
      </section>
      <section class="message"><i class="pi pi-lock"></i><div><strong>Frontière membre appliquée</strong><p>Les actions non sûres restent fermées et aucune donnée fictive ne remplace les API manquantes.</p></div></section>
    } @else if (loading()) { <section class="message"><i class="pi pi-spin pi-spinner"></i>Chargement du domaine membre…</section> }
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; } h1 { margin:0; font-size:21px; } h1 i { color:var(--sg-color-primary); margin-right:9px; }
    h2 { margin:0; font-size:17px; } p { color:var(--sg-text-muted); margin:6px 0 0; } button { border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:9px 14px; background:var(--sg-bg-surface); color:var(--sg-text-primary); }
    .summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; margin-top:18px; } .summary div, .card, .message { background:var(--sg-bg-surface); border:1px solid var(--sg-border); border-radius:var(--sg-radius); }
    .summary div { padding:15px; display:flex; flex-direction:column; gap:7px; } .summary span, small { color:var(--sg-text-muted); } .card { margin-top:18px; padding:18px; } header { display:flex; justify-content:space-between; gap:15px; } header > code { max-width:310px; overflow:hidden; text-overflow:ellipsis; }
    .services { display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); gap:12px; margin-top:15px; } .services article { border:1px solid var(--sg-border); border-radius:var(--sg-radius); padding:14px; } .services article > div:first-child { display:flex; justify-content:space-between; gap:8px; } .service-name { display:flex; flex-direction:column; gap:4px; } .service-name code { color:var(--sg-text-muted); font-size:11px; }
    .tags, .flags { display:flex; flex-wrap:wrap; gap:5px; margin-top:10px; } .tags span, .flags span { font-size:11px; border-radius:12px; padding:4px 8px; background:var(--sg-bg-subtle); color:var(--sg-text-muted); } .flags .on { background:#e8f7ed; color:#16803c; }
    .features { margin-top:14px; } .features article { display:grid; grid-template-columns:minmax(210px,1.2fr) 105px minmax(230px,1fr) 2fr; gap:13px; align-items:center; padding:13px 0; border-top:1px solid var(--sg-border); } .features p { margin:0; font-size:12px; }
    .name { display:flex; flex-direction:column; gap:4px; } .name code { color:var(--sg-text-muted); font-size:11px; } .message { display:flex; gap:12px; padding:16px; margin-top:18px; } .message > i { color:var(--sg-color-primary); } .message.error { color:#b42318; border-color:#f3b5af; }
    .status-up, .feature-available { color:#16803c; } .status-degraded, .feature-blocked { color:#a15c00; } .status-down, .feature-unavailable { color:#b42318; } .status-unknown { color:var(--sg-text-muted); }
    @media (max-width:1050px) { .summary { grid-template-columns:1fr; } .features article { grid-template-columns:1fr 105px; } .features p, .flags { grid-column:1 / -1; } }
  `],
})
export class SwitchMemberDomainComponent implements OnInit {
  private readonly service = inject(SwitchMemberDomainService);
  private readonly route = inject(ActivatedRoute);
  readonly domain = this.route.snapshot.data['domain'] as string;
  readonly title = this.route.snapshot.data['title'] as string;
  readonly description = this.route.snapshot.data['description'] as string;
  readonly icon = this.route.snapshot.data['icon'] as string;
  readonly overview = signal<SwitchDomainOverview | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true); this.error.set(null);
    this.service.overview(this.domain).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: value => this.overview.set(value),
      error: () => this.error.set('Le domaine membre ou la session Switch est indisponible.'),
    });
  }
  statusClass(status: string): string { return `status-${status.toLowerCase()}`; }
  featureClass(status: string): string { return `feature-${status.toLowerCase()}`; }
}
