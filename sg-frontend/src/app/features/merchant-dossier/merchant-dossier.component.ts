import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  MerchantDocument, MerchantDocumentType, MerchantDossier, MerchantDossierUpdate,
} from '../../core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({
  selector: 'app-merchant-dossier',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    @if (loading()) { <div class="state"><i class="pi pi-spin pi-spinner"></i> Chargement du dossier...</div> }
    @else if (error()) { <div class="state error">{{ error() }}</div> }
    @else { @if (dossier(); as item) {
      <header><div><span class="eyebrow">{{ item.reference }}</span><h1>Auto-onboarding commercant</h1></div><div class="badges"><span>{{ item.status }}</span><span>KYC {{ item.kycStatus }}</span></div></header>
      @if (item.complementReason) { <div class="notice warning"><strong>Complements demandes</strong><p>{{ item.complementReason }}</p></div> }
      @if (message()) { <div class="notice success">{{ message() }}</div> }
      <form class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <label>Raison sociale<input formControlName="legalName" /></label>
        <label>Nom commercial<input formControlName="tradingName" /></label>
        <label>Immatriculation<input formControlName="registrationNumber" /></label>
        <label>Pays ISO-2<input formControlName="country" maxlength="2" /></label>
        <label>MCC<input formControlName="mcc" maxlength="4" inputmode="numeric" /></label>
        <label>Compte de reglement<input formControlName="settlementAccountReference" /></label>
        <label>Devise ISO-4217<input formControlName="settlementCurrency" maxlength="3" inputmode="numeric" /></label>
        <label>Produit Acquiring (UUID)<input formControlName="productId" /></label>
        <label>Canal<select formControlName="acceptanceChannel"><option value="TPE">TPE</option><option value="ECOMMERCE">E-commerce</option><option value="BOTH">TPE + E-commerce</option></select></label>
        <label>Code point de vente<input formControlName="outletCode" /></label>
        <label>Nom point de vente<input formControlName="outletName" /></label>
        <label class="wide">Adresse point de vente<input formControlName="outletAddress" /></label>
        <label>Nombre de TPE<input type="number" min="0" max="999" formControlName="terminalCount" /></label>
        <div class="actions wide"><button class="primary" type="submit" [disabled]="form.invalid || busy() || item.status !== 'DRAFT'">Enregistrer le dossier</button></div>
      </form>

      <section class="documents">
        <h2>Pieces KYC obligatoires</h2>
        @for (type of requiredTypes; track type) {
          <article><div><strong>{{ documentLabel(type) }}</strong><p>{{ latest(type)?.reviewStatus || 'NON DEPOSE' }}</p></div>
            <input type="file" accept="application/pdf,image/jpeg,image/png" (change)="choose(type, $event)" [disabled]="item.status !== 'DRAFT'" />
            <button type="button" (click)="upload(type)" [disabled]="!selected[type] || busy() || item.status !== 'DRAFT'">Televerser</button>
          </article>
        }
        <button type="button" class="primary" (click)="submitKyc()" [disabled]="!canSubmitKyc(item) || busy()">Soumettre les pieces au Back-office</button>
      </section>

      <section class="decision">
        <h2>Soumission Maker</h2>
        <p>La soumission devient disponible apres validation KYC. Un Checker distinct devra ensuite approuver.</p>
        <button type="button" class="primary" (click)="submitMaker()" [disabled]="item.status !== 'DRAFT' || item.kycStatus !== 'VALIDATED' || busy()">Soumettre le dossier</button>
      </section>

      @if (item.merchantAcceptorId) { <div class="notice success"><strong>Affiliation terminee</strong><p>MID : {{ item.merchantAcceptorId }}</p></div> }
    } }
  `,
  styles: [`
    header{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap;margin-bottom:20px}.eyebrow{font-size:12px;font-weight:700;color:var(--sg-color-primary)}h1{margin:6px 0}.badges{display:flex;gap:8px}.badges span{padding:6px 10px;border-radius:999px;background:var(--sg-bg-muted);font-size:12px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;padding:20px;border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);background:var(--sg-bg-surface)}label{display:grid;gap:6px;font-weight:600;font-size:13px}input,select{padding:10px;border:1px solid var(--sg-border);border-radius:8px;background:var(--sg-bg-surface);color:var(--sg-text-primary)}.wide{grid-column:1/-1}.actions{display:flex;justify-content:flex-end}.primary,button{padding:10px 14px;border:0;border-radius:8px;cursor:pointer}.primary{background:var(--sg-color-primary);color:#fff}.documents,.decision{margin-top:18px;padding:20px;border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);background:var(--sg-bg-surface)}.documents article{display:grid;grid-template-columns:1fr minmax(220px,1fr) auto;align-items:center;gap:12px;padding:12px 0;border-bottom:1px solid var(--sg-border)}.documents p,.decision p,.notice p{margin:4px 0;color:var(--sg-text-muted)}.notice,.state{margin:16px 0;padding:16px;border:1px solid var(--sg-border);border-radius:10px}.success{border-color:#16803c;color:#116b33}.warning{border-color:#d97706}.error{color:#b42318}@media(max-width:760px){.form-grid{grid-template-columns:1fr}.wide{grid-column:auto}.documents article{grid-template-columns:1fr}}
  `],
})
export class MerchantDossierComponent implements OnInit {
  private readonly service = inject(MerchantOnboardingService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly dossier = signal<MerchantDossier | null>(null);
  readonly documents = signal<MerchantDocument[]>([]);
  readonly requiredTypes: MerchantDocumentType[] = ['LEGAL_EXISTENCE', 'REPRESENTATIVE_IDENTITY', 'BANK_ACCOUNT_PROOF'];
  readonly selected: Partial<Record<MerchantDocumentType, File>> = {};
  readonly form = this.fb.nonNullable.group({
    legalName: ['', Validators.required], tradingName: ['', Validators.required],
    registrationNumber: ['', Validators.required], country: ['MA', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
    mcc: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]], settlementAccountReference: ['', Validators.required],
    settlementCurrency: ['504', [Validators.required, Validators.pattern(/^\d{3}$/)]], productId: ['', Validators.required],
    acceptanceChannel: this.fb.nonNullable.control<'TPE'|'ECOMMERCE'|'BOTH'>('TPE', Validators.required), outletCode: ['', Validators.required],
    outletName: ['', Validators.required], outletAddress: ['', Validators.required], terminalCount: [1, [Validators.min(0), Validators.max(999)]],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    const source = id ? this.service.dossier(id) : this.service.myDossier();
    source.pipe(finalize(() => this.loading.set(false))).subscribe({ next: dossier => this.open(dossier), error: () => this.error.set('Dossier introuvable ou acces refuse.') });
  }
  private open(dossier: MerchantDossier): void {
    this.dossier.set(dossier);
    this.form.patchValue({
      legalName: dossier.legalName ?? '', tradingName: dossier.tradingName ?? '', registrationNumber: dossier.registrationNumber ?? '',
      country: dossier.country ?? 'MA', mcc: dossier.mcc ?? '', settlementAccountReference: dossier.settlementAccountReference ?? '',
      settlementCurrency: dossier.settlementCurrency ?? '504', productId: dossier.productId ?? '',
      acceptanceChannel: (dossier.acceptanceChannel as 'TPE'|'ECOMMERCE'|'BOTH') ?? 'TPE', outletCode: dossier.outletCode ?? '',
      outletName: dossier.outletName ?? '', outletAddress: dossier.outletAddress ?? '', terminalCount: dossier.terminalCount || 1,
    });
    this.reloadDocuments();
  }
  save(): void { if (this.form.invalid || !this.dossier()) return; this.run(this.service.updateDossier(this.dossier()!.id, this.form.getRawValue() as MerchantDossierUpdate), 'Dossier enregistre.'); }
  choose(type: MerchantDocumentType, event: Event): void { const file = (event.target as HTMLInputElement).files?.[0]; if (file) this.selected[type] = file; }
  upload(type: MerchantDocumentType): void { const file = this.selected[type]; if (!file || !this.dossier()) return; this.busy.set(true); this.service.uploadDocument(this.dossier()!.id, type, file).subscribe({ next: () => { delete this.selected[type]; this.busy.set(false); this.message.set('Piece televersee et empreinte SHA-256 calculee par le serveur.'); this.reloadDocuments(); }, error: e => this.fail(e) }); }
  submitKyc(): void { if (this.dossier()) this.run(this.service.submitKyc(this.dossier()!.id), 'KYC soumis au Back-office.'); }
  submitMaker(): void { if (!this.dossier()) return; this.busy.set(true); this.service.submit(this.dossier()!.id).subscribe({ next: () => { this.busy.set(false); this.message.set('Dossier soumis par le Maker au Checker.'); this.refresh(); }, error: e => this.fail(e) }); }
  latest(type: MerchantDocumentType): MerchantDocument | undefined { return this.documents().find(document => document.type === type); }
  canSubmitKyc(dossier: MerchantDossier): boolean { return dossier.status === 'DRAFT' && ['NOT_STARTED','COMPLEMENTS_REQUIRED'].includes(dossier.kycStatus) && this.requiredTypes.every(type => !!this.latest(type)); }
  documentLabel(type: MerchantDocumentType): string { return ({ LEGAL_EXISTENCE:'Existence legale', REPRESENTATIVE_IDENTITY:'Identite du representant', BANK_ACCOUNT_PROOF:'Justificatif bancaire' } as Record<string,string>)[type] ?? type; }
  private reloadDocuments(): void { if (this.dossier()) this.service.documents(this.dossier()!.id).subscribe({ next: value => this.documents.set(value), error: () => this.documents.set([]) }); }
  private refresh(): void { if (this.dossier()) this.service.dossier(this.dossier()!.id).subscribe(value => this.open(value)); }
  private run(source: ReturnType<MerchantOnboardingService['updateDossier']> | ReturnType<MerchantOnboardingService['submitKyc']>, message: string): void { this.busy.set(true); this.error.set(null); source.subscribe({ next: value => { this.busy.set(false); this.message.set(message); this.open(value); }, error: e => this.fail(e) }); }
  private fail(error: any): void { this.busy.set(false); this.error.set(error?.error?.message ?? 'Operation refusee par le serveur.'); }
}
