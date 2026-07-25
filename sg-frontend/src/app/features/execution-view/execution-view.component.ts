import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { CampaignService } from '../../core/services/campaign.service';
import { Campaign, CampaignExecution } from '../../core/models/campaign.models';

const TERMINAL = ['COMPLETED', 'ERROR', 'STOPPED_ERROR_RATE', 'FAILED'];

@Component({
  selector: 'app-execution-view',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './execution-view.component.html',
  styleUrl: './execution-view.component.scss',
})
export class ExecutionViewComponent implements OnInit {
  private service = inject(CampaignService);

  readonly campaigns = signal<Campaign[]>([]);
  readonly executions = signal<CampaignExecution[]>([]);
  readonly selectedCampaignId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Detail d'une execution (panneau lateral)
  readonly detail = signal<CampaignExecution | null>(null);

  ngOnInit(): void {
    this.service.findAll().subscribe({
      next: (data) => this.campaigns.set(data),
      error: () => this.error.set('campaign.loadError'),
    });
  }

  onSelectCampaign(id: string): void {
    const campaignId = id ? Number(id) : null;
    this.selectedCampaignId.set(campaignId);
    this.detail.set(null);
    this.executions.set([]);
    if (campaignId === null) return;

    this.loading.set(true);
    this.error.set(null);
    this.service.findExecutionsByCampaign(campaignId).subscribe({
      next: (data) => { this.executions.set(data ?? []); this.loading.set(false); },
      error: () => { this.error.set('executionView.loadError'); this.loading.set(false); },
    });
  }

  openDetail(ex: CampaignExecution): void {
    // Si on a l'id, on recharge le detail frais ; sinon on affiche ce qu'on a
    const id = ex.campaignExecutionId;
    if (id) {
      this.service.findExecution(id).subscribe({
        next: (full) => this.detail.set(full),
        error: () => this.detail.set(ex),
      });
    } else {
      this.detail.set(ex);
    }
  }

  closeDetail(): void { this.detail.set(null); }

  statusClass(status: string): string {
    if (status === 'COMPLETED') return 'st-ok';
    if (TERMINAL.includes(status)) return 'st-err';
    return 'st-run';
  }

  verdictClass(verdict: string): string {
    return verdict === 'PASSED' ? 'v-pass' : verdict === 'FAILED' ? 'v-fail' : '';
  }
}

