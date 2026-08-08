import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/models/auth.models';

@Component({
  selector: 'app-merchant-dashboard',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="page-header">
      <div><span class="eyebrow">PORTAIL D'AFFILIATION</span><h1>Bienvenue, {{ user()?.login }}</h1>
        <p>Un point d'entree unique pour preparer, controler et suivre l'affiliation commercant.</p></div>
      <span class="role">{{ user()?.role }}</span>
    </header>
    <section class="cards">
      @if (canCreateProspect) {
        <article><i class="pi pi-user-plus"></i><h2>Nouveau prospect</h2><p>Creer le compte initial et son dossier sans saisir de mot de passe.</p><a routerLink="/commercial/prospects/new">Creer une invitation</a></article>
      }
      @if (isMerchant) {
        <article><i class="pi pi-file-edit"></i><h2>Mon dossier</h2><p>Reprendre la saisie, deposer les pieces KYC et soumettre le dossier.</p><a routerLink="/merchant/dossier">Continuer mon onboarding</a></article>
      }
      @if (canReview) {
        <article><i class="pi pi-check-square"></i><h2>Maker / Checker</h2><p>Consulter les demandes reelles d'onboarding en attente de decision.</p><a routerLink="/workflow/my-approvals">Ouvrir mes validations</a></article>
      }
      <article><i class="pi pi-mobile"></i><h2>Application mobile</h2><p>L'APK Android reutilise le meme dossier et les memes regles de validation.</p><span class="planned">Disponible en recette</span></article>
    </section>
  `,
  styles: [`
    .page-header{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:24px}.eyebrow{font-size:12px;font-weight:700;letter-spacing:.12em;color:var(--sg-color-primary)}h1{margin:8px 0}p{color:var(--sg-text-muted);line-height:1.55}.role,.planned,.blocked{display:inline-flex;padding:6px 10px;border-radius:999px;background:var(--sg-bg-muted);font-size:12px}.role{color:var(--sg-color-primary);font-weight:700}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px}.cards article{padding:22px;border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);background:var(--sg-bg-surface)}.cards i{font-size:26px;color:var(--sg-color-primary)}.cards h2{font-size:17px;margin:16px 0 8px}.cards a{display:inline-flex;margin-top:10px;color:var(--sg-color-primary);font-weight:600;text-decoration:none}.blocked{color:#a15c00}.planned{color:var(--sg-text-muted)}
  `],
})
export class MerchantDashboardComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.user;
  readonly isMerchant = this.auth.hasRole('MERCHANT');
  readonly canCreateProspect = this.auth.hasPermission(Permission.ONBOARDING_PROSPECT_CREATE) || this.auth.hasRole('COMMERCIAL') || this.auth.hasRole('ADMIN');
  readonly canReview = this.auth.hasAnyPermission([Permission.ONBOARDING_APPROVE, Permission.ONBOARDING_KYC_REVIEW]) || ['CHECKER', 'BACK_OFFICE', 'ADMIN'].some(role => this.auth.hasRole(role));
}
