import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonBackButton, IonButton, IonButtons, IonCard, IonCardContent, IonContent,
  IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, IonBackButton, IonButton, IonButtons, IonCard,
    IonCardContent, IonContent, IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar],
  template: `
    <ion-header><ion-toolbar color="primary"><ion-buttons slot="start"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Nouveau prospect</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">
      <p>Le commercant recevra son invitation et choisira lui-meme son mot de passe.</p>
      <ion-card class="mobile-card"><ion-card-content>
        <form [formGroup]="form" (ngSubmit)="create()">
          <ion-list lines="full">
            <ion-item><ion-input label="Identifiant" labelPlacement="stacked" formControlName="login" /></ion-item>
            <ion-item><ion-input label="E-mail" labelPlacement="stacked" type="email" formControlName="email" /></ion-item>
            <ion-item><ion-input label="Acquereur" labelPlacement="stacked" formControlName="acquirerId" /></ion-item>
          </ion-list>
          @if (error()) { <p class="form-error">{{ error() }}</p> }
          @if (reference()) { <p class="form-success">Dossier {{ reference() }} cree. Le lien d'activation est affiche une seule fois.</p> }
          @if (activationLink()) { <ion-item><ion-input label="Lien d'activation" labelPlacement="stacked" [value]="activationLink()" readonly /></ion-item> }
          <ion-button expand="block" type="submit" [disabled]="form.invalid || loading()">Creer et inviter</ion-button>
        </form>
      </ion-card-content></ion-card>
    </ion-content>
  `,
})
export class MobileCommercialProspectPage {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MerchantOnboardingService);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly reference = signal('');
  readonly activationLink = signal('');
  readonly form = this.fb.nonNullable.group({
    login: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    acquirerId: ['', Validators.required],
  });

  create(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true); this.error.set(''); this.activationLink.set('');
    this.service.createProspect(this.form.getRawValue()).subscribe({
      next: prospect => {
        this.loading.set(false);
        this.reference.set(prospect.dossier.reference);
        const token = prospect.identityInvitation?.activationToken;
        this.activationLink.set(token ? `${location.origin}/activation?token=${encodeURIComponent(token)}` : '');
      },
      error: () => { this.loading.set(false); this.error.set('Creation impossible. Verifiez les donnees ou le doublon.'); },
    });
  }
}
