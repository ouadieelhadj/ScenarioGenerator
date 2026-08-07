import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({
  selector: 'app-merchant-activation',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="activation-page">
      <section class="activation-card">
        <i class="pi pi-shop brand-icon"></i>
        <h1>Activer mon compte commercant</h1>
        <p>Definissez votre mot de passe pour acceder a votre dossier d'affiliation.</p>
        @if (!token) {
          <div class="message error">Le lien d'activation est absent ou incomplet.</div>
        } @else if (completed()) {
          <div class="message success">Votre compte est actif. Vous pouvez maintenant vous connecter.</div>
          <a class="primary" routerLink="/login">Se connecter</a>
        } @else {
          <form (ngSubmit)="submit()">
            <label>Nouveau mot de passe
              <input type="password" name="password" [(ngModel)]="password" autocomplete="new-password" />
            </label>
            <label>Confirmation
              <input type="password" name="confirmation" [(ngModel)]="confirmation" autocomplete="new-password" />
            </label>
            <small>12 caracteres minimum avec majuscule, minuscule, chiffre et caractere special.</small>
            @if (error()) { <div class="message error">{{ error() }}</div> }
            <button type="submit" [disabled]="loading()">{{ loading() ? 'Activation...' : 'Activer mon compte' }}</button>
          </form>
        }
      </section>
    </main>
  `,
  styles: [`
    .activation-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:linear-gradient(135deg,var(--sg-bg-page),var(--sg-bg-muted))}
    .activation-card{width:min(460px,100%);padding:32px;background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);box-shadow:var(--sg-shadow-lg)}
    .brand-icon{font-size:34px;color:var(--sg-color-primary)} h1{margin:16px 0 8px} p,small{color:var(--sg-text-muted)}
    form,label{display:grid;gap:7px} form{gap:16px;margin-top:24px} input{padding:11px;border:1px solid var(--sg-border-strong);border-radius:var(--sg-radius);background:var(--sg-bg-surface);color:var(--sg-text-primary)}
    button,.primary{border:0;border-radius:var(--sg-radius);padding:12px 16px;background:var(--sg-color-primary);color:white;text-align:center;text-decoration:none;cursor:pointer}.primary{display:block;margin-top:20px}
    button:disabled{opacity:.6}.message{padding:12px;border-radius:var(--sg-radius);margin-top:16px}.error{background:#fff1f0;color:#b42318}.success{background:#ecfdf3;color:#067647}
  `],
})
export class MerchantActivationComponent {
  private readonly service = inject(MerchantOnboardingService);
  private readonly route = inject(ActivatedRoute);
  readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  password = '';
  confirmation = '';
  readonly loading = signal(false);
  readonly completed = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    this.error.set(null);
    if (this.password !== this.confirmation) { this.error.set('Les mots de passe ne correspondent pas.'); return; }
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/.test(this.password)) {
      this.error.set('Le mot de passe ne respecte pas la politique de securite.'); return;
    }
    this.loading.set(true);
    this.service.activate(this.token, this.password).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => this.completed.set(true),
      error: error => this.error.set(error.status === 400 ? "Le lien est invalide, expire ou deja utilise." : "L'activation est indisponible."),
    });
  }
}
