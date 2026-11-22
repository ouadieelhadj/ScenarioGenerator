import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router,RouterLink } from '@angular/router';
import {
  IonButton, IonCard, IonCardContent, IonContent, IonHeader, IonInput,
  IonItem, IonList, IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import { MobileAuthService } from '../services/mobile-auth.service';
import { CommercialConfirmationHandoffService } from '../../app/core/auth/commercial-confirmation-handoff.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, IonButton, IonCard, IonCardContent, IonContent,
    IonHeader, IonInput, IonItem, IonList, IonTitle, IonToolbar,RouterLink],
  template: `
    <ion-header class="mobile-header"><ion-toolbar><ion-title><span class="brand-mark">FP</span> FuturPayment <small>Merchant</small></ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page login-page"><main class="mobile-container">
      <section class="login-intro"><span class="profile-chip">Votre espace professionnel</span><h1>Votre commerce.<br />Toujours à portée de main.</h1><p>Retrouvez vos dossiers et vos prochaines actions, où que vous soyez.</p></section>
      <ion-card class="mobile-card">
        <ion-card-content>
          <h2>Se connecter</h2><p>Utilisez votre compte existant. Les accès sont adaptés à votre profil.</p>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <ion-list lines="full">
              <ion-item><ion-input label="Identifiant" labelPlacement="stacked" formControlName="login" autocomplete="username" autocapitalize="off" autocorrect="off" [spellcheck]="false" /></ion-item>
              <ion-item><ion-input label="Mot de passe" labelPlacement="stacked" type="password" formControlName="password" autocomplete="current-password" autocapitalize="off" autocorrect="off" [spellcheck]="false" /></ion-item>
            </ion-list>
            @if (error()) { <p class="form-error" role="alert">{{ error() }}</p> }
            <ion-button expand="block" type="submit" [disabled]="form.invalid || loading()">
              {{ loading() ? 'Connexion...' : 'Se connecter' }}
            </ion-button>
          </form>
          <a class="recovery-link" routerLink="/onboarding">Commencer mon affiliation</a>
          <a class="recovery-link" routerLink="/account/recovery">Mot de passe oublié ?</a>
        </ion-card-content>
      </ion-card>
      <p class="mobile-footnote">Commerçant · Commercial · Back-office<br />Un accès personnel, des droits maîtrisés.</p>
    </main></ion-content>
  `,
})
export class MobileLoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(MobileAuthService);
  private readonly router = inject(Router);
  private readonly confirmationHandoff=inject(CommercialConfirmationHandoffService);
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
      next: () => {
        this.loading.set(false);
        this.form.controls.password.reset('');
        this.router.navigateByUrl(this.confirmationHandoff.resumePath(this.auth.user()?.role)||'/home');
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.form.controls.password.reset('');
        const status=error instanceof HttpErrorResponse?error.status:-1;
        this.error.set(status===400||status===401?'Identifiant ou mot de passe incorrect.':
          status===403?'Accès à la connexion refusé. Réessayez ou contactez votre support.':
          status===429?'Trop de tentatives. Patientez avant de réessayer.':
          'Le service de connexion est indisponible. Vérifiez votre connexion puis réessayez.');
      },
    });
  }
}
