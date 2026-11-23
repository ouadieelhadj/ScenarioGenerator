import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, filter, finalize, takeUntil } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { ProtectedActivationLinkMemory } from '../../core/auth/protected-activation-link';

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
        @if (completed()) {
          <div class="message success">Votre compte est actif. Vous pouvez maintenant vous connecter.</div>
          <a class="primary" routerLink="/onboarding/login">Se connecter</a>
        } @else if (!token && !error()) {
          <div class="message error">Le lien d'activation est absent ou incomplet.</div>
        } @else if (token) {
          <form (ngSubmit)="submit()">
            <label>Nouveau mot de passe
              <input type="password" name="password" [(ngModel)]="password" autocomplete="new-password" />
            </label>
            <label>Confirmation
              <input type="password" name="confirmation" [(ngModel)]="confirmation" autocomplete="new-password" />
            </label>
            <small>12 caracteres minimum avec majuscule, minuscule, chiffre et caractere special.</small>
            <button type="submit" [disabled]="loading()">{{ loading() ? 'Activation...' : 'Activer mon compte' }}</button>
          </form>
        }
        @if (error()) { <div class="message error" role="alert">{{ error() }}</div> }
      </section>
    </main>
  `,
  styles: [`
    .activation-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:linear-gradient(135deg,var(--sg-bg-page),var(--sg-bg-muted))}
    .activation-card{width:min(460px,100%);padding:32px;background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);box-shadow:var(--sg-shadow-lg)}
    .brand-icon{font-size:34px;color:var(--sg-color-primary)} h1{margin:16px 0 8px} p,small{color:var(--sg-text-secondary,#475467)}
    form,label{display:grid;gap:7px} form{gap:16px;margin-top:24px} input{padding:11px;border:1px solid var(--sg-border-strong);border-radius:var(--sg-radius);background:var(--sg-bg-surface);color:var(--sg-text-primary)}
    button,.primary{border:0;border-radius:var(--sg-radius);padding:12px 16px;background:var(--sg-color-primary);color:white;text-align:center;text-decoration:none;cursor:pointer}.primary{display:block;margin-top:20px}
    button:disabled{opacity:.6}.message{padding:12px;border-radius:var(--sg-radius);margin-top:16px}.error{background:#fff1f0;color:#b42318}.success{background:#ecfdf3;color:#067647}
  `],
})
export class MerchantActivationComponent implements OnDestroy {
  private readonly service = inject(MerchantOnboardingService);
  private readonly router = inject(Router);
  private readonly memory = new ProtectedActivationLinkMemory();
  private readonly cancellation = new Subject<void>();
  private readonly navigation: Subscription;
  private revision = 0;
  private readonly changedLink = () => this.readAddress();
  token = '';
  password = '';
  confirmation = '';
  readonly loading = signal(false);
  readonly completed = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.readAddress();window.addEventListener('hashchange',this.changedLink);
    this.navigation = this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      this.readAddress();
      // A hashchange can be captured before Router commits its URL. Remove the
      // secret from Router's own state as well, so later navigation cannot restore it.
      const address = this.router.parseUrl(this.router.url);
      const fragment = new URLSearchParams(address.fragment || '');
      if (Object.prototype.hasOwnProperty.call(address.queryParams, 'token') || fragment.has('token')) {
        delete address.queryParams['token'];fragment.delete('token');address.fragment=fragment.toString()||null;
        void this.router.navigateByUrl(address,{replaceUrl:true});
      }
    });
  }

  private readAddress(): void {
    // Reuse the qualified strict Web/APK parser. New links stay in fragments;
    // historical one-token query links remain accepted but are scrubbed too.
    const address = new URL(window.location.href);
    const fragment = new URLSearchParams(address.hash.substring(1));
    if(address.pathname!=='/activation'||(!address.searchParams.has('token')&&!fragment.has('token')))return;
    this.memory.clear();
    const incoming=this.memory.retain(address.pathname+address.search+address.hash)?this.memory.take():'';
    // The same pending link may be observed twice (hashchange then NavigationEnd).
    // A scrubbed address is not a replacement; it must not erase the pending intent.
    if(!incoming||incoming!==this.token){
      ++this.revision;this.cancellation.next();this.token=incoming;this.password='';this.confirmation='';
      this.loading.set(false);this.completed.set(false);this.error.set(null);
    }
    if (address.searchParams.has('token') || fragment.has('token')) {
      address.searchParams.delete('token');
      fragment.delete('token');
      address.hash = fragment.toString();
      window.history.replaceState(window.history.state, '', address.pathname + address.search + address.hash);
    }
  }
  ngOnDestroy():void {
    ++this.revision;this.navigation.unsubscribe();window.removeEventListener('hashchange',this.changedLink);this.cancellation.next();this.cancellation.complete();
    this.memory.clear();this.token='';this.password='';this.confirmation='';this.loading.set(false);
  }

  submit(): void {
    if (this.loading() || this.completed() || !this.token) return;
    this.error.set(null);
    if (this.password !== this.confirmation) { this.error.set('Les mots de passe ne correspondent pas.'); return; }
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/.test(this.password)) {
      this.error.set('Le mot de passe ne respecte pas la politique de securite.'); return;
    }
    this.loading.set(true);
    const token=this.token,password=this.password,revision=this.revision;this.password='';this.confirmation='';
    this.service.activate(token,password).pipe(takeUntil(this.cancellation),finalize(() => {if(revision===this.revision)this.loading.set(false);})).subscribe({
      next: () => {if(revision!==this.revision)return;this.token='';this.completed.set(true);},
      error: error => {if(revision!==this.revision)return;
        if([400,409,410].includes(error.status)){this.token='';this.error.set("Le lien est invalide, expire ou deja utilise.");}
        else this.error.set("L'activation est indisponible. Saisissez à nouveau votre mot de passe pour réessayer.");},
    });
  }
}
