import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IonContent, IonHeader, IonTitle, IonToolbar } from '@ionic/angular/standalone';
import { Subject, takeUntil } from 'rxjs';
import { MerchantCommercialService } from '../../app/core/services/merchant-commercial.service';
import { MerchantOnboardingService } from '../../app/core/services/merchant-onboarding.service';
import { AllowedAcquirer } from '../../app/core/models/merchant-onboarding.models';
import { CommercialPhase } from '../../app/core/models/merchant-commercial.models';
import { MobileAuthService } from '../services/mobile-auth.service';
import { MobileMenuButtonComponent } from '../components/mobile-menu-button.component';
import { mobileLabel, statusTone } from '../services/mobile-presentation';
interface QueueItem { id:string;reference:string;legalName:string|null;phase:CommercialPhase;origin:string; }

@Component({standalone:true,imports:[FormsModule,IonContent,IonHeader,IonTitle,IonToolbar,MobileMenuButtonComponent],template:`
  <ion-header class="mobile-header"><ion-toolbar><app-mobile-menu-button slot="start" /><ion-title>Dossiers commerciaux</ion-title></ion-toolbar></ion-header>
  <ion-content class="mobile-page"><main class="mobile-container"><div class="section-heading"><div><p class="eyebrow">Suivi commercial</p><h1>Mes dossiers</h1></div><button class="primary-action compact" (click)="create()">＋ Nouveau</button></div>
    <section class="filter-panel" aria-label="Filtres des dossiers">
      <label>Acquéreur<select aria-label="Acquéreur" [ngModel]="selected()" (ngModelChange)="changeAcquirer($event)" [disabled]="acquirers().length<2 || loadingAcquirers()"> <option value="">Choisir un acquéreur</option>@for(a of acquirers();track a.id){<option [value]="a.code">{{a.name}}</option>}</select></label>
      <label>Étape commerciale<select aria-label="Étape commerciale" [ngModel]="phase()" (ngModelChange)="changePhase($event)"><option value="">Toutes les étapes</option>@for(p of phases;track p){<option [value]="p">{{label(p)}}</option>}</select></label>
    </section>
    @if (loading() || loadingAcquirers()) { <div class="mobile-state" role="status"><span class="loading-dot"></span> Chargement des dossiers…</div> }
    @if (error()) { <div class="mobile-notice danger" role="alert">{{error()}} <button class="text-action" (click)="retry()">Réessayer</button></div> }
    @if (!loading() && !loadingAcquirers() && !error() && selected()) {
      <div class="section-heading"><h2>{{total()}} dossier(s)</h2><button class="text-action" (click)="load()">Actualiser</button></div>
      <label class="search-field">Filtrer les dossiers de cette page<input type="search" placeholder="Référence ou nom du commerce" [ngModel]="search()" (ngModelChange)="search.set($event)" /></label>
      <p class="list-scope">Acquéreur et étape filtrés sur le serveur. Recherche limitée aux {{items().length}} dossiers de cette page.</p>
      <div class="dossier-list">@for(item of visible();track item.id){
        <button class="dossier-card" (click)="open(item.id)"><div class="card-topline"><span class="reference">{{item.reference}}</span><span class="status-pill" [attr.data-tone]="tone(item.phase)">{{label(item.phase)}}</span></div><strong>{{item.legalName || 'Commerce à compléter'}}</strong><div class="card-bottomline"><span>{{item.origin==='AUTO'?'Auto-affiliation':'Affiliation accompagnée'}}</span><span class="open-label">Ouvrir →</span></div></button>
      } @empty { <div class="mobile-state"><h3>{{items().length?'Aucun résultat sur cette page':'Aucun dossier à cette étape'}}</h3><p>Modifiez les filtres pour élargir la recherche.</p></div> }</div>
      <nav class="pagination" aria-label="Pagination des dossiers"><button (click)="previous()" [disabled]="offset()===0">← Précédent</button><span>Page {{page()}} sur {{pages()}}</span><button (click)="next()" [disabled]="offset()+limit>=total()">Suivant →</button></nav>
    }
    @if (!loadingAcquirers() && !error() && !selected()) { <div class="mobile-state"><h3>Choisissez un acquéreur</h3><p>Seuls vos acquéreurs autorisés sont proposés.</p></div> }
  </main></ion-content>`})
export class MobileCommercialDossiersPage implements OnDestroy {
  private readonly service=inject(MerchantCommercialService);private readonly onboarding=inject(MerchantOnboardingService);
  private readonly auth=inject(MobileAuthService);private readonly router=inject(Router);private readonly cancel=new Subject<void>();
  readonly acquirers=signal<AllowedAcquirer[]>([]);readonly selected=signal('');readonly phase=signal<CommercialPhase|''>('');readonly search=signal('');
  readonly items=signal<QueueItem[]>([]);readonly total=signal(0);readonly offset=signal(0);readonly limit=20;
  readonly loading=signal(false);readonly loadingAcquirers=signal(false);readonly error=signal('');readonly label=mobileLabel;readonly tone=statusTone;
  readonly phases:CommercialPhase[]=['DRAFT','AWAITING_CONFIRMATION','IN_REVIEW','TO_DISTRIBUTE','ASSIGNMENT_EXCEPTION','COMPLEMENTS_REQUIRED','REEXAMINATION','READY_FOR_BO','TRANSMITTED_BO'];
  readonly visible=computed(()=>{const term=this.search().trim().toLocaleLowerCase('fr');return this.items().filter(i=>[i.reference,i.legalName||''].some(v=>v.toLocaleLowerCase('fr').includes(term)));});
  readonly page=computed(()=>Math.floor(this.offset()/this.limit)+1);readonly pages=computed(()=>Math.max(1,Math.ceil(this.total()/this.limit)));
  ionViewWillEnter():void {this.initialize();} ionViewWillLeave():void {this.clear();} ngOnDestroy():void{this.clear();this.cancel.complete();}
  private clear():void {this.cancel.next();this.items.set([]);this.total.set(0);this.acquirers.set([]);this.selected.set('');this.phase.set('');this.search.set('');this.offset.set(0);this.error.set('');this.loading.set(false);this.loadingAcquirers.set(false);}
  private initialize():void {
    this.clear();this.loadingAcquirers.set(true);
    this.onboarding.allowedAcquirers().pipe(takeUntil(this.cancel)).subscribe({next:items=>{
      this.acquirers.set(items);this.loadingAcquirers.set(false);const current=this.auth.user()?.acquirerId;
      const active=items.find(i=>i.code===current||i.id===current)||(items.length===1?items[0]:null);
      if(active){this.selected.set(active.code);this.load();}
    },error:()=>{this.loadingAcquirers.set(false);this.error.set('Les acquéreurs autorisés sont indisponibles.');}});
  }
  load():void {
    this.cancel.next();this.items.set([]);this.total.set(0);this.search.set('');this.error.set('');
    if(!this.acquirers().some(a=>a.code===this.selected())){this.loading.set(false);return;}
    this.loading.set(true);this.service.queue(this.selected(),this.phase(),this.offset(),this.limit).pipe(takeUntil(this.cancel)).subscribe({
      next:page=>{this.items.set(page.items);this.total.set(page.total);this.loading.set(false);},error:()=>{this.loading.set(false);this.error.set('Les dossiers n’ont pas pu être chargés. Vérifiez vos accès ou réessayez.');}});
  }
  retry():void {if(this.acquirers().length)this.load();else this.initialize();}
  changeAcquirer(value:string):void {this.selected.set(this.acquirers().some(a=>a.code===value)?value:'');this.offset.set(0);this.load();}
  changePhase(value:CommercialPhase|''):void {this.phase.set(this.phases.includes(value as CommercialPhase)?value:'');this.offset.set(0);this.load();}
  previous():void{if(this.offset()>0){this.offset.update(v=>Math.max(0,v-this.limit));this.load();}}
  next():void{if(this.offset()+this.limit<this.total()){this.offset.update(v=>v+this.limit);this.load();}}
  open(id:string):void{void this.router.navigate(['/commercial/dossiers',id]);} create():void{void this.router.navigateByUrl('/commercial/prospect');}
}
