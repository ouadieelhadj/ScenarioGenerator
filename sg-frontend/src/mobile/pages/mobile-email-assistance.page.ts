import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import {Component,inject,ViewChild} from '@angular/core';
import {IonHeader,IonToolbar,IonTitle,IonContent,IonButtons,IonBackButton} from '@ionic/angular/standalone';
import {MobileAuthService} from '../services/mobile-auth.service';
import {PersonalEmailAssistanceComponent} from '../../app/features/personal-account/personal-email-assistance.component';
@Component({standalone:true,imports:[IonHeader,IonToolbar,IonTitle,IonContent,IonButtons,MobileMenuButtonComponent,IonBackButton,PersonalEmailAssistanceComponent],template:`<ion-header><ion-toolbar color="primary"><app-mobile-menu-button slot="start" /><ion-buttons slot="end"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Assistance acquéreur</ion-title></ion-toolbar></ion-header><ion-content><app-personal-email-assistance [authorized]="authorized()" [acquirerCode]="auth.user()?.acquirerId || ''" /></ion-content>`})
export class MobileEmailAssistancePage {
 readonly auth=inject(MobileAuthService);@ViewChild(PersonalEmailAssistanceComponent) form?:PersonalEmailAssistanceComponent;
 authorized():boolean{return this.auth.user()?.role==='BACK_OFFICE'&&(this.auth.user()?.permissions.includes('ONBOARDING_PERSONAL_EMAIL_SUPPORT')??false);}
 ionViewWillLeave():void{this.form?.reset();}
}
