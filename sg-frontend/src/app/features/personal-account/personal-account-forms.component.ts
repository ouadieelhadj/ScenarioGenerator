import { Component,Input,Output,EventEmitter,OnInit,OnDestroy,inject,signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink,ActivatedRoute } from '@angular/router';
import { Subscription,fromEvent } from 'rxjs';
import { PersonalAccountActionsService } from '../../core/services/personal-account-actions.service';
import { PersonalAccountHandoffService } from '../../core/auth/personal-account-handoff.service';
import { PersonalAccountIntent } from '../../core/auth/protected-personal-account-link';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({selector:'app-personal-account-form',standalone:true,imports:[FormsModule,RouterLink],template:`
 <main class="account-page"><section class="account-card">
  <p class="eyebrow">Compte FuturPayment</p>
  <h1>{{ done()?'Opération confirmée':flow==='EMAIL_CHANGE'?'Vérifier ma nouvelle adresse':hasLink()?'Choisir un nouveau mot de passe':'Récupérer mon compte' }}</h1>
  @if(done()){
   <p role="status" class="success">Votre identifiant personnel, vos dossiers et vos droits sont conservés. Les anciennes sessions ne sont plus utilisables.</p>
   <p>Reconnectez-vous avec votre même identifiant : <strong>{{ personalId() }}</strong>.</p>
   <a class="primary" [routerLink]="loginPath">Revenir à la connexion</a>
  }@else if(sent()){
   <p role="status" class="success">Si les informations correspondent à un compte actif, un lien personnel sera envoyé à son adresse enregistrée.</p>
   <p>Ce message ne confirme pas encore l'envoi ni la réception de l'e-mail.</p><a [routerLink]="loginPath">Revenir à la connexion</a>
  }@else if(hasLink()){
   <p>{{ flow==='EMAIL_CHANGE'?'Cette confirmation vérifie la nouvelle adresse sans créer un autre compte.':'Définissez votre mot de passe pour votre compte existant.' }}</p>
   <form (ngSubmit)="complete()">
    @if(flow==='PASSWORD_RECOVERY'){
     <label>Nouveau mot de passe<input name="password" type="password" [(ngModel)]="password" autocomplete="new-password" required /></label>
     <label>Confirmer le mot de passe<input name="confirmation" type="password" [(ngModel)]="confirmation" autocomplete="new-password" required /></label>
     <small>Au moins 12 caractères : majuscule, minuscule, chiffre et caractère spécial.</small>
    }
    @if(error()){<p role="alert" class="error">{{ error() }}</p>}
    <button type="submit" [disabled]="loading()">{{ loading()?'Vérification…':flow==='EMAIL_CHANGE'?'Confirmer cette adresse':'Enregistrer mon nouveau mot de passe' }}</button>
    <a [routerLink]="loginPath">Annuler et revenir à la connexion</a>
   </form>
  }@else if(flow==='EMAIL_CHANGE'){
   <p role="alert" class="error">Le lien est absent ou invalide. Reconnectez-vous pour demander un nouveau lien.</p><a [routerLink]="loginPath">Revenir à la connexion</a>
  }@else{
   <p>Indiquez votre identifiant, votre établissement et l'adresse enregistrée sur votre compte. Ce parcours concerne les commerçants, les équipes et les administrateurs.</p>
   <form (ngSubmit)="recover()">
    <label>Établissement<select name="acquirer" [(ngModel)]="acquirer" required><option value="">Choisir un établissement</option>@for(bank of banks();track bank.code){<option [value]="bank.code">{{ bank.name }} ({{ bank.code }})</option>}</select></label>
    <label>Identifiant<input name="personalId" [(ngModel)]="identifier" autocomplete="username" placeholder="Identifiant de connexion" required /></label>
    <label>Adresse e-mail enregistrée<input name="email" type="email" [(ngModel)]="email" autocomplete="email" required /></label>
    @if(error()){<p role="alert" class="error">{{ error() }}</p>}
    <button type="submit" [disabled]="loading()||!banks().length">{{ loading()?'Enregistrement…':'Demander mon lien personnel' }}</button>
    <a [routerLink]="loginPath">Revenir à la connexion</a>
   </form>
  }
 </section></main>`,styles:[`
 .account-page{padding:24px;display:grid;place-items:center;min-height:75vh;background:#f4f7fb;color:#172b4d}.account-card{width:min(480px,100%);padding:28px;border:1px solid #cdd5df;background:#fff;border-radius:16px;box-sizing:border-box}.eyebrow{color:#354a65;font-size:13px}h1{font-size:26px;line-height:1.25}p,small{line-height:1.5}form,label{display:grid;gap:8px}form{gap:18px}input,select{background:#fff;color:#172b4d;border:1px solid #7b8ba3;border-radius:8px;padding:12px;width:100%;box-sizing:border-box}button,.primary{background:#164fa3;color:#fff;border:0;padding:13px;border-radius:8px;cursor:pointer;text-align:center;display:block;text-decoration:none}button:disabled{opacity:.6}a{color:#164fa3}.error{padding:12px;color:#9f1d20;background:#fff0f0;border-radius:8px}.success{padding:12px;color:#065f46;background:#eafaf0;border-radius:8px}
 `]})
export class PersonalAccountFormComponent implements OnInit,OnDestroy {
 @Input() flow:'PASSWORD_RECOVERY'|'EMAIL_CHANGE'='PASSWORD_RECOVERY';@Input() loginPath='/login';@Output() confirmed=new EventEmitter<void>();
 private readonly service=inject(PersonalAccountActionsService);private readonly onboarding=inject(MerchantOnboardingService);private readonly handoff=inject(PersonalAccountHandoffService);
 private readonly route=inject(ActivatedRoute);private readonly subscriptions=new Subscription();private intent:PersonalAccountIntent|null=null;private revision=0;private requestId:string|null=null;
 readonly banks=signal<Array<{code:string;name:string}>>([]);readonly hasLink=signal(false);readonly loading=signal(false);readonly done=signal(false);readonly sent=signal(false);readonly error=signal('');readonly personalId=signal('');
 identifier='';email='';acquirer='';password='';confirmation='';
 ngOnInit():void{this.subscriptions.add(this.handoff.changes.subscribe(()=>this.load()));this.subscriptions.add(this.route.fragment.subscribe(()=>this.captureCurrent()));this.subscriptions.add(fromEvent(window,'hashchange').subscribe(()=>this.captureCurrent()));this.captureCurrent();this.load();
  if(this.flow==='PASSWORD_RECOVERY'&&!this.hasLink())this.subscriptions.add(this.onboarding.publicAcquirers().subscribe({next:values=>this.banks.set(values),error:()=>this.error.set('La liste des établissements est indisponible. Réessayez plus tard.')}));}
 private captureCurrent():void{if(window.location.pathname!==(this.flow==='PASSWORD_RECOVERY'?'/account/recovery':'/account/verify-email'))return;
  const accepted=this.handoff.capture(window.location.pathname+window.location.search+window.location.hash);if(accepted===false){this.leave();this.error.set('Le lien est invalide. Demandez un nouveau lien personnel.');}else this.load();}
 private load():void{const pending=this.handoff.take(this.flow);if(!pending)return;this.leave();this.intent=pending;this.hasLink.set(true);}
 recover():void{if(this.loading()||this.sent()||this.hasLink())return;this.error.set('');
  if(!/^[A-Za-z0-9][A-Za-z0-9._@+-]{1,49}$/.test(this.identifier)||!this.email||!this.banks().some(bank=>bank.code===this.acquirer)){this.error.set('Vérifiez votre établissement, votre identifiant de connexion et votre adresse e-mail.');return;}
  this.requestId??=crypto.randomUUID();this.loading.set(true);const revision=this.revision;
  this.subscriptions.add(this.service.recovery(this.requestId,{acquirerCode:this.acquirer,personalId:this.identifier,email:this.email}).subscribe({next:()=>{if(revision!==this.revision)return;this.loading.set(false);this.sent.set(true);},error:failure=>{if(revision!==this.revision)return;this.loading.set(false);this.error.set(failure.status===429?'Trop de demandes. Réessayez ultérieurement.':'La demande est indisponible. Réessayez avec la même référence.');}}));
 }
 complete():void{if(this.loading()||this.done()||!this.intent)return;this.error.set('');
  if(this.flow==='PASSWORD_RECOVERY'&&(this.password!==this.confirmation||!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/.test(this.password)||new TextEncoder().encode(this.password).length>72)){this.error.set('Vérifiez la politique et la confirmation du mot de passe.');return;}
  const revision=this.revision,request=this.intent;this.loading.set(true);
  const pending=this.service.complete(request.requestId,request.token(),this.flow==='PASSWORD_RECOVERY'?this.password:undefined);this.password='';this.confirmation='';
  this.subscriptions.add(pending.subscribe({next:reply=>{if(revision!==this.revision)return;this.loading.set(false);
    if(reply.status!=='CONFIRMED'||!(/^[A-Za-z0-9][A-Za-z0-9._@+-]{1,95}$/.test(reply.personalId))){this.error.set('La confirmation est à vérifier. Réessayez sans créer un autre compte.');return;}
    this.personalId.set(reply.personalId);this.intent?.clear();this.intent=null;this.hasLink.set(false);this.done.set(true);this.confirmed.emit();
   },error:failure=>{if(revision!==this.revision)return;this.loading.set(false);this.error.set(failure.status===400?'Le mot de passe ou la demande ne respecte pas les règles.':failure.status===409?'Lien expiré, utilisé ou résultat à vérifier. Réessayez pour vérifier, sinon demandez un nouveau lien.':'Le service est indisponible. Réessayez pour vérifier le résultat.');}}));
 }
 leave():void{++this.revision;this.intent?.clear();this.intent=null;this.password='';this.confirmation='';this.identifier='';this.email='';this.acquirer='';this.requestId=null;this.personalId.set('');this.error.set('');this.done.set(false);this.sent.set(false);this.hasLink.set(false);this.loading.set(false);}
 ngOnDestroy():void{this.leave();this.subscriptions.unsubscribe();}
}

@Component({selector:'app-personal-email-settings',standalone:true,imports:[FormsModule],template:`
 <section class="settings"><h1>Mon adresse personnelle</h1><p>Votre identifiant, vos dossiers et vos droits ne changent pas. La nouvelle adresse doit être vérifiée avant son utilisation.</p>
 @if(sent()){<p role="status">Demande enregistrée. Vérifiez la nouvelle adresse depuis l'e-mail personnel. L'envoi et la réception ne sont pas encore confirmés.</p>}
 <form (ngSubmit)="submit()"><label>Nouvelle adresse e-mail<input name="email" type="email" [(ngModel)]="email" required /></label><label>Mot de passe actuel<input name="currentPassword" type="password" [(ngModel)]="password" autocomplete="current-password" required /></label>
 @if(error()){<p role="alert">{{ error() }}</p>}<button type="submit" [disabled]="loading()||sent()">{{ loading()?'Enregistrement…':'Demander la vérification de cette adresse' }}</button></form></section>`,styles:[`.settings{padding:24px;max-width:640px;background:#fff;color:#172b4d}form,label{display:grid;gap:8px}form{gap:18px}input{padding:12px;border:1px solid #7b8ba3;border-radius:8px;background:#fff;color:#172b4d}button{padding:13px;border:0;border-radius:8px;background:#164fa3;color:#fff}p{line-height:1.5}[role=alert]{color:#9f1d20}`]})
export class PersonalEmailSettingsComponent implements OnDestroy {
 private readonly service=inject(PersonalAccountActionsService);private pending=new Subscription();private revision=0;private requestId:string|null=null;
 email='';password='';readonly loading=signal(false);readonly sent=signal(false);readonly error=signal('');
 submit():void{if(this.loading()||this.sent()||!this.email||!this.password)return;this.loading.set(true);this.error.set('');this.requestId??=crypto.randomUUID();const revision=this.revision;
  const request=this.service.changeEmail(this.requestId,this.email,this.password);this.password='';
  this.pending.add(request.subscribe({next:()=>{if(revision!==this.revision)return;this.loading.set(false);this.sent.set(true);},error:failure=>{if(revision!==this.revision)return;this.loading.set(false);if([400,403,409].includes(failure.status))this.requestId=null;this.error.set(failure.status===403?'Le mot de passe ou vos droits ne permettent pas cette demande.':'La demande est à vérifier. Réessayez sans créer un autre compte.');}}));
 }
 leave():void{++this.revision;this.password='';this.email='';this.requestId=null;this.sent.set(false);this.error.set('');this.loading.set(false);}
 ngOnDestroy():void{this.leave();this.pending.unsubscribe();}
}
