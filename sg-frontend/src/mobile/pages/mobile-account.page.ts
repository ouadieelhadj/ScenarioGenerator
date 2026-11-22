import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonContent, IonHeader, IonTitle, IonToolbar } from '@ionic/angular/standalone';
import { MobileAuthService } from '../services/mobile-auth.service';
import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { merchantAccess, mobileLabel } from '../services/mobile-presentation';

@Component({standalone:true,imports:[IonButton,IonContent,IonHeader,IonTitle,IonToolbar,MobileMenuButtonComponent],template:`
  <ion-header class="mobile-header"><ion-toolbar><app-mobile-menu-button slot="start" /><ion-title>Mon espace</ion-title></ion-toolbar></ion-header>
  <ion-content class="mobile-page"><main class="mobile-container"><p class="eyebrow">Mon compte professionnel</p><h1>Mes accès</h1>
    <section class="mobile-surface"><div class="account-avatar" aria-hidden="true">{{ auth.user()?.login?.slice(0,2)?.toUpperCase() }}</div><h2>{{ auth.user()?.login }}</h2>
      <dl class="summary-lines"><div><dt>Profil</dt><dd>{{ label(auth.user()?.role) }}</dd></div><div><dt>Acquéreur actif</dt><dd>{{ auth.user()?.acquirerId || 'Non sélectionné' }}</dd></div></dl>
      <p>Votre profil et vos droits sont attribués par votre établissement.</p>
      @if (merchant()) { <ion-button expand="block" fill="outline" (click)="go('/merchant/associations')">Gérer mes rattachements</ion-button> }
      @if (auth.user()?.role==='MERCHANT') { <ion-button expand="block" fill="outline" (click)="go('/merchant/personal-account')">Mon adresse personnelle</ion-button> }
    </section><section class="mobile-surface"><h2>Terminer ma session</h2><p>Déconnectez-vous après utilisation sur un appareil partagé.</p><ion-button expand="block" color="danger" fill="outline" (click)="logout()">Se déconnecter</ion-button></section>
  </main></ion-content>`})
export class MobileAccountPage {
  readonly auth=inject(MobileAuthService); private readonly router=inject(Router); readonly label=mobileLabel;
  merchant=()=>merchantAccess(this.auth.user()); go=(path:string)=>this.router.navigateByUrl(path);
  logout():void { this.auth.logout(); void this.router.navigateByUrl('/login',{replaceUrl:true}); }
}
