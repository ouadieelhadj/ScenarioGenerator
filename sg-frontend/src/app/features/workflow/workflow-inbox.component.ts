import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { WorkflowService } from '../../core/services/workflow.service';
import { WorkflowRequestSummary } from '../../core/models/workflow.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({
  selector: 'app-workflow-inbox',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <section>
      <h1><i class="pi pi-check-square"></i> {{ titleKey | translate }}</h1>
      @if (mode === 'approvals') { <button class="batch" (click)="runBatch()">Executer le batch valide</button> }
      @if (message()) { <div class="notice" role="status">{{ message() }}</div> }
      @if (loading()) {
        <div class="state"><i class="pi pi-spin pi-spinner"></i> {{ 'common.loading' | translate }}</div>
      } @else if (unavailable()) {
        <div class="notice" role="status">
          <i class="pi pi-info-circle"></i>
          <div><strong>{{ 'workflow.foundationTitle' | translate }}</strong><p>{{ 'workflow.foundationDetail' | translate }}</p></div>
        </div>
      } @else if (!requests().length) {
        <div class="state">{{ 'workflow.none' | translate }}</div>
      } @else {
        <div class="card">
          @for (request of requests(); track request.id) {
            <article><div><strong>{{ request.operationType }}</strong><small>{{ request.objectReference }}</small></div><span>{{ request.createdBy }}</span><span>{{ request.status }}</span>
              @if (mode === 'approvals' && request.status === 'PENDING') { <div class="actions"><button (click)="approve(request)">Approuver</button><button class="danger" (click)="reject(request)">Rejeter</button></div> }
              @if (approvedCase() === request.caseId) { <div class="actions"><button (click)="provision(request,'IMMEDIATE')">Provisionner maintenant</button><button (click)="provision(request,'BATCH')">Mettre en batch</button></div> }
            </article>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    h1 { color:var(--sg-text-primary); }
    .state, .notice, .card { padding:1rem; border:1px solid var(--sg-border); border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }.batch{margin:0 0 1rem}
    .state { color:var(--sg-text-secondary); text-align:center; }
    .notice { display:flex; gap:.75rem; } .notice i { color:var(--sg-color-primary); }
    .notice p { color:var(--sg-text-secondary); margin:.35rem 0 0; }
    article { display:grid; grid-template-columns:2fr 1fr 1fr auto; gap:1rem; align-items:center; padding:.75rem; border-bottom:1px solid var(--sg-border); } article div{display:grid}small{color:var(--sg-text-secondary)}button{padding:.5rem .7rem;border:0;border-radius:7px;background:var(--sg-color-primary);color:#fff;cursor:pointer}.danger{background:#b42318}.actions{display:flex!important;gap:.4rem}
  `],
})
export class WorkflowInboxComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private service = inject(WorkflowService);
  private onboarding = inject(MerchantOnboardingService);
  readonly requests = signal<WorkflowRequestSummary[]>([]);
  readonly loading = signal(true);
  readonly unavailable = signal(false);
  readonly approvedCase = signal<string | null>(null);
  readonly message = signal('');
  readonly mode = this.route.snapshot.data['mode'] as 'operations' | 'approvals';
  readonly titleKey = this.mode === 'approvals' ? 'workflow.myApprovals' : 'workflow.myOperations';

  ngOnInit(): void {
    const source = this.mode === 'approvals' ? this.service.myApprovals() : this.service.myOperations();
    source.subscribe({
      next: requests => { this.requests.set(requests); this.loading.set(false); },
      error: () => { this.unavailable.set(true); this.loading.set(false); },
    });
  }

  approve(request: WorkflowRequestSummary): void { this.service.approve(request.id).subscribe({ next: () => { this.approvedCase.set(request.caseId ?? null); request.status='APPROVED'; this.requests.set([...this.requests()]); }, error: () => this.unavailable.set(true) }); }
  reject(request: WorkflowRequestSummary): void { const reason = window.prompt('Motif du rejet'); if (!reason) return; this.service.reject(request.id, reason).subscribe({ next: () => { request.status='REJECTED'; this.requests.set([...this.requests()]); }, error: () => this.unavailable.set(true) }); }
  provision(request: WorkflowRequestSummary, mode: 'IMMEDIATE'|'BATCH'): void { if (!request.caseId) return; this.onboarding.provision(request.caseId, mode).subscribe({ next: result => { this.approvedCase.set(null); this.showProvisioningResult(mode, result); }, error: () => this.unavailable.set(true) }); }
  runBatch(): void { this.onboarding.runBatch().subscribe({ next: results => { const terminalIds = results.flatMap(item => item.result?.terminals?.map(terminal => terminal.terminalId) ?? []); const mids = results.map(item => item.result?.merchantAcceptorId).filter(Boolean); this.message.set(`Batch termine : ${results.length} dossier(s), MID ${mids.join(', ') || 'non genere'}, TID ${terminalIds.join(', ') || 'non genere'}.`); }, error: () => this.unavailable.set(true) }); }
  private showProvisioningResult(mode: 'IMMEDIATE'|'BATCH', result: import('../../core/models/merchant-onboarding.models').MerchantProvisioningView): void { const tids = result.result?.terminals?.map(terminal => terminal.terminalId).join(', ') || 'en attente'; this.message.set(mode === 'BATCH' ? 'Dossier place dans le batch de provisioning.' : `Provisioning termine : MID ${result.result?.merchantAcceptorId ?? 'non genere'}, TID ${tids}.`); }
}
