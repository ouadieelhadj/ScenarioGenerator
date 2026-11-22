import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar } from '@ionic/angular/standalone';
import { Observable, Subject, forkJoin, takeUntil } from 'rxjs';
import { MerchantDocument, MerchantDossier } from '../../app/core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';
import { MobileAuthService } from '../services/mobile-auth.service';
import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { latestDocuments,mobileLabel,requiredDocumentsAccepted,reviewWriteAccess,statusTone } from '../services/mobile-presentation';

@Component({standalone:true,imports:[DatePipe,FormsModule,IonBackButton,IonButtons,IonContent,IonHeader,IonTitle,IonToolbar,MobileMenuButtonComponent],template:`
  <ion-header class="mobile-header"><ion-toolbar><app-mobile-menu-button slot="start" />@if(detailId()){<ion-buttons slot="end"><ion-back-button defaultHref="/backoffice/dossiers" /></ion-buttons>}<ion-title>{{detailId()?'Contrôle du dossier':'Contrôles KYC'}}</ion-title></ion-toolbar></ion-header>
  <ion-content class="mobile-page"><main class="mobile-container"><p class="eyebrow">Back-office · {{auth.user()?.acquirerId || 'Accès autorisés'}}</p><h1>{{detailId()?'Revue des pièces':'Dossiers à contrôler'}}</h1>
    @if(loading()){<div class="mobile-state" role="status"><span class="loading-dot"></span> Chargement…</div>}
    @if(error()){<div class="mobile-notice danger" role="alert">{{error()}}<button class="text-action" (click)="load()" [disabled]="busy()">Actualiser</button></div>}
    @if(message()){<div class="mobile-notice success" role="status">{{message()}}</div>}
    @if(!detailId() && !loading() && !error()){
      <section class="filter-panel"><label>Rechercher un dossier<input type="search" placeholder="Référence ou commerce" [ngModel]="search()" (ngModelChange)="search.set($event)" /></label><label>Vérification des pièces<select [ngModel]="status()" (ngModelChange)="status.set($event)"><option value="">Tous les statuts</option><option value="PENDING_REVIEW">En contrôle</option><option value="COMPLEMENTS_REQUIRED">Compléments demandés</option><option value="VALIDATED">Validé</option><option value="REJECTED">Refusé</option><option value="NOT_STARTED">Non commencé</option></select></label></section>
      <div class="section-heading"><h2>{{filtered().length}} dossier(s) visible(s)</h2><button class="text-action" (click)="load()">Actualiser</button></div>
      <p class="list-scope">Filtrage de la file complète reçue · {{queue().length}} dossier(s) autorisé(s).</p>
      <div class="dossier-list">@for(item of filtered();track item.id){<button class="dossier-card" (click)="open(item.id)"><div class="card-topline"><span class="reference">{{item.reference}}</span><span class="status-pill" [attr.data-tone]="tone(item.kycStatus)">{{label(item.kycStatus)}}</span></div><strong>{{item.legalName || 'Commerce à compléter'}}</strong><div class="card-bottomline"><span>{{item.createdAt | date:'dd/MM/yyyy'}}</span><span class="open-label">Contrôler →</span></div></button>}@empty{<div class="mobile-state"><h3>Aucun dossier correspondant</h3><p>Vous ne voyez que les dossiers autorisés pour votre profil.</p></div>}</div>
    }
    @if(dossier();as item){
      <section class="mobile-surface"><div class="card-topline"><span class="reference">{{item.reference}}</span><span class="status-pill" [attr.data-tone]="tone(item.kycStatus)">{{label(item.kycStatus)}}</span></div><h2>{{item.legalName || 'Commerce à compléter'}}</h2><dl class="summary-lines"><div><dt>Nom commercial</dt><dd>{{item.tradingName || 'Non renseigné'}}</dd></div><div><dt>Activité</dt><dd>{{item.mcc || 'Non renseignée'}}</dd></div><div><dt>Création</dt><dd>{{item.createdAt | date:'dd/MM/yyyy HH:mm'}}</dd></div></dl></section>
      <div class="section-heading"><h2>Pièces justificatives</h2><span>{{latest().length}} type(s)</span></div><p class="list-scope">La dernière version de chaque type est présentée. Les contrôles et l’historique sont conservés sur le serveur.</p>
      @for(doc of latest();track doc.id){<section class="mobile-surface document-card"><div class="card-topline"><h3>{{label(doc.type)}}</h3><span class="status-pill" [attr.data-tone]="tone(doc.reviewStatus)">{{label(doc.reviewStatus)}}</span></div><p>Version {{doc.version}} · {{doc.contentType}} · {{doc.contentLength}} octets</p>
        @if(doc.rejectionReason){<div class="mobile-notice warning">{{doc.rejectionReason}}</div>}
        <div class="button-row"><button (click)="preview(doc)" [disabled]="busy()">Consulter la pièce</button>@if(canWrite() && item.kycStatus==='PENDING_REVIEW'){<button (click)="review(doc,true)" [disabled]="busy() || doc.reviewStatus==='ACCEPTED'">Accepter</button><button class="danger-action" (click)="review(doc,false)" [disabled]="busy() || !validReason() || doc.reviewStatus==='REJECTED'">Refuser</button>}</div></section>
      }@empty{<div class="mobile-state">Aucune pièce disponible. La validation reste impossible.</div>}
      @if(previewUrl();as preview){<section class="mobile-surface" aria-label="Aperçu de la pièce"><div class="section-heading"><h2>{{previewTitle()}}</h2><button (click)="closePreview()">Fermer l’aperçu</button></div>@if(previewImage()){<img class="document-image" [src]="preview" alt="Pièce justificative du dossier sélectionné" />}@else{<iframe class="document-pdf" [src]="pdfUrl()" title="Pièce PDF du dossier" sandbox></iframe><p class="list-scope">L’affichage PDF dépend des capacités du lecteur de votre appareil.</p>}</section>}
      @if(canWrite()){
        <section class="mobile-surface"><h2>Décision du contrôle</h2><p>Le déposant ne peut pas approuver son propre contrôle. Le serveur vérifie chaque décision.</p><label class="search-field">Motif du refus ou des compléments<textarea rows="3" maxlength="1000" [ngModel]="reason()" (ngModelChange)="reason.set($event)" placeholder="Précisez les éléments à corriger"></textarea></label>
          <div class="stacked-actions"><button class="primary-action" (click)="decide('VALIDATE')" [disabled]="busy() || !canValidate()">Valider le KYC</button><button (click)="decide('COMPLEMENTS')" [disabled]="busy() || !validReason() || item.kycStatus!=='PENDING_REVIEW'">Demander des compléments</button><button class="danger-action" (click)="decide('REJECT')" [disabled]="busy() || !validReason() || item.kycStatus!=='PENDING_REVIEW'">Refuser le KYC</button></div>
          @if(busy()){<p role="status">Traitement de votre demande…</p>}
        </section>
      }@else{<div class="mobile-notice info">Consultation uniquement : votre profil ne permet pas de modifier le contrôle KYC.</div>}
    }
  </main></ion-content>`})
export class MobileBackofficePage implements OnDestroy {
  readonly auth=inject(MobileAuthService);private readonly service=inject(MerchantOnboardingService);private readonly route=inject(ActivatedRoute);private readonly router=inject(Router);private readonly sanitizer=inject(DomSanitizer);
  private readonly cancel=new Subject<void>();readonly detailId=signal('');readonly queue=signal<MerchantDossier[]>([]);readonly dossier=signal<MerchantDossier|null>(null);readonly documents=signal<MerchantDocument[]>([]);
  readonly loading=signal(false);readonly busy=signal(false);readonly error=signal('');readonly message=signal('');readonly search=signal('');readonly status=signal('');readonly reason=signal('');readonly label=mobileLabel;readonly tone=statusTone;
  readonly previewUrl=signal<string|null>(null);readonly pdfUrl=signal<SafeResourceUrl|null>(null);readonly previewImage=signal(false);readonly previewTitle=signal('');
  readonly latest=computed(()=>latestDocuments(this.documents()));readonly filtered=computed(()=>{const q=this.search().trim().toLocaleLowerCase('fr');return this.queue().filter(d=>(!this.status()||d.kycStatus===this.status())&&[d.reference,d.legalName||'',d.tradingName||''].some(v=>v.toLocaleLowerCase('fr').includes(q)));});
  canWrite=()=>reviewWriteAccess(this.auth.user());validReason=()=>!!this.reason().trim();
  canValidate=()=>this.canWrite()&&this.dossier()?.kycStatus==='PENDING_REVIEW'&&this.dossier()?.kycSubmittedBy!==this.auth.user()?.login&&requiredDocumentsAccepted(this.documents());
  ionViewWillEnter():void{this.clear();this.detailId.set(this.route.snapshot.paramMap.get('id')||'');this.load();}ionViewWillLeave():void{this.clear();}ngOnDestroy():void{this.clear();this.cancel.complete();}
  private clear():void{this.cancel.next();this.closePreview();this.queue.set([]);this.dossier.set(null);this.documents.set([]);this.search.set('');this.status.set('');this.reason.set('');this.error.set('');this.message.set('');this.loading.set(false);this.busy.set(false);}
  load():void{
    this.cancel.next();this.closePreview();this.error.set('');this.loading.set(true);this.dossier.set(null);this.documents.set([]);this.queue.set([]);
    if(this.detailId())forkJoin({dossier:this.service.reviewDossier(this.detailId()),documents:this.service.reviewDocuments(this.detailId())}).pipe(takeUntil(this.cancel)).subscribe({next:result=>{this.dossier.set(result.dossier);this.documents.set(result.documents);this.loading.set(false);},error:err=>this.fail(err)});
    else this.service.reviewQueue().pipe(takeUntil(this.cancel)).subscribe({next:items=>{this.queue.set(items);this.loading.set(false);},error:err=>this.fail(err)});
  }
  open(id:string):void{void this.router.navigate(['/backoffice/dossiers',id]);}
  review(doc:MerchantDocument,accepted:boolean):void{
    if(this.busy()||!this.canWrite()||this.dossier()?.kycStatus!=='PENDING_REVIEW'||(!accepted&&!this.validReason()))return;
    this.mutate(this.service.reviewDocument(doc.id,accepted,accepted?null:this.reason().trim()),()=>{this.message.set('Contrôle de la pièce enregistré.');this.load();});
  }
  decide(action:'VALIDATE'|'COMPLEMENTS'|'REJECT'):void{
    const item=this.dossier();if(!item||this.busy()||!this.canWrite()||item.kycStatus!=='PENDING_REVIEW'||(action==='VALIDATE'?!this.canValidate():!this.validReason()))return;
    const request=action==='VALIDATE'?this.service.validateKyc(item.id):action==='COMPLEMENTS'?this.service.requestComplements(item.id,this.reason().trim()):this.service.rejectKyc(item.id,this.reason().trim());
    this.mutate(request,()=>{this.reason.set('');this.message.set(action==='VALIDATE'?'KYC validé. Le dossier poursuit le workflow existant.':action==='COMPLEMENTS'?'Demande de compléments enregistrée.':'Refus du KYC enregistré.');this.load();});
  }
  private mutate<T>(request:Observable<T>,success:()=>void):void{this.error.set('');this.message.set('');this.busy.set(true);request.pipe(takeUntil(this.cancel)).subscribe({next:()=>{this.busy.set(false);success();},error:err=>this.fail(err)});}
  private fail(err:{status?:number}):void{this.loading.set(false);this.busy.set(false);this.error.set(err.status===403?'Cette action ou ce dossier n’est pas autorisé pour votre profil.':err.status===409?'Le dossier a changé ou cette décision n’est plus possible. Actualisez avant de poursuivre.':'La demande n’a pas pu être confirmée. Actualisez l’état avant toute nouvelle action.');}
  preview(doc:MerchantDocument):void{
    if(this.busy())return;this.closePreview();this.busy.set(true);this.error.set('');
    this.service.reviewDocumentContent(doc.id).pipe(takeUntil(this.cancel)).subscribe({next:blob=>{
      this.busy.set(false);if(!['image/jpeg','image/png','image/webp','application/pdf'].includes(blob.type)){this.error.set('Ce format de pièce doit être consulté depuis le portail Web.');return;}
      const url=URL.createObjectURL(blob);this.previewUrl.set(url);this.previewImage.set(blob.type.startsWith('image/'));this.previewTitle.set(this.label(doc.type));
      if(blob.type==='application/pdf')this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
    },error:err=>this.fail(err)});
  }
  closePreview():void{const url=this.previewUrl();if(url)URL.revokeObjectURL(url);this.previewUrl.set(null);this.pdfUrl.set(null);this.previewTitle.set('');}
}
