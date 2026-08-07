import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonButton, IonCard, IonCardContent, IonContent, IonHeader, IonIcon,
  IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import { MobileAuthService } from '../services/mobile-auth.service';

@Component({
  standalone: true,
  imports: [IonButton, IonCard, IonCardContent, IonContent, IonHeader, IonIcon, IonTitle, IonToolbar],
  template: `
    <ion-header><ion-toolbar color="primary"><ion-title>Mon espace</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">
      <p class="eyebrow">{{ auth.user()?.role }}</p>
      <h1>Bonjour {{ auth.user()?.login }}</h1>
      @if (isCommercial()) {
        <ion-card class="mobile-card"><ion-card-content>
          <ion-icon name="person-add-outline" size="large" />
          <h2>Nouveau prospect</h2><p>Invitez un commercant sans creer son mot de passe.</p>
          <ion-button expand="block" (click)="go('/commercial/prospect')">Creer le prospect</ion-button>
        </ion-card-content></ion-card>
      }
      @if (isMerchant()) {
        <ion-card class="mobile-card"><ion-card-content>
          <ion-icon name="document-text-outline" size="large" />
          <h2>Mon dossier d'affiliation</h2><p>Consultez l'avancement du dossier partage avec le portail web.</p>
          <ion-button expand="block" (click)="go('/merchant/dossier')">Ouvrir mon dossier</ion-button>
        </ion-card-content></ion-card>
      }
      <ion-button expand="block" fill="outline" color="medium" (click)="logout()">Se deconnecter</ion-button>
    </ion-content>
  `,
})
export class MobileHomePage {
  readonly auth = inject(MobileAuthService);
  private readonly router = inject(Router);
  isCommercial(): boolean { return ['COMMERCIAL', 'ADMIN'].includes(this.auth.user()?.role ?? ''); }
  isMerchant(): boolean { return ['MERCHANT', 'COMMERCANT', 'ADMIN'].includes(this.auth.user()?.role ?? ''); }
  go(route: string): void { this.router.navigateByUrl(route); }
  logout(): void { this.auth.logout(); this.router.navigateByUrl('/login'); }
}
