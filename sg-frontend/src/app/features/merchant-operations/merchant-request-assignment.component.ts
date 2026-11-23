import {Component,DestroyRef,EventEmitter,Input,OnChanges,Output,inject,signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RequestAssignment,RequestAssignmentEvent,RequestAssignmentTarget,RequestCheckerEvent} from '../../core/models/merchant-operations.models';
import {url} from '../../core/config/api.config';

/** Shared operational handover UI. No role, approval or identity is granted by this form. */
@Component({selector:'app-merchant-request-assignment',standalone:true,imports:[FormsModule,DatePipe],template:`
  <section aria-label="Responsabilité de la demande">
    <p>Maker responsable : {{assignment?.responsibleSubject || originalAuthor}}. L’auteur initial et les validations restent conservés.</p>
    <p>Checker responsable : {{assignment?.supervision?.checkerSubject || 'Pas encore pris en charge'}}.</p>
    @if(assignment?.supervision?.pendingId){<p role="status">Transfert proposé à {{assignment?.supervision?.pendingTarget}} : {{assignment?.supervision?.pendingReason}}. Le checker actuel reste responsable jusqu’à acceptation.</p>}
    @if(assignment?.supervision?.canTake||assignment?.supervision?.canPropose||assignment?.supervision?.canRespond){
      <label>Motif de prise en charge ou de transfert<textarea name="checkerReason" [(ngModel)]="checkerReason" maxlength="1000" [disabled]="busy()" required></textarea></label>
      @if(assignment?.supervision?.canTake){<button type="button" (click)="checkerAction('take-charge')" [disabled]="busy()||reading()||needsRefresh()||!checkerReason.trim()">Prendre en charge la demande</button>}
      @if(assignment?.supervision?.canPropose){
        <button type="button" (click)="loadCheckers(0)" [disabled]="busy()||reading()">Choisir un autre checker</button>
        @if(checkerSelection()){
          <label>Checker destinataire<select name="checkerTarget" [(ngModel)]="checkerTarget" [disabled]="busy()||reading()">
            <option value="">Choisir un checker autorisé</option>@for(person of checkers();track person.identitySubject){
              <option [value]="person.identitySubject" [disabled]="person.identitySubject===assignment?.supervision?.checkerSubject">{{person.label}}</option>}
          </select></label>
          <button type="button" (click)="loadCheckers(checkerPage-1)" [disabled]="checkerPage===0||busy()||reading()">Checkers précédents</button>
          <button type="button" (click)="loadCheckers(checkerPage+1)" [disabled]="checkers().length<50||busy()||reading()">Checkers suivants</button>
          <button type="button" (click)="checkerAction('propose-checker-transfer')" [disabled]="busy()||reading()||needsRefresh()||!checkerTarget||!checkerReason.trim()">Proposer le transfert au checker</button>
        }
      }
      @if(assignment?.supervision?.canRespond){
        <button type="button" (click)="checkerAction('accept-checker-transfer')" [disabled]="busy()||reading()||needsRefresh()||!checkerReason.trim()">Accepter la responsabilité checker</button>
        <button type="button" (click)="checkerAction('refuse-checker-transfer')" [disabled]="busy()||reading()||needsRefresh()||!checkerReason.trim()">Refuser le transfert checker</button>
      }
    }
    <button type="button" (click)="loadCheckerHistory(0)" [disabled]="busy()||reading()">Historique des prises en charge checker</button>
    @if(checkerHistory();as rows){
      <div style="overflow:auto"><table><thead><tr><th>Date</th><th>Action</th><th>Checker initial</th><th>Destinataire</th><th>Acteur</th><th>Motif</th></tr></thead>
        <tbody>@for(event of rows;track event.id){<tr><td>{{event.at|date:'dd/MM/yyyy HH:mm'}}</td><td>{{checkerActionLabel(event.action)}}</td><td>{{event.fromChecker||'—'}}</td><td>{{event.toChecker}}</td><td>{{event.actor}}</td><td>{{event.reason}}</td></tr>}
        @empty{<tr><td colspan="6">Aucune prise en charge checker enregistrée sur cette page.</td></tr>}</tbody></table></div>
      <button type="button" (click)="loadCheckerHistory(checkerHistoryPage-1)" [disabled]="checkerHistoryPage===0||busy()||reading()">Prises en charge précédentes</button>
      <button type="button" (click)="loadCheckerHistory(checkerHistoryPage+1)" [disabled]="rows.length<50||busy()||reading()">Prises en charge suivantes</button>
    }
    <button type="button" (click)="loadHistory(0)" [disabled]="busy()||reading()">Historique des réattributions</button>
    @if(assignment?.canReassign){
      <button type="button" (click)="openTransfer()" [disabled]="busy()||reading()">Réattribuer cette demande</button>
    }
    @if(error()){<p role="alert">{{error()}}</p>}
    @if(needsRefresh()){<button type="button" (click)="changed.emit()" [disabled]="busy()">Actualiser la demande après conflit</button>}
    @if(message()){<p role="status">{{message()}}</p>}
    @if(open()){
      <form (ngSubmit)="transfer()">
        <label>Nouveau responsable autorisé<select name="assignmentTarget" [(ngModel)]="target" [disabled]="busy()||reading()" required>
          <option value="">Choisir un responsable</option>@for(person of targets();track person.identitySubject){
            <option [value]="person.identitySubject" [disabled]="person.identitySubject===assignment?.responsibleSubject">{{person.label}}</option>}
        </select></label>
        <button type="button" (click)="loadTargets(targetPage-1)" [disabled]="targetPage===0||busy()||reading()">Responsables précédents</button>
        <button type="button" (click)="loadTargets(targetPage+1)" [disabled]="targets().length<50||busy()||reading()">Responsables suivants</button>
        @if(!targets().length&&!reading()){<p>Aucun responsable autorisé sur cette page. Aucun droit ne sera attribué automatiquement.</p>}
        <label>Motif de réattribution<textarea name="assignmentReason" [(ngModel)]="reason" maxlength="1000" [disabled]="busy()" required></textarea></label>
        <p>La réattribution ne constitue pas une approbation. Un ancien ou nouveau demandeur ne peut pas approuver cette version.</p>
        <button type="submit" [disabled]="busy()||reading()||needsRefresh()||!target||!reason.trim()">Confirmer la réattribution</button>
        <button type="button" (click)="cancel()" [disabled]="busy()">Annuler la réattribution</button>
      </form>
    }
    @if(history();as rows){
      <div style="overflow:auto"><table><thead><tr><th>Date</th><th>Ancien responsable</th><th>Nouveau responsable</th><th>Auteur</th><th>Motif</th></tr></thead>
        <tbody>@for(event of rows;track event.id){<tr><td>{{event.at|date:'dd/MM/yyyy HH:mm'}}</td><td>{{event.fromSubject}}</td><td>{{event.toSubject}}</td><td>{{event.actor}}</td><td>{{event.reason}}</td></tr>}
        @empty{<tr><td colspan="5">Aucune réattribution enregistrée sur cette page.</td></tr>}</tbody></table></div>
      <button type="button" (click)="loadHistory(historyPage-1)" [disabled]="historyPage===0||busy()||reading()">Historique précédent</button>
      <button type="button" (click)="loadHistory(historyPage+1)" [disabled]="rows.length<50||busy()||reading()">Historique suivant</button>
    }
  </section>`,styles:[`section{border:1px solid #c9d5df;border-radius:8px;padding:12px;margin:12px 0}label{display:grid;gap:5px;margin:10px 0}select,textarea{width:100%;padding:8px}button{margin:4px;padding:8px}th,td{text-align:left;padding:8px;overflow-wrap:anywhere}table{width:100%;border-collapse:collapse}[role=alert]{color:#b42318}`]})
export class MerchantRequestAssignmentComponent implements OnChanges{
  @Input({required:true}) resource='';@Input({required:true}) businessVersion=0;
  @Input() assignment:RequestAssignment|undefined;@Input() originalAuthor='';@Output() changed=new EventEmitter<void>();
  private readonly http=inject(HttpClient);private readonly destroy=inject(DestroyRef);private generation=0;
  private pending:{signature:string;command:object}|null=null;
  readonly open=signal(false);readonly busy=signal(false);readonly reading=signal(false);readonly error=signal('');readonly message=signal('');
  readonly needsRefresh=signal(false);
  readonly targets=signal<RequestAssignmentTarget[]>([]);readonly history=signal<RequestAssignmentEvent[]|null>(null);
  readonly checkers=signal<RequestAssignmentTarget[]>([]);readonly checkerHistory=signal<RequestCheckerEvent[]|null>(null);readonly checkerSelection=signal(false);
  checkerTarget='';checkerReason='';checkerPage=0;checkerHistoryPage=0;
  target='';reason='';targetPage=0;historyPage=0;
  ngOnChanges():void{++this.generation;this.busy.set(false);this.reading.set(false);this.open.set(false);this.targets.set([]);this.history.set(null);this.target='';this.reason='';this.pending=null;this.checkers.set([]);this.checkerHistory.set(null);this.checkerSelection.set(false);this.checkerTarget='';this.checkerReason='';this.needsRefresh.set(false);this.error.set('');}
  openTransfer():void{if(!this.assignment?.canReassign||this.busy())return;this.open.set(true);this.loadTargets(0);}
  cancel():void{this.open.set(false);this.target='';this.reason='';this.pending=null;}
  loadTargets(page:number):void{
    if(page<0||this.busy()||!this.assignment?.canReassign)return;const generation=++this.generation;this.reading.set(true);this.error.set('');
    this.http.get<RequestAssignmentTarget[]>(url.onboarding(this.resource+'/assignment-targets'),{params:{page,size:50}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:rows=>{if(generation===this.generation){this.targetPage=page;this.targets.set(rows);this.target='';this.reading.set(false);}},
      error:()=>{if(generation===this.generation){this.reading.set(false);this.targets.set([]);this.error.set('Les responsables autorisés sont indisponibles. Aucun transfert effectué.');}}
    });
  }
  loadHistory(page:number):void{
    if(page<0||this.busy())return;const generation=++this.generation;this.reading.set(true);this.error.set('');
    this.http.get<RequestAssignmentEvent[]>(url.onboarding(this.resource+'/assignment-history'),{params:{page,size:50}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:rows=>{if(generation===this.generation){this.historyPage=page;this.history.set(rows);this.reading.set(false);}},
      error:()=>{if(generation===this.generation){this.reading.set(false);this.history.set(null);this.error.set('Historique indisponible ou accès refusé.');}}
    });
  }
  transfer():void{
    if(!this.assignment?.canReassign||this.busy()||this.reading()||this.needsRefresh()||!this.target||!this.reason.trim())return;
    if(this.reason.length>1000||/[\x00-\x1f\x7f]/.test(this.reason)){this.error.set('Saisissez un motif valide de 1 à 1 000 caractères.');return;}
    const reason=this.reason.trim(),signature=JSON.stringify({resource:this.resource,target:this.target,reason,business:this.businessVersion,assignment:this.assignment.version,checker:this.assignment.supervision?.version});
    if(this.pending?.signature!==signature)this.pending={signature,command:{requestId:crypto.randomUUID(),targetSubject:this.target,reason,expectedBusinessVersion:this.businessVersion,expectedAssignmentVersion:this.assignment.version,expectedCheckerVersion:this.assignment.supervision?.version}};
    const generation=++this.generation;this.busy.set(true);this.error.set('');this.message.set('');
    this.http.post(url.onboarding(this.resource+'/reassign'),this.pending.command).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{if(generation===this.generation){this.busy.set(false);this.cancel();this.message.set('Responsabilité réattribuée ; auteur, pièces et validations conservés.');this.changed.emit();}},
      error:e=>{if(generation===this.generation){this.busy.set(false);this.error.set(e.status===403?'Réattribution refusée pour ce profil ou ce périmètre.':e.status===409?'La demande ou sa responsabilité a changé ; actualisez avant de reprendre.':'Réattribution non confirmée. Vous pouvez réessayer la même commande.');
        if(e.status===403||e.status===409){this.pending=null;this.changed.emit();}}}
    });
  }
  loadCheckers(page:number):void{
    if(page<0||this.busy()||!this.assignment?.supervision?.canPropose)return;
    const generation=++this.generation;this.reading.set(true);this.error.set('');this.checkerSelection.set(true);
    this.http.get<RequestAssignmentTarget[]>(url.onboarding(this.resource+'/checker-targets'),{params:{page,size:50}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:rows=>{if(generation===this.generation){this.checkerPage=page;this.checkers.set(rows);this.checkerTarget='';this.reading.set(false);}},
      error:()=>{if(generation===this.generation){this.reading.set(false);this.checkers.set([]);this.error.set('Les checkers autorisés sont indisponibles. Aucun transfert effectué.');}}
    });
  }
  loadCheckerHistory(page:number):void{
    if(page<0||this.busy())return;const generation=++this.generation;this.reading.set(true);this.error.set('');
    this.http.get<RequestCheckerEvent[]>(url.onboarding(this.resource+'/checker-history'),{params:{page,size:50}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:rows=>{if(generation===this.generation){this.checkerHistoryPage=page;this.checkerHistory.set(rows);this.reading.set(false);}},
      error:()=>{if(generation===this.generation){this.reading.set(false);this.checkerHistory.set(null);this.error.set('Historique checker indisponible ou accès refusé.');}}
    });
  }
  checkerAction(action:'take-charge'|'propose-checker-transfer'|'accept-checker-transfer'|'refuse-checker-transfer'):void{
    const s=this.assignment?.supervision;if(!s||this.busy()||this.reading()||this.needsRefresh())return;
    if(action==='take-charge'?!s.canTake:action==='propose-checker-transfer'?!s.canPropose:!s.canRespond)return;
    const reason=this.checkerReason.trim();if(!reason||reason.length>1000||/[\x00-\x1f\x7f]/.test(reason)){this.error.set('Saisissez un motif valide de 1 à 1 000 caractères.');return;}
    const targetSubject=action==='propose-checker-transfer'?this.checkerTarget:null;
    if(action==='propose-checker-transfer'&&!targetSubject)return;
    const proposalId=action==='accept-checker-transfer'||action==='refuse-checker-transfer'?s.pendingId:null;
    const signature=JSON.stringify({resource:this.resource,action,targetSubject,reason,business:this.businessVersion,checker:s.version,proposalId});
    if(this.pending?.signature!==signature)this.pending={signature,command:{requestId:crypto.randomUUID(),targetSubject,reason,expectedBusinessVersion:this.businessVersion,expectedCheckerVersion:s.version,proposalId}};
    const generation=++this.generation;this.busy.set(true);this.error.set('');this.message.set('');
    this.http.post(url.onboarding(this.resource+'/'+action),this.pending.command).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{if(generation===this.generation){this.busy.set(false);this.pending=null;this.message.set(action==='propose-checker-transfer'?'Transfert proposé. La responsabilité reste au checker actuel jusqu’à acceptation.':action==='accept-checker-transfer'?'Transfert accepté : la responsabilité checker a changé.':action==='refuse-checker-transfer'?'Transfert refusé : le checker initial reste responsable.':'Demande prise en charge par le checker.');this.changed.emit();}},
      error:e=>{if(generation===this.generation){this.busy.set(false);this.error.set(e.status===403?'Action refusée pour ce profil ou ce périmètre.':e.status===409?'La demande ou le transfert a changé ; actualisez son détail.':'Action non confirmée. Réessayez la même commande.');if(e.status===403||e.status===409){this.pending=null;this.needsRefresh.set(true);}}}
    });
  }
  checkerActionLabel(action:string):string{return ({TAKE:'Prise en charge',PROPOSE:'Transfert proposé',ACCEPT:'Transfert accepté',REFUSE:'Transfert refusé'} as Record<string,string>)[action]||'Action checker';}
}
