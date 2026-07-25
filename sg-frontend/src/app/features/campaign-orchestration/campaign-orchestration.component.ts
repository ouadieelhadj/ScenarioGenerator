import { Component, inject, signal, computed, OnInit, OnDestroy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Subscription, interval, switchMap } from 'rxjs';
import { CampaignService } from '../../core/services/campaign.service';
import { Campaign, CampaignExecution } from '../../core/models/campaign.models';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

const POLL_MS = 2000;
const TERMINAL = ['COMPLETED', 'ERROR', 'STOPPED_ERROR_RATE', 'FAILED'];

@Component({
  selector: 'app-campaign-orchestration',
  standalone: true,
  imports: [TranslatePipe, HasPermissionDirective],
  templateUrl: './campaign-orchestration.component.html',
  styleUrl: './campaign-orchestration.component.scss',
})
export class CampaignOrchestrationComponent implements OnInit, OnDestroy {
  private service = inject(CampaignService);

  readonly campaigns = signal<Campaign[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly launchingId = signal<number | null>(null);
  readonly execution = signal<CampaignExecution | null>(null);
  readonly executionId = signal<number | null>(null);
  readonly following = signal(false);

  private pollSub?: Subscription;

  // Seules les campagnes actives
  readonly activeCampaigns = computed(() => this.campaigns().filter(c => c.active));

  readonly isTerminal = computed(() => {
    const ex = this.execution();
    return ex ? TERMINAL.includes(ex.status) : false;
  });

  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { this.stopPolling(); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.findAll().subscribe({
      next: (data) => { this.campaigns.set(data); this.loading.set(false); },
      error: () => { this.error.set('campaign.loadError'); this.loading.set(false); },
    });
  }

  launch(c: Campaign): void {
    this.launchingId.set(c.id);
    this.error.set(null);
    this.execution.set(null);
    this.service.run(c.id).subscribe({
      next: (res) => {
        this.launchingId.set(null);
        this.executionId.set(res.campaignExecutionId);
        this.startPolling(res.campaignExecutionId);
      },
      error: (err) => {
        console.error('[ORCH] launch error', err);
        this.launchingId.set(null);
        this.error.set('orchestration.launchError');
      },
    });
  }

  startPolling(execId: number): void {
    this.stopPolling();
    this.following.set(true);
    // Premiere lecture immediate puis toutes les 2s
    this.fetchOnce(execId);
    this.pollSub = interval(POLL_MS).pipe(
      switchMap(() => this.service.findExecution(execId))
    ).subscribe({
      next: (ex) => {
        this.execution.set(ex);
        if (TERMINAL.includes(ex.status)) this.stopPolling();
      },
      error: () => { this.error.set('orchestration.followError'); this.stopPolling(); },
    });
  }

  private fetchOnce(execId: number): void {
    this.service.findExecution(execId).subscribe({
      next: (ex) => {
        this.execution.set(ex);
        if (TERMINAL.includes(ex.status)) this.stopPolling();
      },
      error: () => { /* le polling gerera */ },
    });
  }

  refresh(): void {
    const id = this.executionId();
    if (id) this.fetchOnce(id);
  }

  stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
    this.following.set(false);
  }

  statusClass(status: string): string {
    if (status === 'COMPLETED') return 'st-ok';
    if (TERMINAL.includes(status)) return 'st-err';
    return 'st-run';
  }

  verdictClass(verdict: string): string {
    return verdict === 'PASSED' ? 'v-pass' : verdict === 'FAILED' ? 'v-fail' : '';
  }
}

