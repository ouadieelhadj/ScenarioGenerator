import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { MerchantActivityService } from '../../core/services/merchant-activity.service';
import {MerchantSynchronizationsService,PortalSourceFreshness} from '../../core/services/merchant-synchronizations.service';
import { PortalRecordKind, PortalFinancialFilter, PortalFinancialView, PortalFinancialRevision, PortalFinancialTotal, PortalFinancialDaily, PortalPage, PortalNode, PortalSettlementComposition, portalMoney } from '../../core/models/merchant-activity.models';

interface ActivitySeries { key: string; kind: PortalRecordKind; currency: string; status: string; days: PortalFinancialDaily[]; points: string; coordinates: {x: number; y: number}[] }

@Component({
  selector: 'app-merchant-activity', standalone: true, imports: [FormsModule, DatePipe],
  templateUrl: './merchant-activity.component.html', styleUrl: './merchant-activity.component.scss',
})
export class MerchantActivityComponent {
  private readonly api = inject(MerchantActivityService);
  private readonly synchronization = inject(MerchantSynchronizationsService);
  readonly sourceFreshness = signal<PortalSourceFreshness[]>([]);
  readonly freshnessWarning = signal('');
  private readonly onboarding = inject(MerchantOnboardingService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly kind = this.route.snapshot.data['kind'] as PortalRecordKind | undefined;
  readonly title = this.route.snapshot.data['activityTitle'] as string || "Tableau de bord d'activité";
  readonly exportEnabled = !!this.route.snapshot.data['exportEnabled'];
  readonly dashboard = !this.kind && !this.exportEnabled;
  readonly exportBusy = signal(false);
  readonly acquirers = signal<{code: string; name: string}[]>([]);
  readonly nodes = signal<PortalNode[]>([]);
  readonly result = signal<PortalPage<PortalFinancialView> | null>(null);
  readonly totals = signal<PortalFinancialTotal[]>([]);
  readonly detail = signal<PortalFinancialView | null>(null);
  readonly busy = signal(false);
  readonly detailBusy = signal(false);
  readonly error = signal('');
  readonly detailError = signal('');
  readonly series = signal<ActivitySeries[]>([]);
  readonly calculatedAt = signal('');
  readonly related = signal<PortalPage<PortalFinancialView> | null>(null);
  readonly relatedBusy = signal(false);
  readonly relatedError = signal('');
  readonly relationMode = signal<'direct' | 'history'>('direct');
  readonly composition = signal<PortalSettlementComposition | null>(null);
  readonly compositionBusy = signal(false);
  readonly compositionError = signal('');
  readonly revisions=signal<PortalPage<PortalFinancialRevision>|null>(null);
  readonly revisionsBusy=signal(false);
  readonly revisionsError=signal('');
  readonly selectedRevision=signal<PortalFinancialRevision|null>(null);
  selectedKind: PortalRecordKind | '' = ''; status = '';
  acquirer = ''; hierarchyNodeId = ''; currency = '';
  from = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
  to = new Date().toISOString().slice(0, 10);
  readonly money = portalMoney;
  private generation = 0;
  private detailGeneration = 0;
  private pendingDetailId = '';

  constructor() {
    this.onboarding.allowedAcquirers().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: values => {
        this.acquirers.set(values);
        this.acquirer = values[0]?.code || '';
        if (this.acquirer) this.applyQuery();
        else this.error.set('Aucun acquéreur actif n’est autorisé pour votre compte.');
      }, error: e => this.error.set(this.message(e)),
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => {
      if (this.acquirers().length) this.applyQuery();
    });
  }
  private applyQuery(): void {
    const params = this.route.snapshot.queryParamMap;
    const requested = params.get('acquirer');
    if (requested && !this.acquirers().some(a => a.code === requested)) {
      ++this.generation; this.closeDetail(); this.result.set(null); this.totals.set([]); this.series.set([]);
      this.busy.set(false); this.error.set('Cet acquéreur ne fait pas partie de vos accès autorisés.'); return;
    }
    this.acquirer = requested || this.acquirer;
    const id = params.get('record');
    if (id && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
      this.error.set('La référence de consultation n’est pas valide.'); return;
    }
    this.pendingDetailId = id || '';
    this.selectAcquirer();
  }
  selectAcquirer(): void {
    ++this.generation; ++this.detailGeneration;
    this.hierarchyNodeId = ''; this.nodes.set([]); this.result.set(null); this.totals.set([]); this.series.set([]); this.closeDetail();
    this.busy.set(false);
    if (!this.acquirer) return;
    const selected = this.acquirer;
    this.loadHierarchy(selected, 0, []);
    this.refresh();
  }
  private loadHierarchy(selected: string, page: number, collected: PortalNode[]): void {
    this.api.hierarchy(selected, page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => {
        if (selected !== this.acquirer) return;
        const all = [...collected, ...result.content];
        if (result.number + 1 < result.totalPages) this.loadHierarchy(selected, page + 1, all);
        else this.nodes.set(all);
      }, error: e => { if (selected === this.acquirer) this.error.set(this.message(e)); },
    });
  }
  refresh(page = 0): void {
    if (this.busy() || !this.acquirer) return;
    this.error.set(''); this.closeDetail();
    let filter: PortalFinancialFilter;
    try { filter = this.filter(); } catch { this.error.set('Choisissez une période valide d’un an au maximum.'); return; }
    const generation = ++this.generation;
    this.sourceFreshness.set([]);this.freshnessWarning.set('');
    this.synchronization.freshness(this.acquirer).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:r=>{if(generation===this.generation){this.sourceFreshness.set(r.filter(s=>!filter.kind||s.kind===filter.kind));
        if(!r.some(s=>!filter.kind||s.kind===filter.kind))this.freshnessWarning.set('Aucune synchronisation configurée pour ces données : leur fraîcheur n’est pas confirmée.');}},
      error:()=>{if(generation===this.generation)this.freshnessWarning.set('Fraîcheur source non vérifiable. Les dernières données confirmées restent affichées.');},
    });
    this.busy.set(true);
    forkJoin({ records: this.api.records(this.acquirer, filter, page),
      totals: this.dashboard ? of([] as PortalFinancialTotal[]) : this.api.totals(this.acquirer, filter),
      summary: this.dashboard ? this.api.summary(this.acquirer, filter) : of(null) })
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: value => {
          if (generation !== this.generation) return;
          this.result.set(value.records); this.totals.set(value.summary?.totals || value.totals);
          this.series.set(this.makeSeries(value.summary?.daily || []));
          this.calculatedAt.set(value.summary?.calculatedAt || ''); this.busy.set(false);
          if (this.pendingDetailId) { const id = this.pendingDetailId; this.pendingDetailId = ''; this.openDetailId(id); }
        }, error: e => {
          if (generation !== this.generation) return;
          this.error.set(this.message(e)); this.result.set(null); this.totals.set([]); this.series.set([]); this.busy.set(false);
        },
      });
  }
  openDetail(record: PortalFinancialView): void {
    this.openDetailId(record.id);
  }
  openDetailId(id: string): void {
    const generation = ++this.detailGeneration;
    this.revisions.set(null);this.revisionsBusy.set(false);this.revisionsError.set('');this.selectedRevision.set(null);
    this.relationMode.set('direct');
    this.composition.set(null); this.compositionBusy.set(false); this.compositionError.set('');
    this.detail.set(null); this.detailError.set(''); this.related.set(null); this.relatedError.set(''); this.relatedBusy.set(false); this.detailBusy.set(true);
    this.api.detail(this.acquirer, id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => { if (generation === this.detailGeneration) { this.detail.set(result); this.detailBusy.set(false); this.loadRelated(); if(result.data.kind==='SETTLEMENT')this.loadComposition(); } },
      error: e => { if (generation === this.detailGeneration) { this.detailError.set(this.message(e)); this.detailBusy.set(false); } },
    });
  }
  loadRelated(page = 0): void {
    const value = this.detail(); if (!value || this.relatedBusy()) return;
    const generation = this.detailGeneration; this.relatedBusy.set(true); this.relatedError.set('');
    const request = this.relationMode() === 'history' ? this.api.history(this.acquirer, value.id, page) : this.api.related(this.acquirer, value.id, page);
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => { if (generation === this.detailGeneration) { this.related.set(result); this.relatedBusy.set(false); } },
      error: e => { if (generation === this.detailGeneration) { this.related.set(null); this.relatedError.set(this.message(e)); this.relatedBusy.set(false); } },
    });
  }
  closeDetail(): void {
    ++this.detailGeneration; this.detail.set(null); this.detailBusy.set(false); this.detailError.set('');
    this.revisions.set(null);this.revisionsBusy.set(false);this.revisionsError.set('');this.selectedRevision.set(null);
    this.related.set(null); this.relatedBusy.set(false); this.relatedError.set('');
    this.composition.set(null); this.compositionBusy.set(false); this.compositionError.set('');
  }
  changeRelations(mode: 'direct' | 'history'): void {
    if (this.relatedBusy()) return;
    this.relationMode.set(mode); this.related.set(null); this.loadRelated();
  }
  loadRevisions(page=0): void {
    const current=this.detail();if(!current||this.revisionsBusy())return;
    const generation=this.detailGeneration;this.revisionsBusy.set(true);this.revisionsError.set('');this.selectedRevision.set(null);
    this.api.revisions(this.acquirer,current.id,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:r=>{if(generation===this.detailGeneration){this.revisions.set(r);this.revisionsBusy.set(false);}},
      error:e=>{if(generation===this.detailGeneration){this.revisions.set(null);this.revisionsError.set(this.message(e));this.revisionsBusy.set(false);}},
    });
  }
  revisionReason(reason: string): string {
    return ({INITIAL_IMPORT:'Premier import observé',SOURCE_UPDATE:'Mise à jour de la source',EXISTING_BASELINE:'Version déjà présente avant suivi'} as Record<string,string>)[reason]||'Origine à vérifier';
  }
  loadComposition(page = 0): void {
    const current=this.detail(); if(!current||current.data.kind!=='SETTLEMENT'||this.compositionBusy())return;
    const generation=this.detailGeneration;this.compositionBusy.set(true);this.compositionError.set('');
    this.api.composition(this.acquirer,current.id,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:r=>{if(generation===this.detailGeneration){this.composition.set(r);this.compositionBusy.set(false);}},
      error:e=>{if(generation===this.detailGeneration){this.composition.set(null);this.compositionError.set(this.message(e));this.compositionBusy.set(false);}},
    });
  }
  compositionStatus(status: string): string {
    return ({NOT_RECEIVED:'Composition non reçue',PARTIAL:'Composition partielle — aucune concordance complète confirmée',
      COMPLETE:'Composition complète contrôlée — pas une confirmation bancaire',STALE:'Source modifiée — composition à réimporter'} as Record<string,string>)[status]||'Composition à vérifier';
  }
  compositionRole(role: string): string {return ({GROSS:'Opération brute',FEE:'Frais appliqué',ADJUSTMENT:'Ajustement'} as Record<string,string>)[role]||'Nature à vérifier';}
  drilldown(total: PortalFinancialTotal): void {
    if (!this.dashboard) return;
    this.selectedKind = total.kind; this.currency = total.currency; this.status = total.status; this.refresh();
  }
  clearDrilldown(): void { this.selectedKind = ''; this.status = ''; this.currency = ''; this.refresh(); }
  statuses(): string[] {
    const kind = this.kind || this.selectedKind;
    if (!kind) return [];
    return ({ AUTHORIZATION: ['APPROVED', 'DECLINED', 'PENDING', 'REVERSED', 'EXPIRED', 'UNKNOWN'],
      TRANSACTION: ['RECEIVED', 'AUTHORIZED', 'CAPTURED', 'CLEARED', 'REVERSED', 'REFUNDED', 'DECLINED', 'PENDING', 'UNKNOWN'],
      FEE: ['ESTIMATED', 'APPLIED', 'REVERSED', 'UNKNOWN'],
      SETTLEMENT: ['PLANNED', 'PROCESSING', 'EXECUTED', 'BANK_CONFIRMED', 'FAILED', 'CANCELLED', 'UNKNOWN'] })[kind];
  }
  private makeSeries(days: PortalFinancialDaily[]): ActivitySeries[] {
    const grouped = new Map<string, ActivitySeries>();
    for (const day of days) {
      const key = day.kind + '|' + day.currency + '|' + day.status;
      const current = grouped.get(key) || { key, kind: day.kind, currency: day.currency, status: day.status, days: [], points: '', coordinates: [] };
      current.days.push(day); grouped.set(key, current);
    }
    const units = (value: string) => {
      const negative = value.startsWith('-'); const [whole, fraction = ''] = value.replace(/^-/, '').split('.');
      return BigInt(whole + fraction.padEnd(4, '0')) * (negative ? -1n : 1n);
    };
    const from = Date.parse(this.from + 'T00:00:00Z'); const to = Date.parse(this.to + 'T00:00:00Z');
    for (const item of grouped.values()) {
      item.days.sort((a, b) => a.date.localeCompare(b.date));
      const amounts = item.days.map(d => units(d.amount));
      const maximum = amounts.reduce((m, n) => (n < 0n ? -n : n) > m ? (n < 0n ? -n : n) : m, 1n);
      // Only normalized drawing coordinates use Number; monetary labels never do.
      item.coordinates = item.days.map((d, index) => {
        const x = to === from ? 150 : 10 + 280 * (Date.parse(d.date + 'T00:00:00Z') - from) / (to - from);
        const y = 60 - Number(amounts[index] * 4500n / maximum) / 100;
        return {x, y};
      });
      item.points = item.coordinates.map(p => `${p.x},${p.y}`).join(' ');
    }
    return [...grouped.values()];
  }
  exportStatement(format: 'PDF' | 'XLSX'): void {
    if (this.exportBusy() || !this.acquirer || !this.exportEnabled) return;
    let filter: PortalFinancialFilter;
    try { filter = this.filter(); } catch { this.error.set('Choisissez une période valide avant de télécharger le relevé.'); return; }
    this.exportBusy.set(true); this.error.set('');
    const selectedAcquirer = this.acquirer;
    this.api.statement(this.acquirer, filter, format).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: blob => {
        if (selectedAcquirer !== this.acquirer) { this.exportBusy.set(false); return; }
        const address = URL.createObjectURL(blob); const anchor = document.createElement('a');
        anchor.href = address; anchor.download = 'releve-futurpayment.' + (format === 'PDF' ? 'pdf' : 'xlsx');
        anchor.click(); URL.revokeObjectURL(address); this.exportBusy.set(false);
      }, error: e => { if (selectedAcquirer === this.acquirer) this.error.set(this.message(e)); this.exportBusy.set(false); },
    });
  }
  nodeLabel(id: string): string { return this.nodes().find(n => n.id === id)?.label || 'Périmètre autorisé'; }
  labelKind(kind: PortalRecordKind|'HIERARCHY'|'CHARGEBACK'): string {
    return { AUTHORIZATION: 'Autorisations', TRANSACTION: 'Transactions', FEE: 'Frais appliqués', SETTLEMENT: 'Règlements',
      HIERARCHY: 'Hiérarchie commerçante', CHARGEBACK: 'Litiges et chargebacks' }[kind];
  }
  labelStatus(status: string): string {
    return ({ APPROVED: 'Acceptée', DECLINED: 'Refusée', PENDING: 'En attente', REVERSED: 'Annulée', EXPIRED: 'Expirée',
      RECEIVED: 'Reçue', AUTHORIZED: 'Autorisée', CAPTURED: 'Capturée', CLEARED: 'Compensée', REFUNDED: 'Remboursée',
      ESTIMATED: 'Estimé', APPLIED: 'Appliqué', PLANNED: 'Prévu', PROCESSING: 'En traitement', EXECUTED: 'Exécuté',
      BANK_CONFIRMED: 'Crédit bancaire confirmé', FAILED: 'Échec', CANCELLED: 'Annulé', UNKNOWN: 'État source à vérifier' } as Record<string,string>)[status] || status;
  }
  private filter(): PortalFinancialFilter {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(this.from) || !/^\d{4}-\d{2}-\d{2}$/.test(this.to)) throw new Error();
    const from = new Date(this.from + 'T00:00:00Z'); const end = new Date(this.to + 'T00:00:00Z');
    end.setUTCDate(end.getUTCDate() + 1);
    const duration = end.getTime() - from.getTime();
    if (!Number.isFinite(duration) || duration <= 0 || duration > 366 * 86400000) throw new Error();
    return { from: from.toISOString(), to: end.toISOString(), kind: this.kind || this.selectedKind || undefined,
      status: this.status || undefined,
      hierarchyNodeId: this.hierarchyNodeId || undefined, currency: this.currency.trim() || undefined };
  }
  private message(e: HttpErrorResponse): string {
    if (e.status === 401) return 'Votre session a expiré. Reconnectez-vous.';
    if (e.status === 403) return 'Votre compte ne possède pas les droits sur ce périmètre.';
    if (e.status === 400) return 'Les filtres saisis ne sont pas valides.';
    return 'La consultation est indisponible. Les données n’ont pas été remplacées par des montants nuls.';
  }
}
