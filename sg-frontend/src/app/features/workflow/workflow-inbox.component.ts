import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { WorkflowService } from '../../core/services/workflow.service';
import { WorkflowRequestSummary } from '../../core/models/workflow.models';

@Component({
  selector: 'app-workflow-inbox',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <section>
      <h1><i class="pi pi-check-square"></i> {{ titleKey | translate }}</h1>
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
            <article><strong>{{ request.operationType }}</strong><span>{{ request.moduleCode }}</span><span>{{ request.status }}</span></article>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    h1 { color:var(--sg-text-primary); }
    .state, .notice, .card { padding:1rem; border:1px solid var(--sg-border); border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .state { color:var(--sg-text-secondary); text-align:center; }
    .notice { display:flex; gap:.75rem; } .notice i { color:var(--sg-color-primary); }
    .notice p { color:var(--sg-text-secondary); margin:.35rem 0 0; }
    article { display:grid; grid-template-columns:2fr 1fr 1fr; gap:1rem; padding:.75rem; border-bottom:1px solid var(--sg-border); }
  `],
})
export class WorkflowInboxComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private service = inject(WorkflowService);
  readonly requests = signal<WorkflowRequestSummary[]>([]);
  readonly loading = signal(true);
  readonly unavailable = signal(false);
  readonly mode = this.route.snapshot.data['mode'] as 'operations' | 'approvals';
  readonly titleKey = this.mode === 'approvals' ? 'workflow.myApprovals' : 'workflow.myOperations';

  ngOnInit(): void {
    const source = this.mode === 'approvals' ? this.service.myApprovals() : this.service.myOperations();
    source.subscribe({
      next: requests => { this.requests.set(requests); this.loading.set(false); },
      error: () => { this.unavailable.set(true); this.loading.set(false); },
    });
  }
}
