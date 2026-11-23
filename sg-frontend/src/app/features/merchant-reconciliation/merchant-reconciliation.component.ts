import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { MerchantActivityService } from '../../core/services/merchant-activity.service';
import { MerchantReconciliationService } from '../../core/services/merchant-reconciliation.service';
import { PortalNode, PortalPage, portalMoney } from '../../core/models/merchant-activity.models';
import { PortalCredit, PortalCreditCorrection, PortalSettlementReconciliation, ReconciliationFilter, ReconciliationPermissions } from '../../core/models/merchant-reconciliation.models';
@Component({selector: 'app-merchant-reconciliation', standalone: true, imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './merchant-reconciliation.component.html', styleUrl: './merchant-reconciliation.component.scss'})
export class MerchantReconciliationComponent {
  private readonly api = inject(MerchantReconciliationService);
  private readonly onboarding = inject(MerchantOnboardingService);
  private readonly activity = inject(MerchantActivityService);
  private readonly destroy = inject(DestroyRef);
  readonly acquirers = signal<{code: string; name: string}[]>([]);
  readonly nodes = signal<PortalNode[]>([]);
  readonly settlements = signal<PortalPage<PortalSettlementReconciliation> | null>(null);
  readonly credits = signal<PortalPage<PortalCredit> | null>(null);
  readonly detail = signal<PortalSettlementReconciliation | null>(null);
  readonly creditDetail = signal<PortalCredit | null>(null);
  readonly corrections = signal<PortalCreditCorrection[]>([]);
  readonly correctionPermissions = signal<ReconciliationPermissions>({maker:false,checker:false});
  correctionFile: File | null=null; correctionReason=''; correctionRequestId='';
  readonly permissions = signal<ReconciliationPermissions>({maker: false, checker: false});
  readonly importPermission = signal(false);
  readonly bankOperations = signal(false);
  readonly accessReady = signal(false);
  readonly busy = signal(false); readonly actionBusy = signal(false);
  readonly error = signal(''); readonly notice = signal('');
  acquirer = ''; nodeId = ''; currency = ''; tab: 'settlements' | 'credits' = 'settlements';
  from = new Date(Date.now() - 30 * 86400000).toISOString().slice(0,10);
  to = new Date().toISOString().slice(0,10);
  selectedCreditId = ''; justification = ''; file: File | null = null;
  readonly money = portalMoney;
  private generation = 0; private detailGeneration = 0;
  constructor() {
    this.onboarding.allowedAcquirers().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: values => { this.acquirers.set(values); this.acquirer = values[0]?.code || ''; this.selectAcquirer(); },
      error: e => this.error.set(this.message(e)),
    });
  }
  selectAcquirer(): void {
    ++this.generation; this.closeDetail(); this.settlements.set(null); this.credits.set(null); this.nodes.set([]);
    this.importPermission.set(false); this.bankOperations.set(false); this.accessReady.set(false); this.tab='settlements'; this.nodeId = ''; this.busy.set(false); this.error.set(''); this.notice.set(''); this.file = null;
    if (!this.acquirer) { this.error.set('Aucun acquéreur actif autorisé.'); return; }
    const code=this.acquirer,current=this.generation;
    this.busy.set(true);
    this.api.access(code).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:value=>{
      if(code!==this.acquirer || current!==this.generation)return;
      this.bankOperations.set(value.bankOperations);this.accessReady.set(true);this.busy.set(false);this.loadNodes(code,0,[]);this.refresh();
    },error:e=>{if(code===this.acquirer && current===this.generation){this.busy.set(false);this.error.set(this.message(e));}}});
  }
  private loadNodes(code: string, page: number, collected: PortalNode[]): void {
    this.activity.hierarchy(code, page).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: result => {
      if (code !== this.acquirer) return;
      const all = [...collected, ...result.content];
      if (result.number + 1 < result.totalPages) this.loadNodes(code, page + 1, all);
      else { this.nodes.set(all); this.checkImportPermission(); }
    }, error: e => { if (code === this.acquirer) this.error.set(this.message(e)); }});
  }
  checkImportPermission(): void {
    this.importPermission.set(false); const id = this.nodeId || this.nodes()[0]?.id; const code = this.acquirer;
    if (!id || !code || !this.bankOperations()) return;
    this.api.permissions(code, id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => {
      if (code === this.acquirer && id === (this.nodeId || this.nodes()[0]?.id)) this.importPermission.set(value.maker);
    }, error: () => this.importPermission.set(false)});
  }
  refresh(page = 0, mode: 'both' | 'settlements' | 'credits' = 'both'): void {
    if (this.busy() || !this.acquirer || !this.accessReady()) return;
    let filter: ReconciliationFilter;
    try { filter = this.filter(); } catch { this.error.set('Choisissez une période valide d’un an maximum.'); return; }
    const current = ++this.generation; this.busy.set(true); this.error.set(''); this.closeDetail();
    const settlementPage = mode === 'credits' ? this.settlements()?.number || 0 : page;
    const creditPage = mode === 'settlements' ? this.credits()?.number || 0 : page;
    forkJoin({settlements: this.api.settlements(this.acquirer,filter,settlementPage),
      credits: this.bankOperations() ? this.api.credits(this.acquirer,filter,creditPage) : of(null)})
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => {
        if (current !== this.generation) return;
        this.settlements.set(value.settlements); this.credits.set(value.credits); this.busy.set(false);
      }, error: e => { if (current === this.generation) {
        this.error.set(this.message(e)); this.busy.set(false); this.settlements.set(null); this.credits.set(null);
      } }});
  }
  openDetail(row: PortalSettlementReconciliation): void {
    const generation = ++this.detailGeneration; this.detail.set(row); this.justification = ''; this.selectedCreditId = row.candidates[0]?.id || '';
    this.permissions.set({maker: false, checker: false}); const code = this.acquirer;
    this.api.permissions(code, row.hierarchyNodeId).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => {
      if (code === this.acquirer && generation === this.detailGeneration) this.permissions.set(value);
    }, error: e => { if (generation === this.detailGeneration) this.error.set(this.message(e)); }});
  }
  closeDetail(): void {
    ++this.detailGeneration; this.detail.set(null); this.permissions.set({maker: false,checker: false});
    this.creditDetail.set(null); this.corrections.set([]); this.correctionPermissions.set({maker:false,checker:false});
    this.correctionFile=null; this.correctionReason=''; this.correctionRequestId='';
  }
  openCredit(row: PortalCredit): void {
    this.closeDetail(); const generation=++this.detailGeneration,code=this.acquirer;
    this.creditDetail.set(row); this.correctionRequestId=crypto.randomUUID();
    forkJoin({permissions:this.api.permissions(code,row.data.hierarchyNodeId),history:this.api.corrections(code,row.id)})
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({next:value=>{
        if (code===this.acquirer && generation===this.detailGeneration) {this.correctionPermissions.set(value.permissions);this.corrections.set(value.history);}
      },error:e=>{if(generation===this.detailGeneration)this.error.set(this.message(e));}});
  }
  chooseCorrectionFile(event:Event): void {
    this.correctionFile=(event.target as HTMLInputElement).files?.[0] || null;
    // A new document is a new command; retries of the same document retain the request identifier.
    this.correctionRequestId=crypto.randomUUID();
  }
  correctionChanged(): void {this.correctionRequestId=crypto.randomUUID();}
  pendingCorrection(): boolean {return this.corrections().some(r=>r.status==='PROPOSED');}
  proposeCorrection(): void {
    const credit=this.creditDetail(),file=this.correctionFile;
    if(!credit || !file || !this.correctionPermissions().maker || this.actionBusy())return;
    if(!file.size || file.size>1048576){this.error.set('Le relevé corrigé ne doit pas dépasser 1 Mio.');return;}
    if(!this.correctionReason.trim() || this.correctionReason.trim().length>1000){this.error.set('Justifiez la correction, sans donnée sensible.');return;}
    const code=this.acquirer;this.actionBusy.set(true);this.error.set('');
    this.correctionRequestId=this.correctionRequestId || crypto.randomUUID();
    this.api.proposeCorrection(code,credit,this.correctionRequestId,file,this.correctionReason.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{this.actionBusy.set(false);if(code===this.acquirer){this.notice.set('Correction proposée. Le crédit effectif reste inchangé avant validation indépendante.');this.refresh();}},
      error:e=>{this.actionBusy.set(false);if(code===this.acquirer)this.error.set(this.message(e));}
    });
  }
  decideCorrection(row:PortalCreditCorrection,approve:boolean): void {
    if(!this.correctionPermissions().checker || this.actionBusy())return;
    if(!this.correctionReason.trim() || this.correctionReason.trim().length>1000){this.error.set('Justifiez la décision, sans donnée sensible.');return;}
    const code=this.acquirer;this.actionBusy.set(true);this.error.set('');
    this.api.decideCorrection(code,row,approve,this.correctionReason.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{this.actionBusy.set(false);if(code===this.acquirer){this.notice.set(approve ? 'Correction validée. Les rapprochements antérieurs doivent être revérifiés.' : 'Correction rejetée. La version effective reste inchangée.');this.refresh();}},
      error:e=>{this.actionBusy.set(false);if(code===this.acquirer)this.error.set(this.message(e));}
    });
  }
  candidateCredits(): PortalCredit[] {
    const row = this.detail(); if (!row) return [];
    const all = [...row.candidates, ...(this.credits()?.content || []).filter(c => c.data.currency === row.currency && c.reconciliationStatus === 'UNMATCHED_CREDIT')];
    return [...new Map(all.map(c => [c.id,c])).values()];
  }
  propose(): void {
    const row = this.detail(); if (!row || !this.selectedCreditId || this.actionBusy() || !this.permissions().maker) return;
    if (!this.validReason()) return;
    const code = this.acquirer; this.actionBusy.set(true); this.error.set('');
    this.api.propose(code, row.id, this.selectedCreditId, this.justification.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.actionBusy.set(false); if (code === this.acquirer) { this.notice.set('Proposition enregistrée. Une validation indépendante est nécessaire.'); this.refresh(); } },
      error: e => { this.actionBusy.set(false); if (code === this.acquirer) this.error.set(this.message(e)); },
    });
  }
  decide(approve: boolean): void {
    const match = this.detail()?.match; if (!match || this.actionBusy() || !this.permissions().checker) return;
    if (!this.validReason()) return;
    const code = this.acquirer; this.actionBusy.set(true); this.error.set('');
    this.api.decide(code, match, approve, this.justification.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.actionBusy.set(false); if (code === this.acquirer) {
        this.notice.set(approve ? 'Rapprochement validé en interne. Le statut du système d’acquisition reste inchangé.' : 'Proposition rejetée ou validation périmée invalidée.'); this.refresh();
      } }, error: e => { this.actionBusy.set(false); if (code === this.acquirer) this.error.set(this.message(e)); },
    });
  }
  chooseFile(event: Event): void { this.file = (event.target as HTMLInputElement).files?.[0] || null; }
  importStatement(): void {
    if (!this.file || !this.importPermission() || this.actionBusy()) return;
    if (!this.file.size || this.file.size > 1048576) { this.error.set('Le relevé ne doit pas dépasser 1 Mio.'); return; }
    const code = this.acquirer; this.actionBusy.set(true); this.error.set('');
    this.api.importStatement(code, this.file).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: result => {
      this.actionBusy.set(false); if (code === this.acquirer) {
        this.notice.set(`${result.created} crédit(s) importé(s), ${result.alreadyPresent} déjà présent(s). Aucun rapprochement automatique.`); this.file = null; this.refresh();
      }
    }, error: e => {this.actionBusy.set(false); if (code === this.acquirer) this.error.set(this.message(e));}});
  }
  downloadTemplate(): void {
    const blob = new Blob(['source_system,source_credit_id,hierarchy_node_id,payment_reference,booked_at,amount,currency\n'], {type:'text/csv;charset=utf-8'});
    const address = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = address;
    anchor.download = 'modele-releve-portail-v1.csv'; anchor.click(); URL.revokeObjectURL(address);
  }
  label(status: string): string { return ({MISSING_CREDIT:'Crédit manquant', UNMATCHED_CREDIT:'Crédit non rapproché', AMBIGUOUS:'Plusieurs crédits possibles',
    EXACT_PROPOSAL:'Correspondance à vérifier', AMOUNT_DIFFERENCE:'Écart de montant', PROPOSED:'Attente de validation', VALIDATED:'Validé en interne',
    REJECTED:'Rejeté', APPROVED:'Correction validée', INVALIDATED:'Invalidé', STALE:'Source modifiée — à reprendre', NOT_EXECUTED:'Règlement non exécuté',
    NOT_RECONCILED:'Rapprochement en attente', CANDIDATE_LIMIT:'Trop de correspondances — préciser la recherche'} as Record<string,string>)[status] || status; }
  private validReason() { if (!this.justification.trim() || this.justification.trim().length > 1000) {
    this.error.set('Une justification de 1 à 1 000 caractères, sans donnée sensible, est obligatoire.'); return false;
  } return true; }
  private filter(): ReconciliationFilter {
    const from = new Date(this.from+'T00:00:00Z'), to = new Date(this.to+'T00:00:00Z'); to.setUTCDate(to.getUTCDate()+1);
    const duration = to.getTime()-from.getTime(); if (!Number.isFinite(duration) || duration <= 0 || duration > 366*86400000) throw new Error();
    return {from:from.toISOString(),to:to.toISOString(),hierarchyNodeId:this.nodeId || undefined,currency:this.currency.trim() || undefined};
  }
  private message(e: HttpErrorResponse) {
    if (e.status === 401) return 'Votre session a expiré. Reconnectez-vous.';
    if (e.status === 403) return 'Votre compte ne possède pas les droits nécessaires sur ce périmètre.';
    if (e.status === 409) return 'Action refusée : auto-approbation, crédit déjà affecté ou source/version modifiée. Rechargez le détail.';
    if (e.status === 400) return 'Fichier, paire de rapprochement ou filtres invalides. Aucun import partiel n’a été validé.';
    return 'Le rapprochement est indisponible. Aucune validation fictive n’a été produite.';
  }
}
