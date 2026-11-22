import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { IonBackButton, IonButtons, IonContent, IonHeader, IonTitle, IonToolbar } from '@ionic/angular/standalone';
import { MerchantOnboardingEntryComponent } from '../../app/features/merchant-onboarding-entry/merchant-onboarding-entry.component';

@Component({
  standalone: true,
  imports: [IonBackButton, IonButtons, IonContent, IonHeader, IonTitle, IonToolbar, MerchantOnboardingEntryComponent],
  template: `<ion-header class="mobile-header"><ion-toolbar><ion-buttons slot="start"><ion-back-button defaultHref="/login" /></ion-buttons><ion-title>Affiliation commerçant</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">@if(active()){<app-merchant-onboarding-entry resumePath="/merchant/dossier" />}</ion-content>`,
})
export class MobileOnboardingPage {
  private readonly detector=inject(ChangeDetectorRef);
  readonly active=signal(false);
  ionViewWillEnter():void { this.active.set(true); }
  // Destroy pending public requests and entered email when Ionic caches this page.
  ionViewWillLeave():void { this.clearCachedView(); }
  ionViewDidLeave():void { this.clearCachedView(); }
  private clearCachedView():void { this.active.set(false);this.detector.detectChanges(); }
}
