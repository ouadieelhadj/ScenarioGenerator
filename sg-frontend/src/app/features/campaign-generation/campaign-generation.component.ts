import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../core/services/campaign.service';
import { Campaign, CampaignRequest, LoadStep, NetworkRef, MessageTypeRef } from '../../core/models/campaign.models';
import { NetworkService } from '../../core/services/network.service';
import { MessageTypeService } from '../../core/services/message-type.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-campaign-generation',
  standalone: true,
  imports: [FormsModule, TranslatePipe, HasPermissionDirective],
  templateUrl: './campaign-generation.component.html',
  styleUrl: './campaign-generation.component.scss',
})
export class CampaignGenerationComponent implements OnInit {
  private service = inject(CampaignService);
  private networkService = inject(NetworkService);
  private messageTypeService = inject(MessageTypeService);
  private i18n = inject(TranslateService);

  readonly campaigns = signal<Campaign[]>([]);
  readonly networks = signal<NetworkRef[]>([]);
  readonly categories = signal<MessageTypeRef[]>([]);
  readonly initiators = ['ACQUIRER', 'ISSUER'];
  readonly loading = signal(false);
  // On stocke une CLE de traduction (ou null), traduite dans le template
  readonly error = signal<string | null>(null);

  readonly showForm = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);

  form: CampaignRequest = this.emptyForm();

  ngOnInit(): void {
    this.load();
    this.loadNetworks();
  }

  loadNetworks(): void {
    this.networkService.findAll().subscribe({
      next: (data) => this.networks.set(data),
      error: (err) => console.error('[CAMPAIGN] loadNetworks error', err),
    });
  }

  /** Charge les categories/types du reseau selectionne (pour le selecteur categorie). */
  onNetworkChange(): void {
    const net = this.form.network;
    if (!net) { this.categories.set([]); return; }
    this.messageTypeService.findByNetwork(net).subscribe({
      next: (types) => {
        // categories distinctes du reseau
        const seen = new Set<string>();
        const cats = types.filter(t => { if (seen.has(t.category)) return false; seen.add(t.category); return true; });
        this.categories.set(cats);
        // si la categorie courante n'existe pas dans ce reseau, prendre la premiere
        if (!cats.some(c => c.category === this.form.category) && cats.length) {
          this.form.category = cats[0].category;
        }
      },
      error: (err) => console.error('[CAMPAIGN] onNetworkChange error', err),
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.findAll().subscribe({
      next: (data) => { this.campaigns.set(data); this.loading.set(false); },
      error: (err) => {
        console.error('[CAMPAIGN] load error', err);
        this.error.set('campaign.loadError');
        this.loading.set(false);
      },
    });
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form = this.emptyForm();
    this.onNetworkChange();
    this.showForm.set(true);
  }

  openEdit(c: Campaign): void {
    this.editingId.set(c.id);
    this.form = {
      name: c.name,
      description: c.description ?? '',
      network: c.network ?? 'DMAS',
      initiator: c.initiator ?? 'ACQUIRER',
      category: c.category,
      config: c.config,
      expectedDe039: c.expectedDe039 ?? '00',
      active: c.active,
      slaP95MaxMs: c.slaP95MaxMs,
      slaErrorRateMax: c.slaErrorRateMax,
      slaApprovalMin: c.slaApprovalMin,
      stopOnErrorRate: c.stopOnErrorRate,
      loadSteps: c.loadSteps?.length ? c.loadSteps.map(s => ({ ...s })) : [this.emptyStep(1)],
    };
    this.onNetworkChange();
    this.showForm.set(true);
  }

  closeForm(): void { this.showForm.set(false); }

  addStep(): void {
    const order = this.form.loadSteps.length + 1;
    this.form.loadSteps.push(this.emptyStep(order));
  }

  removeStep(i: number): void {
    this.form.loadSteps.splice(i, 1);
    this.form.loadSteps.forEach((s: LoadStep, idx: number) => s.stepOrder = idx + 1);
  }

  save(): void {
    if (!this.form.name || !this.form.category) {
      this.error.set('campaign.saveError');
      return;
    }
    this.saving.set(true);
    const id = this.editingId();
    const op = id ? this.service.update(id, this.form) : this.service.create(this.form);
    op.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.load();
      },
      error: (err) => {
        console.error('[CAMPAIGN] save error', err);
        this.error.set('campaign.saveError');
        this.saving.set(false);
      },
    });
  }

  remove(c: Campaign): void {
    const msg = this.i18n.instant('common.confirmDelete');
    if (!confirm(msg)) return;
    this.service.delete(c.id).subscribe({
      next: () => this.load(),
      error: (err) => {
        console.error('[CAMPAIGN] delete error', err);
        this.error.set('campaign.deleteError');
      },
    });
  }

  private emptyForm(): CampaignRequest {
    return {
      name: '',
      description: '',
      network: 'DMAS',
      initiator: 'ACQUIRER',
      category: 'AUTHORIZATION',
      config: '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":false,"VARIABLE_FIELDS":{"AMOUNT":{"mode":"RANGE","min":1000,"max":50000}}}',
      expectedDe039: '00',
      active: true,
      slaP95MaxMs: 500,
      slaErrorRateMax: 10,
      slaApprovalMin: 90,
      stopOnErrorRate: 20,
      loadSteps: [this.emptyStep(1)],
    };
  }

  private emptyStep(order: number): LoadStep {
    return { stepOrder: order, startSeconds: 0, endSeconds: 8, tpsValue: 5 };
  }
}

