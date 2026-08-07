import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
  IonButton, IonCard, IonCardContent, IonContent, IonHeader, IonInput,
  IonItem, IonList, IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import { MobileAuthService } from '../services/mobile-auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, IonButton, IonCard, IonCardContent, IonContent,
    IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar],
  template: `
    <ion-header><ion-toolbar color="primary"><ion-title>FuturPayment Merchant</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">
      <p class="eyebrow">Connexion securisee</p>
      <h1>Bienvenue</h1>
      <p>Accedez a votre espace Commercant ou Commercial avec votre compte existant.</p>
      <ion-card class="mobile-card">
        <ion-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <ion-list lines="full">
              <ion-item><ion-input label="Identifiant" labelPlacement="stacked" formControlName="login" autocomplete="username" /></ion-item>
              <ion-item><ion-input label="Mot de passe" labelPlacement="stacked" type="password" formControlName="password" autocomplete="current-password" /></ion-item>
            </ion-list>
            @if (error()) { <p class="form-error">{{ error() }}</p> }
            <ion-button expand="block" type="submit" [disabled]="form.invalid || loading()">
              {{ loading() ? 'Connexion...' : 'Se connecter' }}
            </ion-button>
          </form>
        </ion-card-content>
      </ion-card>
    </ion-content>
  `,
})
export class MobileLoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(MobileAuthService);
  private readonly router = inject(Router);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/home'),
      error: () => { this.loading.set(false); this.error.set('Identifiant ou mot de passe incorrect.'); },
    });
  }
}
