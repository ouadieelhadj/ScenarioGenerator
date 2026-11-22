import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { Component, ViewChild, inject } from '@angular/core';
import { IonBackButton, IonButtons, IonContent, IonHeader, IonTitle, IonToolbar } from '@ionic/angular/standalone';
import { MobileAuthService } from '../services/mobile-auth.service';
import { PersonalMerchantAssociationsView } from '../../app/features/personal-merchant-associations/personal-merchant-associations.view';
import type { PersonalContextSession } from '../../app/core/auth/personal-context-session';

@Component({ standalone:true, imports:[MobileMenuButtonComponent,IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar,PersonalMerchantAssociationsView], template:`
  <ion-header><ion-toolbar color="primary"><app-mobile-menu-button slot="start" /><ion-buttons slot="end"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Mes rattachements</ion-title></ion-toolbar></ion-header>
  <ion-content class="mobile-page">@if (entered) {
    <app-personal-merchant-associations [currentLogin]="auth.user()?.login || ''" [currentAcquirer]="auth.user()?.acquirerId || ''"
      (contextSelected)="select($event)" />
  }</ion-content>` })
export class MobilePersonalAssociationsPage {
  readonly auth=inject(MobileAuthService); entered=false;
  @ViewChild(PersonalMerchantAssociationsView) view?:PersonalMerchantAssociationsView;
  ionViewWillEnter():void{this.entered=true;}
  ionViewDidLeave():void{this.entered=false;}
  select(event:{session:PersonalContextSession;requested:string}):void{
    if(!this.auth.acceptPersonalContext(event.session,event.requested))this.view?.rejectContext();
  }
}
