import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar } from '@ionic/angular/standalone';
import { MerchantDossierComponent } from '../../app/features/merchant-dossier/merchant-dossier.component';
@Component({standalone:true,imports:[MobileMenuButtonComponent,IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar,MerchantDossierComponent],
 template:`<ion-header><ion-toolbar color="primary"><app-mobile-menu-button slot="start" /><ion-buttons slot="end"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Mon dossier</ion-title></ion-toolbar></ion-header><ion-content>@if(active()){<app-merchant-dossier />}</ion-content>`})
export class MobilePersonalDossierPage {
  private readonly detector=inject(ChangeDetectorRef);
  readonly active=signal(false);
  ionViewWillEnter():void { this.active.set(true); }
  ionViewWillLeave():void { this.clearCachedView(); }
  ionViewDidLeave():void { this.clearCachedView(); }
  private clearCachedView():void { this.active.set(false);this.detector.detectChanges(); }
}
