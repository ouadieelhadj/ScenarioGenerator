import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { MerchantAddressV2 } from '../../core/models/merchant-onboarding.models';
import { MerchantAddressSelection } from '../../core/models/merchant-address-selection';
import { MerchantAddressAssistanceService } from '../../core/services/merchant-address-assistance.service';

@Component({ selector: 'app-merchant-address-assistance', standalone: true, imports: [FormsModule],
  template: `
    <div class="assistance" [attr.aria-label]="label">
      <div class="modes" role="group" [attr.aria-label]="'Mode d’adresse ' + label">
        <button type="button" [disabled]="disabled" [attr.aria-pressed]="selection.mode === 'MANUAL'" (click)="mode('MANUAL')">Saisie manuelle</button>
        <button type="button" [disabled]="disabled" [attr.aria-pressed]="selection.mode === 'GOOGLE'" (click)="mode('GOOGLE')">Saisie avec Google Maps</button>
      </div>
      @if (selection.mode === 'GOOGLE') {
        <p>Une suggestion n'est ni une preuve d'adresse ni une validation KYC. Seule votre recherche d'adresse est transmise au service ; confirmez ensuite les champs et les références internes.</p>
        @if (available()) {
          <label>Adresse à rechercher<input maxlength="200" [ngModel]="query" (ngModelChange)="changeQuery($event)" [ngModelOptions]="{standalone:true}" [disabled]="disabled" /></label>
          <button type="button" (click)="search()" [disabled]="disabled || selection.status === 'SEARCHING' || query.trim().length < 3">Rechercher une adresse</button>
        } @else { <p role="status">L'assistance Google n'est pas configurée ou est indisponible. La saisie manuelle reste utilisable ; vos champs ne sont pas effacés.</p> }
        @if (selection.status === 'SEARCHING') { <p role="status">Recherche en cours…</p> }
        @if (selection.status === 'NOT_FOUND') { <p role="status">Aucune adresse trouvée. Corrigez votre recherche ou poursuivez en saisie manuelle.</p> }
        @if (selection.status === 'UNAVAILABLE' || selection.status === 'INVALID') { <p role="alert">Le service d'adresse est indisponible. Poursuivez en saisie manuelle sans perdre vos champs.</p> }
        @if (selection.status === 'STALE') { <p role="alert">Votre adresse a changé depuis la recherche. Lancez une nouvelle recherche avant de confirmer.</p> }
        @if (selection.predictions.length) {
          <div class="predictions">
            @for (prediction of selection.predictions; track prediction.id) {
              <p>{{ prediction.label }} <button type="button" [disabled]="disabled" (click)="choose(prediction.id)">Choisir cette adresse</button></p>
            }
            <span class="gmp-attribution" translate="no">Google Maps</span>
          </div>
        }
        @for (suggestion of selection.suggestions; track suggestion.id) {
          <article><strong>{{ suggestion.label }}</strong><dl>
            <dt>Adresse</dt><dd>{{ suggestion.address.line1 }}</dd>
            <dt>Complément</dt><dd>{{ suggestion.address.line2 || 'Non renseigné' }}</dd>
            <dt>Quartier</dt><dd>{{ suggestion.address.district || 'Non renseigné' }}</dd>
            <dt>Ville</dt><dd>{{ suggestion.address.city }}</dd>
            <dt>Région</dt><dd>{{ suggestion.address.region || 'À confirmer' }}</dd>
            <dt>Code postal</dt><dd>{{ suggestion.address.postalCode || 'Non renseigné' }}</dd>
            <dt>Pays</dt><dd>{{ suggestion.address.country }}</dd>
          </dl><p [class.gmp-attribution]="suggestion.attribution === 'Google Maps'" translate="no">{{ suggestion.attribution }}</p>
            @for (credit of suggestion.credits || []; track credit.url) {
              <p><a [href]="credit.url" target="_blank" rel="noopener noreferrer">{{ credit.label }}</a></p>
            }
            <button type="button" [disabled]="disabled" (click)="confirm(suggestion.id)">Confirmer cette proposition d'adresse</button>
          </article>
        }
      }
      <span hidden>{{ revision() }}</span>
    </div>`,
  styles: [`.assistance{border:1px solid var(--sg-border);border-radius:8px;padding:12px}.modes{display:flex;gap:8px;flex-wrap:wrap}button{padding:8px;cursor:pointer}button[aria-pressed=true]{font-weight:700;border:2px solid var(--sg-color-primary)}label{display:grid;gap:6px}input{padding:9px;max-width:100%}article,.predictions{margin-top:12px;padding:12px;border:1px solid var(--sg-border)}.gmp-attribution{font-family:sans-serif;font-style:normal;font-weight:400;font-size:14px;letter-spacing:normal;white-space:nowrap;color:#1f1f1f;background:#fff}dl{display:grid;grid-template-columns:auto 1fr;gap:6px}dd{margin:0;overflow-wrap:anywhere}[role=alert]{color:#b42318}`]
})
export class MerchantAddressAssistanceComponent implements OnChanges, OnDestroy {
  private readonly service = inject(MerchantAddressAssistanceService);
  @Input({ required: true }) caseId = '';
  @Input({ required: true }) address!: MerchantAddressV2;
  @Input() label = 'Adresse';
  @Input() disabled = false;
  @Output() confirmed = new EventEmitter<MerchantAddressV2>();
  readonly selection = new MerchantAddressSelection();
  readonly available = signal(false);
  readonly revision = signal(0);
  query = '';
  private capability?: Subscription;
  private request?: Subscription;
  private boundCase = '';
  private sessionId = '';

  ngOnChanges(): void {
    if (this.caseId !== this.boundCase) {
      this.capability?.unsubscribe(); this.clear(); this.available.set(false); this.boundCase = this.caseId;
      if (this.caseId) this.capability = this.service.capability(this.caseId).subscribe({
        next: value => this.available.set(value.available === true && value.provider === 'GOOGLE_MAPS'),
        error: () => this.available.set(false)
      });
    }
    if (this.disabled) this.clear();
  }
  mode(mode: 'MANUAL' | 'GOOGLE'): void {
    if (this.disabled) return;
    this.request?.unsubscribe(); this.sessionId = ''; this.query = ''; this.selection.setMode(mode); this.touch();
  }
  changeQuery(value: string): void {
    this.request?.unsubscribe(); this.selection.cancel(); this.query = value; this.touch();
  }
  search(): void {
    if (this.disabled || !this.available() || this.selection.mode !== 'GOOGLE' || this.selection.status === 'SEARCHING') return;
    const query = this.query.trim();
    if (query.length < 3 || query.length > 200 || /[\x00-\x1f\x7f]/.test(query) || !/^[A-Z]{2}$/.test(this.address.country)) return;
    const generation = this.selection.begin(this.address); this.touch();
    this.sessionId ||= crypto.randomUUID();
    this.request = this.service.search(this.caseId, query, this.address.country, this.sessionId).subscribe({
      next: value => {
        if (value.predictions?.length || (value.predictions && !value.suggestions.length)) this.selection.receivePredictions(generation, value.predictions);
        else this.selection.receive(generation, value.suggestions);
        this.touch();
      },
      error: () => { this.selection.unavailable(generation); this.touch(); }
    });
  }
  choose(id: string): void {
    if (this.disabled || !this.sessionId) return;
    const generation = this.selection.choose(id, this.address); this.touch();
    if (generation === null) return;
    const session = this.sessionId; this.sessionId = '';
    this.request = this.service.resolve(this.caseId, id, this.address.country, session).subscribe({
      next: value => { this.selection.receive(generation, [value]); this.touch(); },
      error: () => { this.selection.unavailable(generation); this.touch(); }
    });
  }
  confirm(id: string): void {
    if (this.disabled) return;
    const address = this.selection.confirm(id, this.address);
    if (address) { this.query = ''; this.sessionId = ''; this.confirmed.emit(address); }
    this.touch();
  }
  private touch(): void { this.revision.update(value => value + 1); }
  private clear(): void { this.request?.unsubscribe(); this.query = ''; this.sessionId = ''; this.selection.setMode('MANUAL'); this.touch(); }
  ngOnDestroy(): void { this.capability?.unsubscribe(); this.clear(); }
}
