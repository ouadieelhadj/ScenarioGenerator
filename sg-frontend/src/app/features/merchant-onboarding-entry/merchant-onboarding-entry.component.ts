import { Component, Input, inject, signal, OnInit,OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize,map,Subscription } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { ActivatedRoute,Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector:'app-merchant-onboarding-entry',standalone:true,imports:[RouterLink,FormsModule],
  template:`<main class="entry"><section><p class="brand">FuturPayment · Affiliation commerçant</p>
    <h1>Préparer et suivre votre affiliation</h1>
    <p>Votre dossier regroupe les informations de votre entreprise, vos points de vente,
      vos demandes de terminaux et les justificatifs nécessaires à son examen.</p>
    <h2>Commencer une affiliation</h2>
    @if(loading()){<p>Chargement des acquéreurs disponibles…</p>}
    @else if(received()){<p class="notice" role="status">Votre demande a été reçue. Si elle est recevable, un lien personnel vous permettra de vérifier votre adresse e-mail et d’activer votre compte. Ce message ne confirme pas encore l’envoi du courriel.</p>}
    @else if(acquirers().length){<form #registration="ngForm" (ngSubmit)="register()">
      @if(dedicated()){
        <p class="notice" role="note">Votre affiliation concerne <strong>{{acquirers()[0].name}}</strong> ({{entryCode()}}). Ce contexte sera vérifié par le serveur.</p>
      }@else{
        <label>Acquéreur de votre affiliation<select name="acquirerCode" [(ngModel)]="acquirerCode" required><option value="" disabled>Choisir un acquéreur</option>@for(a of acquirers();track a.code){<option [value]="a.code">{{a.name}}</option>}</select></label>
      }
      <p>Votre identifiant personnel FP-U sera attribué par FuturPayment. Conservez votre compte existant si vous en possédez déjà un : une adresse e-mail identique ne fusionne pas les comptes.</p>
      <label>Adresse e-mail à vérifier<input name="email" type="email" [(ngModel)]="email" required email maxlength="100" autocomplete="email" /></label>
      <label class="consent"><input name="consent" type="checkbox" [(ngModel)]="consent" required /> J’accepte que ces informations soient utilisées pour ouvrir et suivre mon dossier d’affiliation.</label>
      <button class="primary" type="submit" [disabled]="registration.invalid||busy()">{{busy()?'Enregistrement…':'Demander mon accès sécurisé'}}</button>
    </form>}
    @else{<p>La création publique n’est pas ouverte actuellement. Vous pouvez reprendre un dossier existant ou utiliser l’invitation reçue de votre acquéreur.</p>}
    @if(error()){<p class="notice" role="alert">{{error()}}</p>}
    <h2>Reprendre un dossier existant</h2>
    <p>Connectez-vous avec votre compte commerçant pour consulter ou compléter votre propre dossier.
      Si vous avez reçu une invitation, activez d’abord votre compte depuis son lien sécurisé.</p>
    <a class="primary" [routerLink]="resumePath">Accéder à mon dossier d’affiliation</a>
    <h2>Les étapes de votre dossier</h2><ol><li>Activation du compte et connexion sécurisée.</li>
      <li>Saisie des informations de l’entreprise et des points de vente.</li>
      <li>Confirmation du dossier et examen commercial avant sa transmission au Back-office.</li>
      <li>Suivi de la validation ou des compléments demandés.</li></ol>
    <p class="notice">Cette page publique n’affiche aucune donnée d’un commerçant. L’accès au dossier exige une authentification et les droits correspondants.</p>
  </section></main>`,
  styles:[`.entry{min-height:100vh;padding:32px;display:grid;place-items:center;background:var(--sg-bg-page)}section{width:min(780px,100%);padding:32px;border:1px solid var(--sg-border);border-radius:16px;background:var(--sg-bg-surface)}.brand{font-weight:700;color:var(--sg-color-primary)}h1{font-size:30px}h2{margin-top:28px;font-size:20px}p,li{line-height:1.7}li{margin-bottom:8px}.primary{display:inline-block;background:var(--sg-color-primary);color:white;padding:12px 20px;border:0;border-radius:8px;text-decoration:none;cursor:pointer}.primary:disabled{opacity:.6;cursor:default}.notice{border-left:4px solid var(--sg-color-primary);padding-left:16px;color:var(--sg-text-primary,#213547)}.notice[role=alert]{color:#b42318;border-left-color:#b42318}form{display:grid;gap:16px}label{display:grid;gap:8px}input,select{padding:10px;border:1px solid var(--sg-border);border-radius:8px;background:var(--sg-bg-surface);color:var(--sg-text-primary)}.consent{display:flex;align-items:flex-start}`]
})
export class MerchantOnboardingEntryComponent implements OnInit,OnDestroy {
  @Input() resumePath='/onboarding/dossier';
  private readonly service=inject(MerchantOnboardingService);
  private readonly route=inject(ActivatedRoute);
  readonly acquirers=signal<Array<{code:string;name:string}>>([]);readonly loading=signal(true);readonly busy=signal(false);readonly received=signal(false);readonly error=signal<string|null>(null);
  readonly dedicated=signal(false);readonly entryCode=signal<string|undefined>(undefined);
  private routeSubscription?:Subscription;private lookupSubscription?:Subscription;private requestSubscription?:Subscription;
  private revision=0;
  email='';acquirerCode='';consent=false;private id=crypto.randomUUID();private lastContent='';
  ngOnInit():void{this.routeSubscription=this.route.paramMap.subscribe(params=>this.loadContext(params.get('code')));}
  private loadContext(code:string|null):void{
    this.lookupSubscription?.unsubscribe();this.requestSubscription?.unsubscribe();
    const revision=++this.revision;this.email='';this.consent=false;this.acquirerCode='';this.lastContent='';this.id=crypto.randomUUID();
    this.received.set(false);this.busy.set(false);this.error.set(null);this.acquirers.set([]);this.dedicated.set(code!==null);this.entryCode.set(code??undefined);
    if(code!==null&&!/^[A-Z][A-Z0-9_-]{2,63}$/.test(code)){this.loading.set(false);this.error.set('Cette entrée d’affiliation est indisponible. Aucune demande ne peut être enregistrée.');return;}
    this.loading.set(true);
    const lookup=code===null?this.service.publicAcquirers():this.service.publicAcquirer(code).pipe(map(value=>[value]));
    this.lookupSubscription=lookup.pipe(finalize(()=>{if(revision===this.revision)this.loading.set(false);})).subscribe({
      next:values=>{
        if(revision!==this.revision)return;
        if(!Array.isArray(values)||values.some(a=>!a||typeof a.code!=='string'||!/^[A-Z][A-Z0-9_-]{2,63}$/.test(a.code)||typeof a.name!=='string'||!a.name.trim())
          ||new Set(values.map(a=>a.code)).size!==values.length||(code!==null&&(values.length!==1||values[0].code!==code))){
          this.error.set('Le contexte d’affiliation n’a pas pu être confirmé. Aucune demande ne peut être enregistrée.');return;
        }
        this.acquirers.set(values);if(code!==null)this.acquirerCode=code;
      },
      error:failure=>{if(revision===this.revision)this.error.set(code!==null&&failure.status===404?'Cette entrée d’affiliation est indisponible. Aucune demande ne peut être enregistrée.':'Le service d’affiliation est momentanément indisponible.');}
    });
  }
  ngOnDestroy():void{++this.revision;this.routeSubscription?.unsubscribe();this.lookupSubscription?.unsubscribe();this.requestSubscription?.unsubscribe();this.email='';this.consent=false;this.lastContent='';}
  register():void{if(this.loading()||this.busy()||this.received()||!this.consent||!this.acquirerCode||!this.email)return;
    const code=this.entryCode()??this.acquirerCode;if(!this.acquirers().some(a=>a.code===code)||this.acquirerCode!==code)return;
    const revision=this.revision,request={email:this.email.trim(),acquirerCode:code,consent:this.consent};
    const content=JSON.stringify(request);if(this.lastContent&&this.lastContent!==content)this.id=crypto.randomUUID();this.lastContent=content;
    this.busy.set(true);this.error.set(null);this.requestSubscription=this.service.registerPublic(this.id,request,this.entryCode()).pipe(finalize(()=>{if(revision===this.revision)this.busy.set(false);})).subscribe({
      next:v=>{if(revision!==this.revision)return;if(v.status!=='REQUEST_RECEIVED'){this.error.set('La demande n’a pas été confirmée. Vous pouvez réessayer.');return;}this.received.set(true);this.email='';this.lastContent='';},
      error:e=>{if(revision===this.revision)this.error.set(e.status===429?'Trop de demandes ont été reçues. Réessayez ultérieurement.':'La demande n’a pas été confirmée. Vous pouvez réessayer sans recréer le dossier.');}});
  }
}

@Component({
  selector:'app-merchant-onboarding-layout',standalone:true,imports:[RouterLink,RouterOutlet],
  template:`<header><a routerLink="/onboarding">FuturPayment · Affiliation commerçant</a><span>{{auth.user()?.login}}</span><button type="button" (click)="logout()">Se déconnecter du dossier</button></header>
    <main><p class="notice">Espace d’affiliation sécurisé : votre dossier uniquement. Les consultations financières et l’administration restent dans leurs espaces distincts.</p><router-outlet /></main>`,
  styles:[`header{display:flex;gap:20px;align-items:center;padding:18px 28px;background:var(--sg-bg-surface);border-bottom:1px solid var(--sg-border)}header a{font-weight:700;color:var(--sg-color-primary);text-decoration:none}header span{margin-left:auto}button{padding:10px;border:1px solid var(--sg-border);border-radius:8px;background:var(--sg-bg-surface);cursor:pointer}main{max-width:1200px;margin:0 auto;padding:24px}.notice{padding:12px;border-left:4px solid var(--sg-color-primary);background:var(--sg-bg-muted)}@media(max-width:600px){header{flex-wrap:wrap}main{padding:12px}}`]
})
export class MerchantOnboardingLayoutComponent {
  readonly auth=inject(AuthService);private readonly router=inject(Router);
  logout():void{this.auth.logout();this.router.navigateByUrl('/onboarding');}
}
