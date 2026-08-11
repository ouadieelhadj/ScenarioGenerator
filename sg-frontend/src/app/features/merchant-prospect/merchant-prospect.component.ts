import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MerchantProspect } from '../../core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { merchantActivationUrl } from '../../core/config/merchant-activation.config';

@Component({
  selector: 'app-merchant-prospect',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <header><span class="eyebrow">ESPACE COMMERCIAL</span><h1>Creer un prospect commercant</h1><p>Le compte reste inactif jusqu'a l'utilisation du lien d'activation.</p></header>
    <section class="panel">
      <form (ngSubmit)="submit()">
        <label>Identifiant du commercant<input name="login" [(ngModel)]="login" required autocomplete="off" /></label>
        <label>Adresse email<input name="email" type="email" [(ngModel)]="email" required autocomplete="off" /></label>
        <label>Code acquereur<input name="acquirerId" [(ngModel)]="acquirerId" required autocomplete="off" /></label>
        @if (error()) { <div class="message error">{{ error() }}</div> }
        <button type="submit" [disabled]="loading() || !login || !email || !acquirerId">{{ loading() ? 'Creation...' : 'Creer et inviter' }}</button>
      </form>
    </section>
    @if (created(); as prospect) {
      <section class="result" aria-live="polite">
        <i class="pi pi-check-circle"></i><div><h2>Prospect cree</h2><p>Dossier <strong>{{ prospect.dossier.reference }}</strong> - compte {{ prospect.account.status }}</p>
        @if (activationUrl(prospect); as link) {
          <label>Lien d'activation a transmettre par un canal securise<input [value]="link" readonly /></label>
        } @else { <p class="warning">L'identite n'a pas retourne d'invitation. Verifiez son raccordement avant transmission.</p> }
        <a [routerLink]="['/merchant/dossier', prospect.dossier.id]">Ouvrir le squelette du dossier</a></div>
      </section>
    }
  `,
  styles: [`
    header{margin-bottom:20px}.eyebrow{font-size:12px;font-weight:700;letter-spacing:.12em;color:var(--sg-color-primary)}h1{margin:8px 0}p{color:var(--sg-text-muted)}.panel,.result{max-width:720px;padding:22px;background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg)}form,label{display:grid;gap:7px}form{gap:16px}input{padding:10px;border:1px solid var(--sg-border-strong);border-radius:var(--sg-radius);background:var(--sg-bg-surface);color:var(--sg-text-primary)}button{justify-self:start;padding:11px 18px;border:0;border-radius:var(--sg-radius);background:var(--sg-color-primary);color:white;cursor:pointer}button:disabled{opacity:.6}.result{display:flex;gap:14px;margin-top:18px}.result>i{font-size:28px;color:#16803c}.result h2{margin:0}.result label{margin:14px 0}.result a{color:var(--sg-color-primary);font-weight:600}.message,.warning{padding:10px;border-radius:var(--sg-radius)}.error{background:#fff1f0;color:#b42318}.warning{background:#fff8e6;color:#8a4b00}
  `],
})
export class MerchantProspectComponent {
  private readonly service = inject(MerchantOnboardingService);
  login = '';
  email = '';
  acquirerId = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly created = signal<MerchantProspect | null>(null);

  submit(): void {
    this.loading.set(true); this.error.set(null); this.created.set(null);
    this.service.createProspect({ login: this.login.trim(), email: this.email.trim(), acquirerId: this.acquirerId.trim() })
      .pipe(finalize(() => this.loading.set(false))).subscribe({
        next: value => this.created.set(value),
        error: error => this.error.set(error.status === 409 ? 'Ce login ou cet email existe deja.' : 'La creation du prospect a echoue.'),
      });
  }

  activationUrl(prospect: MerchantProspect): string | null {
    const token = prospect.identityInvitation?.activationToken;
    return token ? merchantActivationUrl(token) : null;
  }
}
