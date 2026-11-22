import { mobileDossierSummary } from '../../app/core/services/personal-dossier-source';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonContent, IonHeader, IonTitle, IonToolbar } from '@ionic/angular/standalone';
import { Subject, takeUntil } from 'rxjs';
import { MobileAuthService } from '../services/mobile-auth.service';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';
import { MerchantCommercialService } from '../../app/core/services/merchant-commercial.service';
import { MerchantDossier } from '../../app/core/models/merchant-onboarding.models';
import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { commercialAccess, merchantAccess, reviewAccess, mobileLabel, statusTone } from '../services/mobile-presentation';

@Component({ standalone: true, imports: [IonButton, IonContent, IonHeader, IonTitle, IonToolbar, MobileMenuButtonComponent], template: `
  <ion-header class="mobile-header"><ion-toolbar><app-mobile-menu-button slot="start" /><ion-title><span class="brand-mark">FP</span> FuturPayment <small>Merchant</small></ion-title></ion-toolbar></ion-header>
  <ion-content class="mobile-page">
    <main class="mobile-container">
      <section class="welcome-panel"><div class="welcome-meta"><span class="profile-chip">{{ label(auth.user()?.role) }}</span><span class="context-chip">{{ auth.user()?.acquirerId || 'Choisir un rattachement' }}</span></div>
        <p class="welcome-caption">Votre espace professionnel</p><h1>Bonjour {{ auth.user()?.login }}</h1><p>Vos démarches, vos dossiers et vos prochaines actions.</p>
      </section>
      <div class="section-heading"><h2>À la une</h2><button class="text-action" type="button" (click)="refresh()" [disabled]="loading()">Actualiser</button></div>
      @if (loading()) { <div class="mobile-state" role="status"><span class="loading-dot"></span> Mise à jour de votre activité…</div> }
      @if (error()) { <div class="mobile-notice danger" role="alert">{{ error() }}</div> }
      @if (count() !== null) { <section class="metric-card"><span>{{ countLabel() }}</span><strong>{{ count() }}</strong><p>{{ countScope() }}</p></section> }
      @if (dossier(); as item) { <section class="mobile-surface dossier-summary"><div class="card-topline"><span class="eyebrow">Dossier d’affiliation</span><span class="status-pill" [attr.data-tone]="tone(item.kycStatus)">{{ label(item.kycStatus) }}</span></div>
        <h2>{{ item.tradingName || item.legalName || 'Mon commerce' }}</h2><p class="reference">{{ item.reference }}</p>
        <dl class="summary-lines"><div><dt>Avancement</dt><dd>{{ label(item.status) }}</dd></div><div><dt>Vérification des pièces</dt><dd>{{ label(item.kycStatus) }}</dd></div></dl>
        @if (item.complementReason) { <div class="mobile-notice warning"><strong>Une action est attendue</strong><p>{{ item.complementReason }}</p></div> }
        <ion-button expand="block" (click)="go('/merchant/dossier')">Ouvrir mon dossier</ion-button>
      </section> }
      @if (!loading() && !error() && isMerchant() && !dossier()) { <div class="mobile-state"><h3>Retrouvez votre commerce</h3><p>Choisissez votre rattachement pour consulter votre dossier.</p></div> }
      <div class="section-heading"><h2>Accès rapides</h2></div>
      <div class="action-grid">
        @if (isCommercial()) {
          <button class="action-card accent" (click)="go('/commercial/prospect')"><span class="action-symbol" aria-hidden="true">＋</span><strong>Nouveau prospect</strong><span>Inviter un commerçant et suivre son dossier</span><span class="action-arrow" aria-hidden="true">→</span></button>
          <button class="action-card" (click)="go('/commercial/dossiers')"><span class="action-symbol" aria-hidden="true">▤</span><strong>Dossiers commerciaux</strong><span>Filtrer par acquéreur et étape commerciale</span><span class="action-arrow" aria-hidden="true">→</span></button>
        }
        @if (isReview()) { <button class="action-card" (click)="go('/backoffice/dossiers')"><span class="action-symbol" aria-hidden="true">✓</span><strong>Contrôles KYC</strong><span>Consulter les pièces et traiter les dossiers autorisés</span><span class="action-arrow" aria-hidden="true">→</span></button> }
        @if (isMerchant()) {
          <button class="action-card" (click)="go('/merchant/associations')"><span class="action-symbol" aria-hidden="true">↗</span><strong>Mes rattachements</strong><span>Choisir le commerce et l’acquéreur actifs</span><span class="action-arrow" aria-hidden="true">→</span></button>
          @if (!dossier()) { <button class="action-card" (click)="go('/merchant/dossier')"><span class="action-symbol" aria-hidden="true">▤</span><strong>Mon dossier d’affiliation</strong><span>Consulter les informations et les pièces demandées</span><span class="action-arrow" aria-hidden="true">→</span></button> }
        }
        @if (isEmailSupport()) { <button class="action-card" (click)="go('/backoffice/email-assistance')"><span class="action-symbol" aria-hidden="true">&#64;</span><strong>Assistance e-mail</strong><span>Accompagner un utilisateur après perte d’accès</span><span class="action-arrow" aria-hidden="true">→</span></button> }
      </div>
      <p class="mobile-footnote">Les informations et les actions sont limitées à vos habilitations et à votre acquéreur.</p>
    </main>
  </ion-content>` })
export class MobileHomePage implements OnDestroy {
  readonly auth = inject(MobileAuthService);
  private readonly service = inject(MerchantOnboardingService);
  private readonly commercial = inject(MerchantCommercialService);
  private readonly router = inject(Router);
  private readonly cancel = new Subject<void>();
  readonly dossier = signal<MerchantDossier | null>(null);
  readonly count = signal<number | null>(null); readonly countLabel = signal(''); readonly countScope = signal('');
  readonly loading = signal(false); readonly error = signal(''); readonly label = mobileLabel; readonly tone = statusTone;
  isCommercial = () => commercialAccess(this.auth.user()); isMerchant = () => merchantAccess(this.auth.user()); isReview = () => reviewAccess(this.auth.user());
  isEmailSupport = () => this.auth.user()?.role === 'BACK_OFFICE' && !!this.auth.user()?.permissions.includes('ONBOARDING_PERSONAL_EMAIL_SUPPORT');
  ionViewWillEnter(): void { this.refresh(); }
  ionViewWillLeave(): void { this.clear(); }
  ngOnDestroy(): void { this.clear(); this.cancel.complete(); }
  private clear(): void { this.cancel.next(); this.dossier.set(null); this.count.set(null); this.loading.set(false); this.error.set(''); }
  refresh(): void {
    this.clear(); const user = this.auth.user(); if (!user) return;
    if (this.isCommercial()) {
      this.loading.set(true);
      this.service.allowedAcquirers().pipe(takeUntil(this.cancel)).subscribe({ next: acquirers => {
        const selected = acquirers.find(a => a.code === user.acquirerId || a.id === user.acquirerId) || (acquirers.length === 1 ? acquirers[0] : null);
        if (!selected) { this.loading.set(false); this.countScope.set('Sélectionnez un acquéreur dans Dossiers.'); return; }
        this.commercial.queue(selected.code, '', 0, 1).pipe(takeUntil(this.cancel)).subscribe({ next: page => {
          this.count.set(page.total); this.countLabel.set('Dossiers commerciaux accessibles'); this.countScope.set('Total retourné par le serveur · ' + selected.name); this.loading.set(false);
        }, error: () => this.failed() });
      }, error: () => this.failed() });
    } else if (this.isReview()) {
      this.loading.set(true); this.service.reviewQueue().pipe(takeUntil(this.cancel)).subscribe({ next: items => {
        this.count.set(items.length); this.countLabel.set('Dossiers dans votre file KYC'); this.countScope.set('File complète retournée pour vos habilitations'); this.loading.set(false);
      }, error: () => this.failed() });
    } else if (this.isMerchant()) {
      this.loading.set(true); mobileDossierSummary(this.service).pipe(takeUntil(this.cancel)).subscribe({ next: dossier => { this.dossier.set(dossier); this.loading.set(false); },
        error: err => { if (err.status === 404) this.loading.set(false); else this.failed(); } });
    }
  }
  private failed(): void { this.loading.set(false); this.error.set('Votre activité n’a pas pu être chargée. Actualisez pour réessayer.'); }
  go(route: string): void { void this.router.navigateByUrl(route); }
}
