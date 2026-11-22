import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import {ChangeDetectorRef, Component, inject, signal} from '@angular/core';
import {IonBackButton, IonButtons, IonContent, IonHeader, IonTitle, IonToolbar} from '@ionic/angular/standalone';
import {MerchantProspectComponent} from '../../app/features/merchant-prospect/merchant-prospect.component';

/** Shares the durable creation contract with Web; never collects a password or displays the activation link. */
@Component({standalone:true,imports:[MobileMenuButtonComponent,IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar,MerchantProspectComponent],
  template:`<ion-header><ion-toolbar color="primary"><app-mobile-menu-button slot="start" /><ion-buttons slot="end"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Nouveau prospect</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">@if(active()){<app-merchant-prospect dossierRoute="/commercial/dossiers" />}</ion-content>`})
export class MobileCommercialProspectPage {
  private readonly detector=inject(ChangeDetectorRef);
  readonly active=signal(false);
  ionViewWillEnter():void{this.active.set(true);}
  ionViewWillLeave():void{this.clearCachedView();}
  ionViewDidLeave():void{this.clearCachedView();}
  private clearCachedView():void{this.active.set(false);this.detector.detectChanges();}
}
