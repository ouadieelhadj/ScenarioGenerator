import { Component, inject, OnInit, signal } from '@angular/core';
import { switchMap } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonBackButton, IonBadge, IonButton, IonButtons, IonCard, IonCardContent,
  IonContent, IonHeader, IonInput, IonItem, IonSelect, IonSelectOption,
  IonTitle, IonToolbar,
} from '@ionic/angular/standalone';
import {
  MerchantDocument, MerchantDocumentType, MerchantDossier, MerchantDossierUpdate,
  ProvisioningDestination,
} from '../../app/core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, IonBackButton, IonBadge, IonButton, IonButtons,
    IonCard, IonCardContent, IonContent, IonHeader, IonInput, IonItem, IonSelect,
    IonSelectOption, IonTitle, IonToolbar],
  template: `
    <ion-header><ion-toolbar color="primary"><ion-buttons slot="start"><ion-back-button defaultHref="/home" /></ion-buttons><ion-title>Mon auto-onboarding</ion-title></ion-toolbar></ion-header>
    <ion-content class="mobile-page">
      @if (loading()) { <p>Chargement du dossier...</p> }
      @if (error()) { <p class="form-error">{{ error() }}</p> }
      @if (message()) { <p class="form-success">{{ message() }}</p> }
      @if (dossier(); as item) {
        <ion-card class="mobile-card"><ion-card-content><p class="eyebrow">{{ item.reference }}</p><h2>{{ item.legalName || 'Dossier a completer' }}</h2><ion-badge>{{ item.status }}</ion-badge> <ion-badge color="tertiary">KYC {{ item.kycStatus }}</ion-badge>@if(item.complementReason){<p class="form-error">{{item.complementReason}}</p>}</ion-card-content></ion-card>
        <form [formGroup]="form" (ngSubmit)="save()">
          <ion-item><ion-input label="Raison sociale" labelPlacement="stacked" formControlName="legalName" /></ion-item>
          <ion-item><ion-input label="Nom commercial" labelPlacement="stacked" formControlName="tradingName" /></ion-item>
          <ion-item><ion-input label="Immatriculation" labelPlacement="stacked" formControlName="registrationNumber" /></ion-item>
          <ion-item><ion-input label="Pays ISO-2" labelPlacement="stacked" formControlName="country" /></ion-item>
          <ion-item><ion-input label="MCC" labelPlacement="stacked" inputmode="numeric" formControlName="mcc" /></ion-item>
          <ion-item><ion-input label="Compte de reglement" labelPlacement="stacked" formControlName="settlementAccountReference" /></ion-item>
          <ion-item><ion-input label="Devise ISO-4217" labelPlacement="stacked" inputmode="numeric" formControlName="settlementCurrency" /></ion-item>
          <ion-item><ion-input label="Produit Acquiring (UUID)" labelPlacement="stacked" formControlName="productId" /></ion-item>
          <ion-item><ion-select label="Système de destination" labelPlacement="stacked" formControlName="provisioningDestination"><ion-select-option value="FUTURPAYMENT">FuturPayment Acquiring</ion-select-option><ion-select-option value="WAY4">WAY4 Acquiring</ion-select-option><ion-select-option value="BOTH">FuturPayment + WAY4</ion-select-option></ion-select></ion-item>
          <ion-item><ion-select label="Canal" labelPlacement="stacked" formControlName="acceptanceChannel"><ion-select-option value="TPE">TPE</ion-select-option><ion-select-option value="ECOMMERCE">E-commerce</ion-select-option><ion-select-option value="BOTH">TPE + E-commerce</ion-select-option></ion-select></ion-item>
          <ion-item><ion-input label="Code point de vente" labelPlacement="stacked" formControlName="outletCode" /></ion-item>
          <ion-item><ion-input label="Nom point de vente" labelPlacement="stacked" formControlName="outletName" /></ion-item>
          <ion-item><ion-input label="Adresse point de vente" labelPlacement="stacked" formControlName="outletAddress" /></ion-item>
          <ion-item><ion-input label="Nombre de TPE" labelPlacement="stacked" type="number" formControlName="terminalCount" /></ion-item>
          <ion-button expand="block" type="submit" [disabled]="form.invalid || busy() || item.status !== 'DRAFT'">Enregistrer</ion-button>
        </form>

        <ion-card class="mobile-card"><ion-card-content><h2>Pieces KYC</h2>
          @for (type of requiredTypes; track type) { <section class="document"><strong>{{ label(type) }}</strong><small>{{ latest(type)?.reviewStatus || 'NON DEPOSE' }}</small><input type="file" accept="application/pdf,image/jpeg,image/png" capture="environment" (change)="choose(type,$event)" /><ion-button size="small" (click)="upload(type)" [disabled]="!selected[type] || busy()">Envoyer</ion-button></section> }
          <ion-button expand="block" (click)="submitKyc()" [disabled]="!canSubmitKyc(item) || busy()">Soumettre le KYC</ion-button>
        </ion-card-content></ion-card>
        <ion-card class="mobile-card"><ion-card-content><h2>Soumission Maker</h2><p>Apres validation KYC, soumettez le dossier a un Checker distinct.</p><ion-button expand="block" (click)="submitMaker()" [disabled]="item.status !== 'DRAFT' || item.kycStatus !== 'VALIDATED' || busy()">Soumettre le dossier</ion-button></ion-card-content></ion-card>
        @if(item.merchantAcceptorId){<ion-card color="success"><ion-card-content><h2>Affiliation terminee</h2><p>MID {{item.merchantAcceptorId}}</p></ion-card-content></ion-card>}
      }
    </ion-content>
  `,
  styles:[`.document{display:grid;gap:8px;padding:12px 0;border-bottom:1px solid var(--ion-color-light-shade)}.document small{color:var(--ion-color-medium)}input[type=file]{max-width:100%}`]
})
export class MobileMerchantDossierPage implements OnInit {
  private fb=inject(FormBuilder); private service=inject(MerchantOnboardingService);
  readonly loading=signal(true); readonly busy=signal(false); readonly error=signal(''); readonly message=signal(''); readonly dossier=signal<MerchantDossier|null>(null); readonly documents=signal<MerchantDocument[]>([]);
  readonly requiredTypes:MerchantDocumentType[]=['LEGAL_EXISTENCE','REPRESENTATIVE_IDENTITY','BANK_ACCOUNT_PROOF']; readonly selected:Partial<Record<MerchantDocumentType,File>>={};
  readonly form=this.fb.nonNullable.group({legalName:['',Validators.required],tradingName:['',Validators.required],registrationNumber:['',Validators.required],country:['MA',[Validators.required,Validators.pattern(/^[A-Z]{2}$/)]],mcc:['',[Validators.required,Validators.pattern(/^\d{4}$/)]],settlementAccountReference:['',Validators.required],settlementCurrency:['504',[Validators.required,Validators.pattern(/^\d{3}$/)]],productId:['',Validators.required],provisioningDestination:this.fb.nonNullable.control<ProvisioningDestination | ''>('',Validators.required),acceptanceChannel:['TPE',Validators.required],outletCode:['',Validators.required],outletName:['',Validators.required],outletAddress:['',Validators.required],terminalCount:[1,[Validators.min(0),Validators.max(999)]]});
  ngOnInit():void{this.load();}
  load():void{this.loading.set(true);this.service.myDossier().subscribe({next:d=>{this.loading.set(false);this.open(d);},error:()=>{this.loading.set(false);this.error.set('Dossier introuvable ou acces refuse.');}});}
  open(d:MerchantDossier):void{this.dossier.set(d);this.form.patchValue({legalName:d.legalName??'',tradingName:d.tradingName??'',registrationNumber:d.registrationNumber??'',country:d.country??'MA',mcc:d.mcc??'',settlementAccountReference:d.settlementAccountReference??'',settlementCurrency:d.settlementCurrency??'504',productId:d.productId??'',provisioningDestination:d.provisioningDestination??'',acceptanceChannel:d.acceptanceChannel??'TPE',outletCode:d.outletCode??'',outletName:d.outletName??'',outletAddress:d.outletAddress??'',terminalCount:d.terminalCount||1});this.reloadDocuments();}
  save():void{if(!this.dossier()||this.form.invalid)return;this.busy.set(true);const {provisioningDestination,...request}=this.form.getRawValue();const id=this.dossier()!.id;this.service.updateDossier(id,request as MerchantDossierUpdate).pipe(switchMap(()=>this.service.selectDestination(id,provisioningDestination as ProvisioningDestination))).subscribe({next:d=>{this.busy.set(false);this.message.set('Dossier enregistre.');this.open(d);},error:e=>this.fail(e)});}
  choose(type:MerchantDocumentType,event:Event):void{const file=(event.target as HTMLInputElement).files?.[0];if(file)this.selected[type]=file;}
  upload(type:MerchantDocumentType):void{const file=this.selected[type];if(!file||!this.dossier())return;this.busy.set(true);this.service.uploadDocument(this.dossier()!.id,type,file).subscribe({next:()=>{delete this.selected[type];this.busy.set(false);this.message.set('Piece KYC envoyee.');this.reloadDocuments();},error:e=>this.fail(e)});}
  submitKyc():void{if(!this.dossier())return;this.busy.set(true);this.service.submitKyc(this.dossier()!.id).subscribe({next:d=>{this.busy.set(false);this.message.set('KYC soumis au Back-office.');this.open(d);},error:e=>this.fail(e)});}
  submitMaker():void{if(!this.dossier())return;this.busy.set(true);this.service.submit(this.dossier()!.id).subscribe({next:()=>{this.busy.set(false);this.message.set('Dossier soumis au Checker.');this.load();},error:e=>this.fail(e)});}
  latest(type:MerchantDocumentType):MerchantDocument|undefined{return this.documents().find(d=>d.type===type);} canSubmitKyc(d:MerchantDossier):boolean{return d.status==='DRAFT'&&['NOT_STARTED','COMPLEMENTS_REQUIRED'].includes(d.kycStatus)&&this.requiredTypes.every(t=>!!this.latest(t));}
  label(type:MerchantDocumentType):string{return({LEGAL_EXISTENCE:'Existence legale',REPRESENTATIVE_IDENTITY:'Identite du representant',BANK_ACCOUNT_PROOF:'Justificatif bancaire'} as Record<string,string>)[type]??type;}
  reloadDocuments():void{if(this.dossier())this.service.documents(this.dossier()!.id).subscribe(d=>this.documents.set(d));} fail(e:any):void{this.busy.set(false);this.error.set(e?.error?.message??'Operation refusee.');}
}
