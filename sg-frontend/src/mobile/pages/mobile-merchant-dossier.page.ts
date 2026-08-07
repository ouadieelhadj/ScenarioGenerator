import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonBackButton, IonBadge, IonButton, IonButtons, IonCard, IonCardContent,
  IonContent, IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import { MerchantDossier } from '../../app/core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, IonBackButton, IonBadge, IonButton, IonButtons,
    IonCard, IonCardContent, IonContent, IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar],
  template: `
    <ion-header><ion-toolbar color="primary"><ion-buttons slot="start"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Mon dossier</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">
      <p>Le dossier est le meme que sur le portail web. Saisissez son identifiant pour ce premier increment.</p>
      <form [formGroup]="form" (ngSubmit)="load()">
        <ion-item><ion-input label="Identifiant du dossier" labelPlacement="stacked" formControlName="id" /></ion-item>
        <ion-button expand="block" type="submit" [disabled]="form.invalid || loading()">Consulter</ion-button>
      </form>
      @if (error()) { <p class="form-error">{{ error() }}</p> }
      @if (dossier(); as item) {
        <ion-card class="mobile-card"><ion-card-content>
          <p class="eyebrow">{{ item.reference }}</p>
          <h2>{{ item.legalName || item.tradingName || 'Dossier a completer' }}</h2>
          <p><ion-badge color="primary">{{ item.status }}</ion-badge> <ion-badge color="tertiary">KYC {{ item.kycStatus }}</ion-badge></p>
          <ion-list lines="full">
            <ion-item>Immatriculation : {{ item.registrationNumber || 'A renseigner' }}</ion-item>
            <ion-item>Pays : {{ item.country || 'A renseigner' }}</ion-item>
            <ion-item>MCC : {{ item.mcc || 'A renseigner' }}</ion-item>
            <ion-item>Terminaux demandes : {{ item.terminalCount }}</ion-item>
          </ion-list>
          <p>La modification detaillee et les photos KYC seront ouvertes apres extension du contrat dossier.</p>
        </ion-card-content></ion-card>
      }
    </ion-content>
  `,
})
export class MobileMerchantDossierPage {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MerchantOnboardingService);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly dossier = signal<MerchantDossier | null>(null);
  readonly form = this.fb.nonNullable.group({ id: ['', Validators.required] });

  load(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true); this.error.set(''); this.dossier.set(null);
    this.service.dossier(this.form.getRawValue().id).subscribe({
      next: dossier => { this.loading.set(false); this.dossier.set(dossier); },
      error: () => { this.loading.set(false); this.error.set('Dossier introuvable ou acces refuse.'); },
    });
  }
}
