import {Component,Input,OnDestroy,inject,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';
import {Subscription} from 'rxjs';
import {AuthService} from '../../core/auth/auth.service';
import {PersonalAccountActionsService,EmailAssistanceStatus} from '../../core/services/personal-account-actions.service';

@Component({selector:'app-personal-email-assistance',standalone:true,imports:[FormsModule,DatePipe],styles:[`
 :host{display:block;padding:1.2rem;max-width:52rem;margin:auto}form{display:grid;gap:1rem}label{display:grid;gap:.3rem}input{padding:.6rem;max-width:100%;box-sizing:border-box}button{padding:.6rem;margin:.3rem}dl{display:grid;grid-template-columns:1fr 1.5fr;gap:.5rem}dd{margin:0;overflow-wrap:anywhere}
 `],template:`
 <h1>Assistance après perte d’e-mail</h1>
 @if(!authorized || !acquirerCode){<p role="alert">Seul le support habilité de votre acquéreur peut autoriser cette assistance.</p>}
 @else {
  <p>Acquéreur : {{ acquirerCode }}</p>
  <p>Vérifiez l’identité selon la procédure de votre établissement. Ne saisissez ni pièce d’identité, ni mot de passe, ni secret. Le destinataire confirmera personnellement sa nouvelle adresse.</p>
  @if(error()){<p role="alert">{{error()}}</p>}
  @if(requestId){<p>Référence de demande : {{requestId}}</p>}
  <form (ngSubmit)="submit()">
   <label>Identifiant personnel<input name="personalId" [(ngModel)]="personalId" required pattern="[Ff][Pp]-[Uu][0-9]{6,}" [disabled]="locked()" /></label>
   <label>Nouvelle adresse e-mail<input name="email" type="email" [(ngModel)]="email" required [disabled]="locked()" /></label>
   <label>Référence de vérification d’identité<input name="verificationReference" [(ngModel)]="reference" required pattern="[A-Za-z0-9][A-Za-z0-9._/-]{2,99}" [disabled]="locked()" /></label>
   <label><input name="identityVerified" type="checkbox" [(ngModel)]="verified" required [disabled]="locked()" /> J’ai vérifié l’identité selon la procédure de l’acquéreur</label>
   <button type="submit" [disabled]="busy() || locked() || !verified">Autoriser la vérification de la nouvelle adresse</button>
  </form>
  @if(locked()){
   <p role="status">Demande enregistrée ou résultat à vérifier. L’adresse ne change qu’après confirmation personnelle ; aucun envoi ni réception n’est présumé réussi.</p>
   <button type="button" (click)="refresh()" [disabled]="busy()">Actualiser le statut</button>
   @if(canRetry()){<button type="button" (click)="retry()" [disabled]="busy()">Reprendre la même demande</button>}
  }
  @if(status();as s){<section aria-label="Suivi de l’assistance"><h2>Suivi de l’assistance</h2><dl>
   <dt>Identifiant personnel</dt><dd>{{s.personalId}}</dd><dt>Vérification d’identité</dt><dd>{{s.verificationReference}}</dd>
   <dt>État de la demande</dt><dd>{{stateLabel(s.state)}}</dd><dt>Transmission au relais e-mail</dt><dd>{{mailLabel(s.mailState)}}</dd>
   <dt>Expiration du lien</dt><dd>{{s.expiresAt ? (s.expiresAt|date:'dd/MM/yyyy HH:mm') : 'Pas encore disponible'}}</dd>
   </dl></section>
   @if(['CONFIRMED','EXPIRED','BLOCKED','REJECTED'].includes(s.state)){<button type="button" (click)="reset()">Nouvelle assistance</button>}
  }
 }
 `})
export class PersonalEmailAssistanceComponent implements OnDestroy {
 @Input() authorized=false;@Input() acquirerCode='';
 private readonly service=inject(PersonalAccountActionsService);private readonly subscriptions=new Subscription();private revision=0;
 readonly busy=signal(false);readonly locked=signal(false);readonly error=signal('');readonly status=signal<EmailAssistanceStatus|null>(null);
 personalId='';email='';reference='';verified=false;requestId:string|null=null;
 private request:{personalId:string;email:string;verificationReference:string;identityVerified:boolean}|null=null;
 submit():void {if(this.busy()||this.locked()||!this.authorized||!this.acquirerCode)return;
  if(!/^FP-U[0-9]{6,}$/i.test(this.personalId)||!this.email||!/^[A-Za-z0-9][A-Za-z0-9._/-]{2,99}$/.test(this.reference)||!this.verified){this.error.set('Complétez la vérification d’identité et les champs obligatoires.');return;}
  this.requestId=crypto.randomUUID();this.request={personalId:this.personalId,email:this.email,verificationReference:this.reference,identityVerified:true};this.locked.set(true);this.send();
 }
 private send():void{if(!this.requestId||!this.request||this.busy())return;const revision=this.revision;this.busy.set(true);this.error.set('');
  this.subscriptions.add(this.service.assist(this.requestId,this.acquirerCode,this.request).subscribe({next:()=>{if(revision!==this.revision)return;this.busy.set(false);this.refresh();},error:e=>{if(revision!==this.revision)return;this.busy.set(false);if([400,401,403].includes(e.status)){this.error.set('Assistance refusée : vérifiez vos droits et votre contrôle d’identité.');this.locked.set(false);this.requestId=null;this.request=null;}else this.error.set('Résultat incertain. Actualisez le statut et conservez la même référence avant de reprendre.');}}));
 }
 canRetry():boolean{return !this.status()||['PREPARED','IDENTITY_UNCERTAIN'].includes(this.status()!.state);}
 retry():void{if(this.authorized&&this.locked()&&this.canRetry())this.send();}
 refresh():void{if(!this.authorized||!this.requestId||this.busy())return;const revision=this.revision;this.busy.set(true);
  this.subscriptions.add(this.service.assistanceStatus(this.requestId,this.acquirerCode).subscribe({next:s=>{if(revision!==this.revision)return;this.busy.set(false);this.status.set(s);this.error.set('');},error:()=>{if(revision!==this.revision)return;this.busy.set(false);this.error.set('Statut indisponible. Conservez la référence de demande et contactez le support.');}}));
 }
 stateLabel(s:string):string{return ({PREPARED:'Préparée, identité en attente',IDENTITY_WORKING:'Vérification en cours',IDENTITY_UNCERTAIN:'Résultat identité à vérifier',READY:'En attente de confirmation personnelle',COMPLETING:'Confirmation en cours',COMPLETE_UNCERTAIN:'Confirmation à vérifier',CONFIRMED:'Nouvelle adresse confirmée',REJECTED:'Refusée',BLOCKED:'Bloquée',EXPIRED:'Lien expiré'} as Record<string,string>)[s]||'À vérifier';}
 mailLabel(s:string):string{return ({NONE:'Non préparée',PENDING:'En attente',SENDING:'En cours',ACCEPTED:'Acceptée par le relais, réception non prouvée',UNCERTAIN:'Résultat incertain, ne pas renvoyer automatiquement',BLOCKED:'Bloquée'} as Record<string,string>)[s]||'À vérifier';}
 reset():void{++this.revision;this.personalId='';this.email='';this.reference='';this.verified=false;this.requestId=null;this.request=null;this.status.set(null);this.error.set('');this.locked.set(false);this.busy.set(false);}
 ngOnDestroy():void{this.reset();this.subscriptions.unsubscribe();}
}
@Component({standalone:true,imports:[PersonalEmailAssistanceComponent],template:`<app-personal-email-assistance [authorized]="authorized()" [acquirerCode]="auth.user()?.acquirerId || ''" />`})
export class PersonalEmailAssistancePageComponent {
 readonly auth=inject(AuthService);authorized():boolean{return this.auth.hasRole('BACK_OFFICE')&&this.auth.hasPermission('ONBOARDING_PERSONAL_EMAIL_SUPPORT');}
}
