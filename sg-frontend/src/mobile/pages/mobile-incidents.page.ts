import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar } from '@ionic/angular/standalone';
import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { MerchantIncidentsComponent } from '../../app/features/merchant-incidents/merchant-incidents.component';

@Component({standalone:true,imports:[IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar,MobileMenuButtonComponent,MerchantIncidentsComponent],
 template:`<ion-header><ion-toolbar><app-mobile-menu-button slot="start" /><ion-buttons slot="end"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Assistance · Incidents</ion-title></ion-toolbar></ion-header><ion-content>@if(active()){<app-merchant-incidents [mobile]="true" />}</ion-content>`})
export class MobileIncidentsPage {
 readonly active=signal(false);private readonly detector=inject(ChangeDetectorRef);
 ionViewWillEnter(){this.active.set(true);}
 ionViewWillLeave(){this.clear();}ionViewDidLeave(){this.clear();}
 private clear(){this.active.set(false);this.detector.detectChanges();}
}
