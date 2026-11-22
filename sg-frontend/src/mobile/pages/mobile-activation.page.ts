import { Component, inject, signal, OnDestroy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, Subscription, filter, takeUntil } from 'rxjs';
import { ActivationHandoffService } from '../../app/core/auth/activation-handoff.service';
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
      <h1>{{ success() ? 'Votre compte est actif' : 'Creez votre mot de passe' }}</h1>
      @if (!success()) { <p>Au moins 12 caracteres avec majuscule, minuscule, chiffre et caractere special.</p> }
      <ion-card class="mobile-card"><ion-card-content>
        @if (!success()) { <form [formGroup]="form" (ngSubmit)="activate()">
          <ion-list lines="full">
            <ion-item><ion-input label="Nouveau mot de passe" labelPlacement="stacked" type="password" formControlName="password" /></ion-item>
            <ion-item><ion-input label="Confirmer" labelPlacement="stacked" type="password" formControlName="confirmation" /></ion-item>
          </ion-list>
          @if (!token && !error()) { <p class="form-error">Lien d'activation incomplet.</p> }
          @if (error()) { <p class="form-error">{{ error() }}</p> }
          <ion-button expand="block" type="submit" [disabled]="form.invalid || !token || loading()">Activer mon compte</ion-button>
        </form> } @else { <p class="form-success">Compte active. Vous pouvez vous connecter.</p>
          <ion-button expand="block" fill="clear" type="button" (click)="login()">Continuer vers la connexion</ion-button> }
      </ion-card-content></ion-card>
    </ion-content>
  `,
  styles:[`.form-success{color:#067647;line-height:1.5}.form-error{color:#b42318;line-height:1.5}`],
})
export class MobileActivationPage implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MerchantOnboardingService);
  private readonly router = inject(Router);
  private readonly handoff=inject(ActivationHandoffService);
  private readonly subscription:Subscription;
  private readonly navigation:Subscription;
  private readonly cancellation=new Subject<void>();
  private readonly changedLink=()=>this.readAddress();
  private active=true;
  private requestRevision=0;
  token = '';
  readonly loading = signal(false);
  readonly success = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.pattern(STRONG_PASSWORD)]],
    confirmation: ['', Validators.required],
  });

  constructor(){
    this.subscription=this.handoff.changes.subscribe(()=>{if(this.active)this.loadPending(true);});
    this.readAddress();this.loadPending();window.addEventListener('hashchange',this.changedLink);
    this.navigation=this.router.events.pipe(filter(event=>event instanceof NavigationEnd)).subscribe(()=>{
      if(!this.active)return;this.readAddress();this.scrubRouter();
    });
  }
  private readAddress():void{
    if(this.active&&window.location.pathname==='/activation')this.handoff.capture(window.location.pathname+window.location.search+window.location.hash);
  }
  private scrubRouter():void{
    const address=this.router.parseUrl(this.router.url),fragment=new URLSearchParams(address.fragment||'');
    if(Object.prototype.hasOwnProperty.call(address.queryParams,'token')||fragment.has('token')){
      delete address.queryParams['token'];fragment.delete('token');address.fragment=fragment.toString()||null;
      void this.router.navigateByUrl(address,{replaceUrl:true});
    }
  }
  private loadPending(discardEmpty=false):void {
    const token=this.handoff.take();if(!token&&!discardEmpty)return;if(token&&token===this.token)return;
    ++this.requestRevision;this.cancellation.next();this.token=token;this.form.reset();this.loading.set(false);this.success.set(false);this.error.set('');
  }
  ngOnDestroy():void {++this.requestRevision;this.active=false;this.navigation.unsubscribe();window.removeEventListener('hashchange',this.changedLink);this.subscription.unsubscribe();this.cancellation.next();this.cancellation.complete();this.handoff.clear();this.token='';this.form.reset();}
  ionViewWillEnter():void {this.active=true;this.readAddress();this.loadPending();this.scrubRouter();}
  ionViewWillLeave():void {++this.requestRevision;this.active=false;this.cancellation.next();this.handoff.clear();this.token='';this.loading.set(false);this.form.reset();}

  activate(): void {
    const { password, confirmation } = this.form.getRawValue();
    if (this.loading()||this.success())return;
    if (this.form.invalid || password !== confirmation || !this.token) {
      this.error.set(password !== confirmation ? 'Les mots de passe sont differents.' : 'Mot de passe non conforme.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const revision=this.requestRevision;
    this.service.activate(this.token, password).pipe(takeUntil(this.cancellation)).subscribe({
      next: () => { if(revision!==this.requestRevision)return;this.token='';this.form.reset();this.loading.set(false);this.success.set(true); },
      error: failure => { if(revision!==this.requestRevision)return;this.form.reset();this.loading.set(false);
        if([400,409,410].includes(failure.status)){this.token='';this.error.set("Le lien est invalide, expire ou deja utilise.");}
        else this.error.set("L'activation est indisponible. Vous pouvez réessayer."); },
    });
  }

  login(): void { this.router.navigateByUrl('/login'); }
}
