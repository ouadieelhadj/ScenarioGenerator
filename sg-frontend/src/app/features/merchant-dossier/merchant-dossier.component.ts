import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';
import { MerchantDossier } from '../../core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({
  selector: 'app-merchant-dossier',
  standalone: true,
  template: `
    @if (loading()) { <div class="state"><i class="pi pi-spin pi-spinner"></i> Chargement du dossier...</div> }
    @else if (error()) { <div class="state error">{{ error() }}</div> }
    @else {
      @if (dossier(); as item) {
      <header><span class="eyebrow">{{ item.reference }}</span><h1>{{ item.legalName || 'Dossier commercant a completer' }}</h1><div class="badges"><span>{{ item.status }}</span><span>KYC {{ item.kycStatus }}</span></div></header>
      @if (item.complementReason) { <div class="notice"><strong>Complements demandes</strong><p>{{ item.complementReason }}</p></div> }
      <section class="steps">
        @for (step of steps; track step.code) { <article [class.ready]="step.ready(item)"><i [class]="step.icon"></i><div><strong>{{ step.label }}</strong><p>{{ step.detail }}</p></div></article> }
      </section>
      <div class="notice info">Ce premier increment affiche uniquement les donnees retournees par l'API reelle. L'edition detaillee sera ouverte apres extension du contrat de lecture du dossier.</div>
      }
    }
  `,
  styles: [`
    header{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:22px}.eyebrow{font-size:12px;font-weight:700;color:var(--sg-color-primary)}h1{margin:6px 0}.badges{display:flex;gap:8px}.badges span{padding:6px 10px;border-radius:999px;background:var(--sg-bg-muted);font-size:12px}.steps{display:grid;gap:12px}.steps article{display:flex;gap:14px;padding:18px;border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);background:var(--sg-bg-surface)}.steps article.ready{border-left:4px solid #16803c}.steps i{font-size:22px;color:var(--sg-color-primary)}.steps p,.notice p{margin:5px 0 0;color:var(--sg-text-muted)}.notice,.state{margin:16px 0;padding:16px;border:1px solid var(--sg-border);border-radius:var(--sg-radius);background:var(--sg-bg-surface)}.info{color:var(--sg-text-muted)}.error{color:#b42318}
  `],
})
export class MerchantDossierComponent implements OnInit {
  private readonly service = inject(MerchantOnboardingService);
  private readonly route = inject(ActivatedRoute);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly dossier = signal<MerchantDossier | null>(null);
  readonly steps = [
    { code: 'identity', label: 'Identite et entreprise', detail: 'Informations legales et activite.', icon: 'pi pi-building', ready: (d: MerchantDossier) => !!d.legalName && !!d.registrationNumber },
    { code: 'outlet', label: 'Point de vente', detail: 'Canal d’acceptation et terminaux.', icon: 'pi pi-shop', ready: (d: MerchantDossier) => !!d.acceptanceChannel },
    { code: 'documents', label: 'Pieces et KYC', detail: 'Depot, controle et complements.', icon: 'pi pi-folder-open', ready: (d: MerchantDossier) => d.kycStatus !== 'NOT_STARTED' },
    { code: 'approval', label: 'Maker / Checker', detail: 'Soumission et decision separee.', icon: 'pi pi-check-square', ready: (d: MerchantDossier) => ['APPROVED','QUEUED_FOR_PROVISIONING','PROVISIONING','PROVISIONED'].includes(d.status) },
    { code: 'acquiring', label: 'Activation Acquiring', detail: 'MID et TID retournes par le socle autoritatif.', icon: 'pi pi-credit-card', ready: (d: MerchantDossier) => d.status === 'PROVISIONED' },
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.error.set('Identifiant de dossier absent.'); this.loading.set(false); return; }
    this.service.dossier(id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: value => this.dossier.set(value),
      error: error => this.error.set(error.status === 403 ? "Vous n'etes pas autorise a consulter ce dossier." : 'Le dossier est indisponible.'),
    });
  }
}
