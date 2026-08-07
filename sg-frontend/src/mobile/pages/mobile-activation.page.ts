import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  IonButton, IonCard, IonCardContent, IonContent, IonHeader, IonInput,
  IonItem, IonList, IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';

const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/;

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, IonButton, IonCard, IonCardContent, IonContent,
    IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar],
  template: `
    <ion-header><ion-toolbar color="primary"><ion-title>Activation du compte</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">
      <p class="eyebrow">Invitation commercant</p>
      <h1>Creez votre mot de passe</h1>
      <p>Au moins 12 caracteres avec majuscule, minuscule, chiffre et caractere special.</p>
      <ion-card class="mobile-card"><ion-card-content>
        <form [formGroup]="form" (ngSubmit)="activate()">
          <ion-list lines="full">
            <ion-item><ion-input label="Nouveau mot de passe" labelPlacement="stacked" type="password" formControlName="password" /></ion-item>
            <ion-item><ion-input label="Confirmer" labelPlacement="stacked" type="password" formControlName="confirmation" /></ion-item>
          </ion-list>
          @if (!token) { <p class="form-error">Lien d'activation incomplet.</p> }
          @if (error()) { <p class="form-error">{{ error() }}</p> }
          @if (success()) { <p class="form-success">Compte active. Vous pouvez vous connecter.</p> }
          <ion-button expand="block" type="submit" [disabled]="form.invalid || !token || loading()">Activer mon compte</ion-button>
          @if (success()) { <ion-button expand="block" fill="clear" type="button" (click)="login()">Continuer vers la connexion</ion-button> }
        </form>
      </ion-card-content></ion-card>
    </ion-content>
  `,
})
export class MobileActivationPage {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MerchantOnboardingService);
  private readonly router = inject(Router);
  readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
  readonly loading = signal(false);
  readonly success = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.pattern(STRONG_PASSWORD)]],
    confirmation: ['', Validators.required],
  });

  activate(): void {
    const { password, confirmation } = this.form.getRawValue();
    if (this.form.invalid || password !== confirmation || !this.token) {
      this.error.set(password !== confirmation ? 'Les mots de passe sont differents.' : 'Mot de passe non conforme.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.service.activate(this.token, password).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => { this.loading.set(false); this.error.set("Le lien n'est plus valide ou a deja ete utilise."); },
    });
  }

  login(): void { this.router.navigateByUrl('/login'); }
}
