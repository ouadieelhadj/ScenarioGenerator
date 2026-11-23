import {Component,DestroyRef,inject,signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MerchantOnboardingService,PublicRegistrationTracking} from '../../core/services/merchant-onboarding.service';

@Component({selector:'app-merchant-public-registrations',standalone:true,imports:[FormsModule,DatePipe],template:`
  <header><h1>Demandes publiques d’affiliation</h1><p>Suivi du compte commerçant, de son activation et du courrier personnel. Aucun token ni adresse n’est affiché.</p></header>
  <section class="panel">
    <label>Acquéreur <select name="acquirer" [(ngModel)]="acquirer" (ngModelChange)="changeAcquirer()" [disabled]="actionBusy()">
      <option value="">Sélectionner un acquéreur autorisé</option>
      @for(a of acquirers();track a.code){<option [value]="a.code">{{a.name}} ({{a.code}})</option>}
    </select></label>
    <button type="button" (click)="refresh()" [disabled]="busy()||actionBusy()||!acquirer">Actualiser les demandes</button>
    <p class="notice">Au maximum les 200 demandes les plus récentes. L’acceptation par le relais ne confirme pas la réception du courrier.</p>
    @if(error()){<p class="error" role="alert">{{error()}}</p>}
    @if(notice()){<p class="notice" role="status">{{notice()}}</p>}
    @if(busy()){<p role="status">Chargement des demandes…</p>}
    @if(loaded()&&!busy()&&rows().length===0){<p>Aucune demande visible pour cet acquéreur.</p>}
    @if(rows().length){<div class="scroll"><table><thead><tr><th>Référence</th><th>Création</th><th>État</th><th>Tentatives identité</th><th>Expiration du lien</th><th>Action</th></tr></thead>
      <tbody>@for(r of rows();track r.id){<tr><td>{{r.id}}</td><td>{{r.createdAt|date:'dd/MM/yyyy HH:mm':'UTC'}} UTC</td><td>{{state(r.state,r.invitationGeneration)}}</td>
        <td>{{r.identityAttempts}} / 5</td><td>{{r.expiresAt?(r.expiresAt|date:'dd/MM/yyyy HH:mm':'UTC')+' UTC':'Non émise'}}</td>
        <td>@if(r.renewable){<button type="button" (click)="openRenewal(r)" [disabled]="actionBusy()">Renouveler le lien expiré</button>}
          @else if(r.state==='MAIL_UNCERTAIN'){<button type="button" (click)="openRetry(r)" [disabled]="actionBusy()">Reprise après contrôle du relais</button>}@else{<span>Aucune action mail</span>}</td>
      </tr>}</tbody></table></div>}
  </section>
  @if(retry();as request){<section class="panel" aria-label="Confirmer la reprise du courrier">
    <h2>Contrôler le relais avant toute reprise</h2><p>Demande {{request.id}}. Ne renvoyez rien si le relais a déjà accepté le courrier ou si le résultat reste inconnu.</p>
    <label>Preuve de non-acceptation par le relais<textarea name="relayReason" [(ngModel)]="reason" maxlength="500" [disabled]="actionBusy()" placeholder="Référence non sensible du contrôle et constat de non-acceptation"></textarea></label>
    <p>Ne saisir aucun mot de passe, jeton ou contenu de message. La justification sera conservée chiffrée dans l’audit.</p>
    <button type="button" (click)="confirmRetry()" [disabled]="actionBusy()||!reason.trim()">Confirmer la remise en attente</button>
    <button type="button" (click)="cancelRetry()" [disabled]="actionBusy()">Annuler la reprise</button>
  </section>}
  @if(renewal();as request){<section class="panel" aria-label="Renouveler une invitation expirée">
    <h2>Nouveau lien pour le dossier existant</h2><p>Demande {{request.id}}. Le compte et le dossier sont conservés. L’ancien lien expiré ne sera pas prolongé et sera révoqué par le service d’identité.</p>
    <label>Motif du renouvellement<textarea name="renewalReason" [(ngModel)]="reason" (ngModelChange)="renewalRequestId=''" [disabled]="actionBusy()" maxlength="500" placeholder="Motif non sensible"></textarea></label>
    <p>Un nouvel envoi ne sera déclaré accepté qu’après le retour du relais SMTP. Aucun token ne sera affiché ici.</p>
    <button type="button" (click)="confirmRenewal()" [disabled]="actionBusy()||!reason.trim()">Demander le nouveau lien</button>
    <button type="button" (click)="cancelRenewal()" [disabled]="actionBusy()">Annuler le renouvellement</button>
  </section>}
`,styles:[`
  header{margin-bottom:20px}.panel{margin-bottom:18px;padding:20px;background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:12px}
  label{display:flex;gap:12px;align-items:center;margin-bottom:14px}select,textarea{padding:10px;border:1px solid var(--sg-border);border-radius:8px;background:var(--sg-bg-surface);color:var(--sg-text-primary)}textarea{min-width:320px;min-height:100px}
  button{padding:9px 14px;margin:4px;border:1px solid var(--sg-border);border-radius:8px;background:var(--sg-bg-surface);color:var(--sg-text-primary);cursor:pointer}button:disabled{opacity:.5;cursor:default}
  .scroll{overflow-x:auto}table{width:100%;border-collapse:collapse}th,td{padding:12px;text-align:left;border-bottom:1px solid var(--sg-border);vertical-align:top}.error{color:#b42318;background:#fff1f0;padding:12px}.notice{color:var(--sg-text-muted)}
`]})
export class MerchantPublicRegistrationsComponent {
  private readonly api=inject(MerchantOnboardingService);private readonly destroy=inject(DestroyRef);
  readonly acquirers=signal<{code:string;name:string}[]>([]);readonly rows=signal<PublicRegistrationTracking[]>([]);
  readonly busy=signal(false);readonly loaded=signal(false);readonly actionBusy=signal(false);readonly error=signal('');readonly notice=signal('');readonly retry=signal<PublicRegistrationTracking|null>(null);
  readonly renewal=signal<PublicRegistrationTracking|null>(null);renewalRequestId='';
  acquirer='';reason='';private generation=0;
  constructor(){this.api.allowedAcquirers().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:a=>{this.acquirers.set(a);if(a.length===1){this.acquirer=a[0].code;this.changeAcquirer();}},error:()=>this.error.set('Le catalogue des acquéreurs autorisés est indisponible.')});}
  changeAcquirer():void{if(this.actionBusy())return;++this.generation;this.rows.set([]);this.loaded.set(false);this.cancelRetry();this.cancelRenewal();this.notice.set('');this.refresh();}
  refresh():void{const current=++this.generation;this.error.set('');this.loaded.set(false);this.rows.set([]);if(!this.acquirer){this.busy.set(false);return;}this.busy.set(true);
    this.api.publicRegistrations(this.acquirer).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(current===this.generation){this.rows.set(r);this.loaded.set(true);this.busy.set(false);}},error:e=>{if(current===this.generation){this.error.set(this.message(e.status));this.busy.set(false);}}});}
  openRetry(row:PublicRegistrationTracking):void{if(row.state!=='MAIL_UNCERTAIN'||this.actionBusy())return;this.cancelRenewal();this.retry.set(row);this.reason='';this.error.set('');this.notice.set('');}
  openRenewal(row:PublicRegistrationTracking):void{if(!row.renewable||this.actionBusy())return;this.cancelRetry();this.renewal.set(row);this.reason='';this.renewalRequestId=crypto.randomUUID();this.error.set('');this.notice.set('');}
  cancelRenewal():void{if(this.actionBusy())return;this.renewal.set(null);this.reason='';this.renewalRequestId='';}
  confirmRenewal():void{const row=this.renewal();if(!row||!this.reason.trim()||this.actionBusy())return;this.renewalRequestId=this.renewalRequestId||crypto.randomUUID();
    const current=this.generation;this.actionBusy.set(true);this.error.set('');
    this.api.renewRegistrationInvitation(this.acquirer,row.id,this.renewalRequestId,this.reason.trim(),row.version).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{this.actionBusy.set(false);if(current!==this.generation)return;this.cancelRenewal();this.notice.set('Renouvellement demandé pour le même dossier. Aucun envoi ni activation n’est encore confirmé.');this.refresh();},
      error:e=>{this.actionBusy.set(false);if(current===this.generation)this.error.set(this.message(e.status));}
    });
  }
  cancelRetry():void{if(this.actionBusy())return;this.retry.set(null);this.reason='';}
  confirmRetry():void{const row=this.retry();if(!row||!this.reason.trim()||this.actionBusy())return;const current=this.generation;this.actionBusy.set(true);this.error.set('');
    this.api.retryRegistrationMail(this.acquirer,row.id,this.reason.trim(),row.version).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.actionBusy.set(false);if(current!==this.generation)return;this.cancelRetry();this.notice.set('Demande remise en attente. Aucun nouvel envoi ni réception n’est encore confirmé.');this.refresh();},error:e=>{this.actionBusy.set(false);if(current===this.generation)this.error.set(this.message(e.status));}});}
  private message(status:number):string{return status===401?'Session expirée : reconnectez-vous.':status===403?'Droits insuffisants pour cet acquéreur.':status===409?'La demande a changé : annulez puis actualisez avant une nouvelle action.':'Le suivi des demandes est indisponible. Aucun envoi n’est confirmé.';}
  state(value:string,generation=0):string{if(generation>0&&value==='WAIT_IDENTITY')return 'Nouveau lien en attente du service d’identité';if(generation>0&&value==='IDENTITY_WORKING')return 'Renouvellement du lien en cours';return ({WAIT_IDENTITY:'En attente de création du compte',IDENTITY_WORKING:'Création du compte en cours',MAIL_PENDING:'Courrier en attente',MAIL_SENDING:'Tentative de courrier en cours',MAIL_ACCEPTED:'Accepté par le relais — réception non confirmée',MAIL_UNCERTAIN:'Résultat courrier incertain',ACTIVATED:'Compte activé',EXPIRED:'Lien expiré',NEEDS_ATTENTION:'Intervention requise'} as Record<string,string>)[value]||'État non reconnu — consulter le support';}
}
