import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';
import { FraudAlertView, FraudCaseView, FraudOverview } from '../../core/models/product-contracts.models';
import { FraudWorkspaceService } from '../../core/services/fraud-workspace.service';

@Component({
  selector: 'app-fraud-workspace', standalone: true, imports: [DatePipe],
  template: `
    <div class="page-header"><div><p class="eyebrow">{{ workspace() === 'SWITCH' ? 'Switch · production' : 'SwitchLab · laboratoire' }}</p><h1><i class="pi pi-shield"></i> Fraud Monitoring</h1><p>{{ subtitle() }}</p></div><button type="button" (click)="load()" [disabled]="loading()"><i [class]="loading() ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i> Actualiser</button></div>
    @if (error()) { <section class="notice error"><i class="pi pi-exclamation-triangle"></i>{{ error() }}</section> }
    @if (overview(); as state) {
      <section class="summary"><div><span>État plateforme</span><strong [class]="statusClass(state.overallStatus)">{{ state.overallStatus }}</strong></div><div><span>Mode</span><strong>{{ state.operatingMode }}</strong></div><div><span>Observation</span><strong>{{ state.checkedAt | date:'medium' }}</strong></div></section>
      <section class="notice" [class.warning]="!state.platformConfigured"><i class="pi pi-info-circle"></i><div><strong>{{ state.platformConfigured ? 'Plateforme configurée' : 'Plateforme non configurée' }}</strong><p>{{ state.platformConfigured ? 'Les disponibilités ci-dessous proviennent de sg-fraud-platform.' : 'Aucune capacité n’est simulée. Configurez l’URL de sg-fraud-platform pour poursuivre.' }}</p></div></section>
      <section class="grid">@for (feature of state.features; track feature.code) { <article><header><div><strong>{{ feature.label }}</strong><code>{{ feature.code }}</code></div><span [class.available]="feature.available">{{ feature.status }}</span></header>@if (feature.limitation) { <p>{{ feature.limitation }}</p> }</article> }</section>
      @if (workspace() === 'SWITCH' && overview()?.platformConfigured) {
        <section class="ops"><article><h2>Alertes réelles</h2><strong>{{ alerts().length }}</strong><p>Derniers signaux remontés par la plateforme, sans décision de blocage.</p>@for (item of alerts().slice(0,5); track item.id) { <div class="row"><code>{{ item.transactionReference }}</code><span>{{ item.score }} · {{ item.band }}</span></div> }</article><article><h2>Dossiers d’investigation</h2><strong>{{ cases().length }}</strong><p>Dossiers ouverts et isolés pour le membre connecté.</p>@for (item of cases().slice(0,5); track item.id) { <div class="row"><span>{{ item.title }}</span><code>{{ item.status }}</code></div> }</article></section>
      }
      @if (workspace() === 'SWITCHLAB' && overview()?.platformConfigured) { <section class="notice"><i class="pi pi-chart-line"></i><div><strong>Laboratoire raccordé</strong><p>Les injections batch, backtests et métriques passent par la même chaîne de scoring ALERT_ONLY. Aucun volume ni résultat n’est affiché sans campagne exécutée.</p></div></section> }      <section class="guard"><i class="pi pi-lock"></i><div><strong>Protection fail-closed</strong><p>{{ guardText() }}</p><small>Corrélation : {{ state.correlationId }}</small></div></section>
    } @else if (loading()) { <section class="notice"><i class="pi pi-spin pi-spinner"></i> Chargement du sous-module fraude…</section> }
  `,
  styles: [`
    .page-header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.eyebrow{margin:0 0 5px;color:var(--sg-color-primary);font-weight:700;text-transform:uppercase;font-size:11px}h1{margin:0;font-size:22px;color:var(--sg-text-primary)}h1 i{color:var(--sg-color-primary);margin-right:9px}p{color:var(--sg-text-muted);margin:6px 0 0}button{border:1px solid var(--sg-border-strong);border-radius:var(--sg-radius);padding:9px 14px;background:var(--sg-bg-surface);color:var(--sg-text-primary)}
    .summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:18px}.summary div,.notice,.grid article,.guard{background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:var(--sg-radius)}.summary div{padding:15px;display:flex;flex-direction:column;gap:7px}.summary span{color:var(--sg-text-muted)}
    .notice,.guard{display:flex;gap:12px;align-items:flex-start;padding:16px;margin-top:18px}.notice>i,.guard>i{color:var(--sg-color-primary);font-size:18px}.notice.warning{border-color:#e7ba63}.notice.error{color:#b42318;border-color:#f3b5af}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px;margin-top:18px}.grid article{padding:15px}.grid header{display:flex;justify-content:space-between;gap:12px}.grid header div{display:flex;flex-direction:column;gap:5px}.grid code{font-size:11px;color:var(--sg-text-muted)}.grid span{font-size:11px;color:#b42318}.grid span.available{color:#16803c}.guard small{display:block;margin-top:8px;color:var(--sg-text-muted)}.ops{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:18px}.ops article{background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:var(--sg-radius);padding:15px}.ops h2{font-size:15px;margin:0 0 8px}.ops article>strong{font-size:26px;color:var(--sg-color-primary)}.row{display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-top:1px solid var(--sg-border);font-size:12px}.status-up{color:#16803c}.status-down{color:#b42318}.status-unknown,.status-degraded{color:#a15c00}@media(max-width:800px){.summary{grid-template-columns:1fr}.page-header{flex-direction:column}}
  `],
})
export class FraudWorkspaceComponent implements OnInit {
  private readonly route = inject(ActivatedRoute); private readonly fraud = inject(FraudWorkspaceService);
  readonly workspace = signal<'SWITCH' | 'SWITCHLAB'>('SWITCH'); readonly overview = signal<FraudOverview | null>(null); readonly alerts = signal<FraudAlertView[]>([]); readonly cases = signal<FraudCaseView[]>([]); readonly loading = signal(false); readonly error = signal<string | null>(null);
  ngOnInit(): void { this.workspace.set(this.route.snapshot.data['workspace'] === 'SWITCHLAB' ? 'SWITCHLAB' : 'SWITCH'); this.load(); }
  load(): void { this.loading.set(true); this.error.set(null); this.fraud.overview(this.workspace()).pipe(finalize(()=>this.loading.set(false))).subscribe({next:value=>{this.overview.set(value);if(value.platformConfigured&&this.workspace()==='SWITCH'){this.fraud.alerts(this.workspace()).subscribe({next:items=>this.alerts.set(items),error:()=>this.alerts.set([])});this.fraud.cases(this.workspace()).subscribe({next:items=>this.cases.set(items),error:()=>this.cases.set([])});}},error:()=>this.error.set('Le BFF ou la session du produit est indisponible.')}); }
  subtitle(): string { return this.workspace()==='SWITCH' ? 'Alertes, scores, investigations et feedback analyste du membre.' : 'Scénarios, injection synthétique, métriques et preuves du POC.'; }
  guardText(): string { return this.workspace()==='SWITCH' ? 'Le mode initial est ALERT_ONLY : ce sous-module ne bloque aucune transaction.' : 'Aucune donnée sensible, aucun routage externe et aucune capacité fictive.'; }
  statusClass(status:string): string { return `status-${status.toLowerCase()}`; }
}
