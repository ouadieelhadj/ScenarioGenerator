import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  MerchantDocument, MerchantDocumentType, MerchantDossier, MerchantDossierV2,
  MerchantDossierV2Update, MerchantOutletV2, ProvisioningDestination,
} from '../../core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({
  selector: 'app-merchant-dossier',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    @if (loading()) { <div class="state"><i class="pi pi-spin pi-spinner"></i> Chargement du dossier...</div> }
    @else if (error()) { <div class="state error">{{ error() }}</div> }
    @else { @if (dossier(); as item) {
      <header><div><span class="eyebrow">{{ item.reference }}</span><h1>Dossier commercant et PDV</h1></div><div class="badges"><span>{{ item.status }}</span><span>KYC {{ item.kycStatus }}</span></div></header>
      @if (item.complementReason) { <div class="notice warning"><strong>Complements demandes</strong><p>{{ item.complementReason }}</p></div> }
      @if (message()) { <div class="notice success">{{ message() }}</div> }

      <form [formGroup]="form" (ngSubmit)="saveV2()">
        <section class="panel form-grid">
          <h2 class="wide">Profil juridique</h2>
          <label class="wide">Système de destination<select formControlName="provisioningDestination"><option value="" disabled>Choisir une destination</option><option value="FUTURPAYMENT">FuturPayment Acquiring</option><option value="WAY4">WAY4 Acquiring</option><option value="BOTH">FuturPayment + WAY4</option></select></label>
          <label>Type<select formControlName="merchantType"><option value="PP">Personne physique</option><option value="PM">Personne morale</option><option value="AE">Auto-entrepreneur</option><option value="ASSOCIATION_FOUNDATION">Association / Fondation</option></select></label>
          @if (form.controls.merchantType.value === 'ASSOCIATION_FOUNDATION') { <label>Nature<select formControlName="organizationLegalNature"><option value="ASSOCIATION">Association</option><option value="FOUNDATION">Fondation</option></select></label> }
          <label>Raison sociale<input formControlName="legalName" /></label>
          <label>Nom commercial<input formControlName="tradingName" /></label>
          <label>Immatriculation<input formControlName="registrationNumber" /></label>
          <label>Identifiant fiscal<input formControlName="taxIdentifier" /></label>
          <label>ICE<input formControlName="ice" /></label>
          <label>Forme juridique<input formControlName="legalForm" /></label>
          <label>Activite<input formControlName="businessActivity" /></label>
          <label>Objet association/fondation<input formControlName="associationPurpose" /></label>
          <label>Telephone principal<input formControlName="primaryPhone" /></label>
          <label>E-mail principal<input type="email" formControlName="primaryEmail" /></label>
          <label>MCC<input formControlName="mcc" maxlength="4" /></label>
          <label>RIB<input formControlName="rib" maxlength="24" /></label>
          <h3 class="wide">Siege</h3>
          <label class="wide">Adresse<input formControlName="headquartersLine1" /></label>
          <label>Ville<input formControlName="headquartersCity" /></label>
          <label>Pays ISO-2<input formControlName="headquartersCountry" maxlength="2" /></label>
          <h3 class="wide">Representant legal</h3>
          <label>Prenom<input formControlName="representativeFirstName" /></label>
          <label>Nom<input formControlName="representativeLastName" /></label>
          <label>Telephone<input formControlName="representativePhone" /></label>
          <label>E-mail<input type="email" formControlName="representativeEmail" /></label>
          <label>Type d'identite<input formControlName="representativeIdType" /></label>
          <label>Numero d'identite<input formControlName="representativeIdNumber" /></label>
          <label>Pays de residence<input formControlName="representativeResidenceCountry" maxlength="2" /></label>
          <label>Nationalite<input formControlName="representativeNationality" maxlength="2" /></label>
        </section>

        <section class="panel">
          <div class="section-title"><h2>Beneficiaires effectifs</h2><button type="button" (click)="addOwner()">Ajouter</button></div>
          @for (owner of owners(); track owner.id; let index = $index) {
            <div class="row-card three"><label>Prenom<input [value]="owner.firstName" (input)="updateOwner(index,'firstName',$any($event.target).value)" /></label><label>Nom<input [value]="owner.lastName" (input)="updateOwner(index,'lastName',$any($event.target).value)" /></label><button type="button" class="danger" (click)="removeOwner(index)">Retirer</button></div>
          }
        </section>

        <section class="panel">
          <div class="section-title"><h2>Points de vente</h2><button type="button" (click)="addOutlet()">Ajouter un PDV</button></div>
          @for (outlet of outlets(); track outlet.id; let oi = $index) {
            <article class="outlet-card">
              <div class="section-title"><h3>PDV {{ oi + 1 }}</h3><div><label class="check"><input type="radio" name="principal" [checked]="outlet.principal" (change)="makePrincipal(oi)" /> Principal</label><button type="button" class="danger" (click)="removeOutlet(oi)">Retirer</button></div></div>
              <div class="form-grid">
                <label>Code<input [value]="outlet.code" (input)="patchOutlet(oi,{code:$any($event.target).value})" /></label>
                <label>Nom<input [value]="outlet.name" (input)="patchOutlet(oi,{name:$any($event.target).value})" /></label>
                <label class="wide">Adresse<input [value]="outlet.address.line1" (input)="patchAddress(oi,'line1',$any($event.target).value)" /></label>
                <label>Ville<input [value]="outlet.address.city" (input)="patchAddress(oi,'city',$any($event.target).value)" /></label>
                <label>Pays<input maxlength="2" [value]="outlet.address.country" (input)="patchAddress(oi,'country',$any($event.target).value)" /></label>
                <label>Telephone contact<input [value]="outlet.contactPhone" (input)="patchOutlet(oi,{contactPhone:$any($event.target).value})" /></label>
                <label>E-mail contact<input type="email" [value]="outlet.contactEmail" (input)="patchOutlet(oi,{contactEmail:$any($event.target).value})" /></label>
                <label>Responsable - prenom<input [value]="outlet.responsible.firstName" (input)="patchResponsible(oi,'firstName',$any($event.target).value)" /></label>
                <label>Responsable - nom<input [value]="outlet.responsible.lastName" (input)="patchResponsible(oi,'lastName',$any($event.target).value)" /></label>
                <label>Responsable - telephone<input [value]="outlet.responsible.phone" (input)="patchResponsible(oi,'phone',$any($event.target).value)" /></label>
                <label>Responsable - e-mail<input type="email" [value]="outlet.responsible.email" (input)="patchResponsible(oi,'email',$any($event.target).value)" /></label>
              </div>

              <div class="subsection"><div class="section-title"><h4>Produits et tarification</h4><button type="button" (click)="addProduct(oi)">Ajouter</button></div>
                @for (product of outlet.products; track $index; let pi = $index) { <div class="row-card four"><label>Produit UUID<input [value]="product.productId" (input)="patchProduct(oi,pi,'productId',$any($event.target).value)" /></label><label>Pack<input [value]="product.pricingPackCode || ''" (input)="patchProduct(oi,pi,'pricingPackCode',$any($event.target).value)" /></label><label>Version<input type="number" min="1" [value]="product.pricingPackVersion || ''" (input)="patchProduct(oi,pi,'pricingPackVersion',numberValue($event))" /></label><button type="button" class="danger" (click)="removeProduct(oi,pi)">Retirer</button></div> }
              </div>

              <div class="subsection"><div class="section-title"><h4>Demandes TPE</h4><button type="button" (click)="addTerminal(oi)">Ajouter</button></div>
                @for (terminal of outlet.terminalRequests; track terminal.id; let ti = $index) { <div class="row-card five"><label>Produit UUID<input [value]="terminal.productId" (input)="patchTerminal(oi,ti,'productId',$any($event.target).value)" /></label><label>Quantite<input type="number" min="1" max="999" [value]="terminal.quantity" (input)="patchTerminal(oi,ti,'quantity',numberValue($event))" /></label><label>Modele<input [value]="terminal.modelCode" (input)="patchTerminal(oi,ti,'modelCode',$any($event.target).value)" /></label><label>Connectivite<input [value]="terminal.connectivityCode" (input)="patchTerminal(oi,ti,'connectivityCode',$any($event.target).value)" /></label><button type="button" class="danger" (click)="removeTerminal(oi,ti)">Retirer</button></div> }
              </div>

              <div class="subsection"><div class="section-title"><h4>Boutiques e-commerce</h4><button type="button" (click)="addStore(oi)">Ajouter</button></div>
                @for (store of outlet.ecommerceStores; track store.id; let si = $index) { <div class="store-grid"><label>Produit UUID<input [value]="store.productId" (input)="patchStore(oi,si,'productId',$any($event.target).value)" /></label><label>Code<input [value]="store.storeCode" (input)="patchStore(oi,si,'storeCode',$any($event.target).value)" /></label><label>Nom<input [value]="store.name" (input)="patchStore(oi,si,'name',$any($event.target).value)" /></label><label>Domaine<input [value]="store.allowedDomain" (input)="patchStore(oi,si,'allowedDomain',$any($event.target).value)" /></label><label class="wide">URL retour HTTPS<input [value]="store.returnUrl" (input)="patchStore(oi,si,'returnUrl',$any($event.target).value)" /></label><label class="wide">URL notification HTTPS<input [value]="store.notificationUrl" (input)="patchStore(oi,si,'notificationUrl',$any($event.target).value)" /></label><label>Devise<input maxlength="3" [value]="store.currency" (input)="patchStore(oi,si,'currency',$any($event.target).value)" /></label><label>Capture<select [value]="store.captureMode" (change)="patchStore(oi,si,'captureMode',$any($event.target).value)"><option value="IMMEDIATE">Immediate</option><option value="DEFERRED">Differee</option></select></label><button type="button" class="danger" (click)="removeStore(oi,si)">Retirer</button></div> }
              </div>
            </article>
          }
          <div class="actions"><button class="primary" type="submit" [disabled]="form.invalid || busy() || item.status !== 'DRAFT' || outlets().length === 0">Enregistrer le dossier complet</button></div>
        </section>
      </form>

      <section class="panel documents"><h2>Pieces KYC obligatoires</h2>
        @for (type of requiredTypes; track type) { <article><div><strong>{{ documentLabel(type) }}</strong><p>{{ latest(type)?.reviewStatus || 'NON DEPOSE' }}</p></div><input type="file" accept="application/pdf,image/jpeg,image/png" (change)="choose(type,$event)" [disabled]="item.status !== 'DRAFT'" /><button type="button" (click)="upload(type)" [disabled]="!selected[type] || busy() || item.status !== 'DRAFT'">Televerser</button></article> }
        <button type="button" class="primary" (click)="submitKyc()" [disabled]="!canSubmitKyc(item) || busy()">Soumettre les pieces au Back-office</button>
      </section>
      <section class="panel"><h2>Soumission Maker</h2><p>Disponible apres validation KYC. Un Checker distinct devra approuver.</p><button type="button" class="primary" (click)="submitMaker()" [disabled]="item.status !== 'DRAFT' || item.kycStatus !== 'VALIDATED' || busy()">Soumettre le dossier</button></section>
      @if (item.merchantAcceptorId) { <div class="notice success"><strong>Affiliation terminee</strong><p>MID : {{ item.merchantAcceptorId }}</p></div> }
    } }
  `,
  styles: [`
    header,.section-title{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}header{margin-bottom:20px}.eyebrow{font-size:12px;font-weight:700;color:var(--sg-color-primary)}h1{margin:6px 0}.badges{display:flex;gap:8px}.badges span{padding:6px 10px;border-radius:999px;background:var(--sg-bg-muted);font-size:12px}.panel{margin:18px 0;padding:20px;border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);background:var(--sg-bg-surface)}.form-grid,.store-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.wide{grid-column:1/-1}label{display:grid;gap:6px;font-weight:600;font-size:13px}.check{display:inline-flex;grid-auto-flow:column;align-items:center}input,select{padding:10px;border:1px solid var(--sg-border);border-radius:8px;background:var(--sg-bg-surface);color:var(--sg-text-primary)}button{padding:9px 13px;border:0;border-radius:8px;cursor:pointer}.primary{background:var(--sg-color-primary);color:#fff}.danger{color:#b42318;background:#fff0ee}.outlet-card{margin:16px 0;padding:16px;border:1px solid var(--sg-border);border-radius:12px}.subsection{margin-top:18px;padding-top:12px;border-top:1px solid var(--sg-border)}.row-card{display:grid;gap:10px;align-items:end;margin:10px 0}.row-card.three{grid-template-columns:1fr 1fr auto}.row-card.four{grid-template-columns:2fr 1fr 100px auto}.row-card.five{grid-template-columns:2fr 90px 1fr 1fr auto}.actions{display:flex;justify-content:flex-end;margin-top:18px}.documents article{display:grid;grid-template-columns:1fr minmax(220px,1fr) auto;align-items:center;gap:12px;padding:12px 0;border-bottom:1px solid var(--sg-border)}.notice,.state{margin:16px 0;padding:16px;border:1px solid var(--sg-border);border-radius:10px}.success{border-color:#16803c;color:#116b33}.warning{border-color:#d97706}.error{color:#b42318}@media(max-width:760px){.form-grid,.store-grid,.row-card.three,.row-card.four,.row-card.five,.documents article{grid-template-columns:1fr}.wide{grid-column:auto}}
  `],
})
export class MerchantDossierComponent implements OnInit {
  private readonly service = inject(MerchantOnboardingService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly loading = signal(true); readonly busy = signal(false);
  readonly error = signal<string | null>(null); readonly message = signal<string | null>(null);
  readonly dossier = signal<MerchantDossier | null>(null); readonly advanced = signal<MerchantDossierV2 | null>(null);
  readonly outlets = signal<MerchantOutletV2[]>([]);
  readonly owners = signal<Array<{id:string|null;firstName:string;lastName:string;active:boolean}>>([]);
  readonly documents = signal<MerchantDocument[]>([]);
  readonly requiredTypes: MerchantDocumentType[] = ['LEGAL_EXISTENCE','REPRESENTATIVE_IDENTITY','BANK_ACCOUNT_PROOF'];
  readonly selected: Partial<Record<MerchantDocumentType, File>> = {};
  readonly form = this.fb.nonNullable.group({
    provisioningDestination: this.fb.nonNullable.control<ProvisioningDestination | ''>('', Validators.required),
    merchantType: this.fb.nonNullable.control<'PP'|'PM'|'AE'|'ASSOCIATION_FOUNDATION'>('PM'), organizationLegalNature:[''],
    legalName:['',Validators.required], tradingName:['',Validators.required], registrationNumber:['',Validators.required],
    taxIdentifier:[''], ice:[''], legalForm:[''], businessActivity:[''], associationPurpose:[''],
    primaryPhone:['',Validators.required], primaryEmail:['',[Validators.required,Validators.email]], mcc:['',[Validators.required,Validators.pattern(/^\d{4}$/)]], rib:['',Validators.required],
    headquartersLine1:['',Validators.required], headquartersCity:['',Validators.required], headquartersCountry:['MA',[Validators.required,Validators.pattern(/^[A-Z]{2}$/)]],
    representativeFirstName:['',Validators.required], representativeLastName:['',Validators.required], representativePhone:['',Validators.required], representativeEmail:['',[Validators.required,Validators.email]],
    representativeIdType:['CIN',Validators.required], representativeIdNumber:['',Validators.required], representativeResidenceCountry:['MA',Validators.required], representativeNationality:['MA',Validators.required],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    const source = id ? this.service.dossier(id) : this.service.myDossier();
    source.pipe(finalize(() => this.loading.set(false))).subscribe({next:value=>this.open(value),error:()=>this.error.set('Dossier introuvable ou acces refuse.')});
  }
  private open(value: MerchantDossier): void {
    this.dossier.set(value); this.reloadDocuments();
    this.service.dossierV2(value.id).subscribe({next:v2=>this.openV2(v2),error:()=>this.error.set('Le contrat v2 du dossier est indisponible.')});
  }
  private openV2(value: MerchantDossierV2): void {
    this.advanced.set(value); this.outlets.set(structuredClone(value.outlets)); this.owners.set(structuredClone(value.beneficialOwners));
    this.form.patchValue({provisioningDestination:value.provisioningDestination,merchantType:value.merchantType,organizationLegalNature:value.organizationLegalNature??'',legalName:value.legalName,tradingName:value.tradingName,registrationNumber:value.registrationNumber,taxIdentifier:value.taxIdentifier??'',ice:value.ice??'',legalForm:value.legalForm??'',businessActivity:value.businessActivity??'',associationPurpose:value.associationPurpose??'',primaryPhone:value.primaryPhone,primaryEmail:value.primaryEmail,mcc:value.mcc,rib:value.rib,headquartersLine1:value.headquartersAddress.line1,headquartersCity:value.headquartersAddress.city,headquartersCountry:value.headquartersAddress.country,representativeFirstName:value.representative.firstName,representativeLastName:value.representative.lastName,representativePhone:value.representative.phone,representativeEmail:value.representative.email,representativeIdType:value.representative.idType,representativeIdNumber:value.representative.idNumber,representativeResidenceCountry:value.representative.residenceCountry,representativeNationality:value.representative.nationality});
  }
  saveV2(): void {
    const current=this.advanced(); if(!current||this.form.invalid)return; const raw=this.form.getRawValue();
    const request:MerchantDossierV2Update={version:current.version,provisioningDestination:raw.provisioningDestination as ProvisioningDestination,merchantType:raw.merchantType,organizationLegalNature:raw.merchantType==='ASSOCIATION_FOUNDATION'?(raw.organizationLegalNature as 'ASSOCIATION'|'FOUNDATION'):null,legalName:raw.legalName,tradingName:raw.tradingName,registrationNumber:raw.registrationNumber,taxIdentifier:raw.taxIdentifier||null,ice:raw.ice||null,legalForm:raw.legalForm||null,businessActivity:raw.businessActivity||null,associationPurpose:raw.associationPurpose||null,primaryPhone:raw.primaryPhone,primaryEmail:raw.primaryEmail,mcc:raw.mcc,rib:raw.rib,headquartersAddress:{...current.headquartersAddress,line1:raw.headquartersLine1,city:raw.headquartersCity,country:raw.headquartersCountry},representative:{...current.representative,firstName:raw.representativeFirstName,lastName:raw.representativeLastName,phone:raw.representativePhone,email:raw.representativeEmail,idType:raw.representativeIdType,idNumber:raw.representativeIdNumber,residenceCountry:raw.representativeResidenceCountry,nationality:raw.representativeNationality},beneficialOwners:this.owners(),outlets:this.outlets()};
    this.busy.set(true); this.service.updateDossierV2(current.id,request).subscribe({next:value=>{this.busy.set(false);this.openV2(value);this.message.set('Dossier juridique, PDV, TPE et e-commerce enregistres.');},error:e=>this.fail(e)});
  }
  addOwner():void{this.owners.update(v=>[...v,{id:crypto.randomUUID(),firstName:'',lastName:'',active:true}]);}
  updateOwner(i:number,k:'firstName'|'lastName',v:string):void{this.owners.update(a=>a.map((x,n)=>n===i?{...x,[k]:v}:x));}
  removeOwner(i:number):void{this.owners.update(a=>a.filter((_,n)=>n!==i));}
  addOutlet():void{const principal=this.outlets().length===0;this.outlets.update(v=>[...v,this.emptyOutlet(principal)]);}
  removeOutlet(i:number):void{this.outlets.update(v=>v.filter((_,n)=>n!==i));if(this.outlets().length&&!this.outlets().some(x=>x.principal))this.makePrincipal(0);}
  makePrincipal(i:number):void{this.outlets.update(v=>v.map((x,n)=>({...x,principal:n===i})));}
  patchOutlet(i:number,p:Partial<MerchantOutletV2>):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,...p}:x));}
  patchAddress(i:number,k:keyof MerchantOutletV2['address'],value:string):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,address:{...x.address,[k]:value}}:x));}
  patchResponsible(i:number,k:keyof MerchantOutletV2['responsible'],value:string):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,responsible:{...x.responsible,[k]:value}}:x));}
  addProduct(i:number):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,products:[...x.products,{productId:'',pricingPackCode:null,pricingPackVersion:null,pricingSnapshotJson:null}]}:x));}
  patchProduct(i:number,j:number,k:string,value:any):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,products:x.products.map((p,m)=>m===j?{...p,[k]:value||null}:p)}:x));}
  removeProduct(i:number,j:number):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,products:x.products.filter((_,m)=>m!==j)}:x));}
  addTerminal(i:number):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,terminalRequests:[...x.terminalRequests,{id:crypto.randomUUID(),productId:'',quantity:1,modelCode:'STANDARD',connectivityCode:'4G',optionCodes:[],externalReference:null}]}:x));}
  patchTerminal(i:number,j:number,k:string,value:any):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,terminalRequests:x.terminalRequests.map((p,m)=>m===j?{...p,[k]:value}:p)}:x));}
  removeTerminal(i:number,j:number):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,terminalRequests:x.terminalRequests.filter((_,m)=>m!==j)}:x));}
  addStore(i:number):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,ecommerceStores:[...x.ecommerceStores,{id:crypto.randomUUID(),productId:'',storeCode:'',name:'',allowedDomain:'',returnUrl:'https://',notificationUrl:'https://',currency:'504',captureMode:'IMMEDIATE',optionCodes:[],externalReference:null}]}:x));}
  patchStore(i:number,j:number,k:string,value:any):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,ecommerceStores:x.ecommerceStores.map((p,m)=>m===j?{...p,[k]:value}:p)}:x));}
  removeStore(i:number,j:number):void{this.outlets.update(v=>v.map((x,n)=>n===i?{...x,ecommerceStores:x.ecommerceStores.filter((_,m)=>m!==j)}:x));}
  numberValue(event:Event):number{return Number(((event.target as HTMLInputElement).value)||0);}
  private emptyOutlet(principal:boolean):MerchantOutletV2{return{id:crypto.randomUUID(),code:'',name:'',principal,active:true,address:{line1:'',line2:null,district:null,city:'',region:null,postalCode:null,country:'MA'},contactPhone:'',contactEmail:'',responsible:{title:null,firstName:'',lastName:'',birthDate:null,phone:'',email:'',idType:'CIN',idNumber:'',residenceCountry:'MA',nationality:'MA'},products:[],terminalRequests:[],ecommerceStores:[]};}
  choose(type:MerchantDocumentType,event:Event):void{const file=(event.target as HTMLInputElement).files?.[0];if(file)this.selected[type]=file;}
  upload(type:MerchantDocumentType):void{const file=this.selected[type];if(!file||!this.dossier())return;this.busy.set(true);this.service.uploadDocument(this.dossier()!.id,type,file).subscribe({next:()=>{delete this.selected[type];this.busy.set(false);this.message.set('Piece televersee et empreinte calculee par le serveur.');this.reloadDocuments();},error:e=>this.fail(e)});}
  submitKyc():void{if(this.dossier())this.run(this.service.submitKyc(this.dossier()!.id),'KYC soumis au Back-office.');}
  submitMaker():void{if(!this.dossier())return;this.busy.set(true);this.service.submit(this.dossier()!.id).subscribe({next:()=>{this.busy.set(false);this.message.set('Dossier soumis au Checker.');this.refresh();},error:e=>this.fail(e)});}
  latest(type:MerchantDocumentType):MerchantDocument|undefined{return this.documents().find(d=>d.type===type);}
  canSubmitKyc(d:MerchantDossier):boolean{return d.status==='DRAFT'&&['NOT_STARTED','COMPLEMENTS_REQUIRED'].includes(d.kycStatus)&&this.requiredTypes.every(t=>!!this.latest(t));}
  documentLabel(type:MerchantDocumentType):string{return({LEGAL_EXISTENCE:'Existence legale',REPRESENTATIVE_IDENTITY:'Identite du representant',BANK_ACCOUNT_PROOF:'Justificatif bancaire'} as Record<string,string>)[type]??type;}
  private reloadDocuments():void{if(this.dossier())this.service.documents(this.dossier()!.id).subscribe({next:v=>this.documents.set(v),error:()=>this.documents.set([])});}
  private refresh():void{if(this.dossier())this.service.dossier(this.dossier()!.id).subscribe(v=>this.open(v));}
  private run(source:ReturnType<MerchantOnboardingService['submitKyc']>,message:string):void{this.busy.set(true);this.error.set(null);source.subscribe({next:value=>{this.busy.set(false);this.message.set(message);this.open(value);},error:e=>this.fail(e)});}
  private fail(error:any):void{this.busy.set(false);this.error.set(error?.error?.error??error?.error?.message??'Operation refusee par le serveur.');}
}
