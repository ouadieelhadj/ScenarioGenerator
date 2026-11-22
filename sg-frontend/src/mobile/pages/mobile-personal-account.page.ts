import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { Component,inject,ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { IonHeader,IonToolbar,IonTitle,IonContent,IonButtons,IonBackButton } from '@ionic/angular/standalone';
import { PersonalAccountFormComponent,PersonalEmailSettingsComponent } from '../../app/features/personal-account/personal-account-forms.component';
import { MobileAuthService } from '../services/mobile-auth.service';
@Component({standalone:true,imports:[IonHeader,IonToolbar,IonTitle,IonContent,PersonalAccountFormComponent],template:`<ion-header><ion-toolbar color="primary"><ion-title>Mon compte personnel</ion-title></ion-toolbar></ion-header><ion-content><app-personal-account-form [flow]="flow" loginPath="/login" (confirmed)="resetSession()" /></ion-content>`})
export class MobilePersonalAccountPage {
 private readonly route=inject(ActivatedRoute);private readonly auth=inject(MobileAuthService);
 readonly flow:'PASSWORD_RECOVERY'|'EMAIL_CHANGE'=this.route.snapshot.data['flow']==='EMAIL_CHANGE'?'EMAIL_CHANGE':'PASSWORD_RECOVERY';
 @ViewChild(PersonalAccountFormComponent) private form?:PersonalAccountFormComponent;
 ionViewWillLeave():void{this.form?.leave();}
 resetSession():void{this.auth.logout();}
}
@Component({standalone:true,imports:[IonHeader,IonToolbar,IonTitle,IonContent,IonButtons,IonBackButton,MobileMenuButtonComponent,PersonalEmailSettingsComponent],template:`<ion-header><ion-toolbar color="primary"><app-mobile-menu-button slot="start" /><ion-buttons slot="end"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Mon adresse personnelle</ion-title></ion-toolbar></ion-header><ion-content><app-personal-email-settings /></ion-content>`})
export class MobilePersonalEmailSettingsPage {
 @ViewChild(PersonalEmailSettingsComponent) private form?:PersonalEmailSettingsComponent;
 ionViewWillLeave():void{this.form?.leave();}
}
