import {Component,Input,Output,EventEmitter,OnChanges,OnDestroy,inject,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Observable,Subject,takeUntil} from 'rxjs';
import {MerchantIncidentsService,IncidentCollective,IncidentCollectiveHead,IncidentBroadcast,IncidentView} from '../../core/services/merchant-incidents.service';

@Component({selector:'app-incident-collectives',standalone:true,imports:[CommonModule,FormsModule],template:`
 @if(manage()){
 <details class="collective"><summary>Incidents collectifs · Support uniquement</summary>
 <p>Le dossier principal, les motifs et la liste des commerçants restent internes. Une diffusion ajoute une réponse publique distincte à chaque ticket choisi ; elle ne résout ni ne clôture ces tickets.</p>
 @if(error()){<p role="alert">{{error()}}</p>}@if(notice()){<p role="status">{{notice()}}</p>}
 @if(uncertain()){<p>Résultat non confirmé : ne créez pas une autre demande. Reprenez exactement la même action avec son identifiant conservé.</p><button (click)="retry()" [disabled]="busy()">Reprendre la même action collective</button>}
 <fieldset [disabled]="busy()||uncertain()"><legend>Dossiers principaux</legend>
 <button (click)="load()">Charger les collectifs</button>
 @for(h of heads();track h.id){<button (click)="open(h.id)">{{h.subject}} · {{h.state==='OPEN'?'Ouvert':'Clôturé'}}</button>}
 <button (click)="page(-1)" [disabled]="offset===0">Collectifs précédents</button><button (click)="page(1)" [disabled]="heads().length<50">Collectifs suivants</button>
 <form (ngSubmit)="create()"><h3>Créer un dossier principal interne</h3>
 <label>Objet interne du collectif<input name="collectiveSubject" [(ngModel)]="subject" minlength="3" maxlength="160" required /></label>
 <label>Description interne du collectif<textarea name="collectiveDescription" [(ngModel)]="description" minlength="5" maxlength="8000" required></textarea></label>
 <button type="submit">Créer le collectif interne</button></form></fieldset>
 @if(group();as g){
 <h3>{{g.subject}}</h3><p>{{g.description}}</p><p>État : {{g.state==='OPEN'?'Ouvert':'Clôturé'}} · version {{g.version}}</p>
 <fieldset [disabled]="busy()||uncertain()"><legend>Rattachements internes</legend><button (click)="open(g.id)">Actualiser ce collectif</button>
 @if(g.state==='OPEN'){
 <label>Ticket à rattacher<select [(ngModel)]="candidateId"><option value="">Choisir dans la page de tickets autorisés</option>@for(t of candidates;track t.id){@if(t.status!=='DRAFT'){<option [value]="t.id">{{t.reference}} · {{t.subject}}</option>}}</select></label>
 <p>Utilisez la pagination des tickets ci-dessus pour choisir un autre ticket. Aucun UUID étranger ne donne d’accès supplémentaire.</p>
 <label>Motif interne du rattachement<input [(ngModel)]="reason" minlength="3" maxlength="8000" /></label><button (click)="membership(candidateId,true)" [disabled]="!candidateId||reason.trim().length<3">Rattacher le ticket</button>}
 @for(m of g.members;track m.incidentId){<p>{{m.reference}} · {{m.subject}} · {{m.status}} · version {{m.version}}</p>@if(g.state==='OPEN'){<button (click)="membership(m.incidentId,false)" [disabled]="reason.trim().length<3">Retirer {{m.reference}} du collectif</button>}}
 <label>Motif interne de clôture ou réouverture<input [(ngModel)]="decisionReason" maxlength="8000" /></label><button (click)="decide()" [disabled]="decisionReason.trim().length<3">{{g.state==='OPEN'?'Clôturer uniquement le collectif':'Réouvrir uniquement le collectif'}}</button></fieldset>
 @if(g.canSend&&g.state==='OPEN'){
 <fieldset [disabled]="busy()||uncertain()"><legend>Préparer une communication publique</legend>
 <p>Sélectionnez les destinataires et relisez le texte. Toute modification du collectif ou d’un ticket après préparation impose une nouvelle préparation. Aucune donnée interne ne doit être copiée dans ce texte.</p>
 @for(m of g.members;track m.incidentId){<label><input type="checkbox" [checked]="selected.has(m.incidentId)" (change)="toggle(m.incidentId)" />{{m.reference}} · {{m.subject}}</label>}
 <label>Texte public commun<textarea [(ngModel)]="publicText" maxlength="8000"></textarea></label><button (click)="prepare()" [disabled]="selected.size===0||!publicText.trim()">Préparer sans envoyer</button></fieldset>}
 @for(b of g.broadcasts;track b.id){<article><h4>Communication {{b.state==='SENT'?'envoyée':b.state==='READY'?'préparée':'périmée'}}</h4><p>{{b.text}}</p><p>{{b.targets.length}} ticket(s) destinataire(s) ; préparée par {{b.author}} le {{b.createdAt|date:'dd/MM/yyyy HH:mm'}}.</p>
 @for(t of b.targets;track t.incidentId){<p>Destinataire : {{memberReference(t.incidentId)}} · version relue {{t.expectedVersion}}</p>}
 @if(b.state==='READY'&&g.canSend){<label><input type="checkbox" [checked]="confirmed===b.id" (change)="confirmed=confirmed===b.id?'':b.id" [disabled]="busy()||uncertain()" />J’ai relu le texte public et ces destinataires</label><button (click)="send(b)" [disabled]="confirmed!==b.id||busy()||uncertain()">Confirmer et diffuser à ces tickets</button>}</article>}
 <details><summary>Historique interne du collectif (100 derniers événements)</summary>@for(e of g.history;track e.version){<p>{{e.version}} · {{e.action}} · {{e.actor}} · {{e.createdAt|date:'dd/MM/yyyy HH:mm'}}</p>@if(e.reason){<p>{{e.reason}}</p>}}</details>
 }
 </details>}`,styles:[`
 :host{display:block}.collective{padding:1rem;margin:1rem 0;border:1px solid #94a3b8;border-radius:.65rem;background:var(--surface-card,#fff)}
 summary{cursor:pointer;font-weight:600;min-height:2rem}fieldset,article{padding:1rem;margin:1rem 0;border:1px solid #cbd5e1;border-radius:.5rem;min-width:0}
 legend{font-weight:600;padding:0 .35rem}label{display:block;margin:.8rem 0;font-weight:500}p{overflow-wrap:anywhere;line-height:1.5}
 input:not([type=checkbox]),textarea,select{display:block;box-sizing:border-box;width:100%;max-width:42rem;padding:.65rem;border:1px solid #94a3b8;border-radius:.35rem;font:inherit;color:inherit;background:var(--surface-card,#fff);margin-top:.3rem}
 textarea{min-height:5rem;resize:vertical}input[type=checkbox]{margin-right:.5rem;accent-color:#2563eb}label:has(input[type=checkbox]){font-weight:400}
 button{font:inherit;min-height:2.5rem;margin:.3rem .35rem .3rem 0;padding:.45rem .8rem;border:1px solid #94a3b8;border-radius:.35rem;background:var(--surface-card,#fff);color:inherit;cursor:pointer;max-width:100%;overflow-wrap:anywhere}
 button:hover:not(:disabled){border-color:#2563eb;background:var(--surface-hover,#eff6ff)}button:disabled{opacity:.55;cursor:default}
 :focus-visible{outline:2px solid #2563eb;outline-offset:2px}[role=alert]{color:#991b1b}[role=status]{color:#166534}article p{white-space:pre-wrap}
 `]})
export class IncidentCollectivesComponent implements OnChanges,OnDestroy {
 @Input() acquirer='';@Input() candidates:IncidentView[]=[];@Output() changed=new EventEmitter<void>();
 private readonly api=inject(MerchantIncidentsService);private readonly cancel=new Subject<void>();
 readonly manage=signal(false);readonly group=signal<IncidentCollective|null>(null);readonly heads=signal<IncidentCollectiveHead[]>([]);
 readonly busy=signal(false);readonly uncertain=signal(false);readonly error=signal('');readonly notice=signal('');
 subject='';description='';reason='';decisionReason='';candidateId='';publicText='';confirmed='';selected=new Set<string>();offset=0;
 private generation=0;private detailRequest=0;private pending:{execute:()=>Observable<unknown>;done:(value:unknown)=>void}|null=null;
 ngOnChanges(changes:import('@angular/core').SimpleChanges){if(!changes['acquirer'])return;this.cancel.next();const generation=++this.generation;
  this.manage.set(false);this.group.set(null);this.heads.set([]);this.error.set('');this.notice.set('');this.pending=null;this.busy.set(false);this.uncertain.set(false);
  this.subject='';this.description='';this.reason='';this.decisionReason='';this.candidateId='';this.publicText='';this.confirmed='';this.selected.clear();this.offset=0;
  if(this.acquirer)this.api.collectiveCapabilities(this.acquirer).pipe(takeUntil(this.cancel)).subscribe({next:c=>{if(generation===this.generation)this.manage.set(c.manage===true);},error:()=>this.manage.set(false)});
 }
 ngOnDestroy(){this.cancel.next();this.cancel.complete();}
 load(){if(!this.manage()||this.busy()||this.uncertain())return;const generation=this.generation;this.api.collectives(this.acquirer,this.offset).pipe(takeUntil(this.cancel)).subscribe({next:r=>{if(generation===this.generation)this.heads.set(r);},error:()=>{this.heads.set([]);this.error.set('Collectifs indisponibles dans vos droits courants.');}});}
 page(delta:number){this.offset=Math.max(0,this.offset+delta*50);this.load();}
 open(id:string){if(this.busy()||this.uncertain())return;const generation=this.generation,request=++this.detailRequest;this.group.set(null);this.confirmed='';this.selected.clear();
  this.api.collective(this.acquirer,id).pipe(takeUntil(this.cancel)).subscribe({next:r=>{if(generation===this.generation&&request===this.detailRequest){this.group.set(r);this.error.set('');}},error:()=>{if(request===this.detailRequest)this.error.set('Collectif indisponible ou droits retirés.');}});}
 create(){if(this.subject.trim().length<3||this.description.trim().length<5)return;const code=this.acquirer,input={requestId:crypto.randomUUID(),subject:this.subject,description:this.description};
  this.mutate(()=>this.api.createCollective(code,input),value=>{this.group.set(value as IncidentCollective);this.subject='';this.description='';this.notice.set('Dossier principal interne créé ; aucun ticket commerçant créé.');this.load();});}
 membership(incidentId:string,linked:boolean){const g=this.group();if(!g||!incidentId||this.reason.trim().length<3)return;const code=this.acquirer,input={requestId:crypto.randomUUID(),expectedVersion:g.version,incidentId,linked,reason:this.reason};
  this.mutate(()=>this.api.collectiveMembership(code,g.id,input),value=>{this.group.set(value as IncidentCollective);this.selected.clear();this.confirmed='';this.notice.set('Rattachement interne enregistré sans message public.');});}
 decide(){const g=this.group();if(!g||this.decisionReason.trim().length<3)return;const code=this.acquirer,input={requestId:crypto.randomUUID(),expectedVersion:g.version,closed:g.state==='OPEN',reason:this.decisionReason};
  this.mutate(()=>this.api.collectiveState(code,g.id,input),value=>{this.group.set(value as IncidentCollective);this.confirmed='';this.notice.set('État du collectif enregistré ; états des tickets inchangés.');});}
 toggle(id:string){if(this.selected.has(id))this.selected.delete(id);else this.selected.add(id);}
 prepare(){const g=this.group();if(!g||!this.publicText.trim()||!this.selected.size)return;const code=this.acquirer,input={requestId:crypto.randomUUID(),expectedVersion:g.version,text:this.publicText,targets:g.members.filter(m=>this.selected.has(m.incidentId)).map(m=>({incidentId:m.incidentId,expectedVersion:m.version}))};
  this.mutate(()=>this.api.prepareBroadcast(code,g.id,input),()=>{this.open(g.id);this.notice.set('Communication préparée, aucun message encore envoyé. Relisez puis confirmez.');});}
 send(b:IncidentBroadcast){const g=this.group();if(!g||this.confirmed!==b.id)return;const code=this.acquirer,input={requestId:crypto.randomUUID(),expectedVersion:g.version,confirm:true};
  this.mutate(()=>this.api.sendBroadcast(code,g.id,b.id,input),()=>{this.confirmed='';this.open(g.id);this.changed.emit();this.notice.set('Diffusion confirmée : une réponse publique distincte par ticket sélectionné.');});}
 memberReference(id:string){return this.group()?.members.find(m=>m.incidentId===id)?.reference||'Ticket précédemment rattaché (historique interne)';}
 private mutate(execute:()=>Observable<unknown>,done:(value:unknown)=>void){if(this.busy()||this.pending)return;this.detailRequest++;this.pending={execute,done};this.retry();}
 retry(){if(this.busy()||!this.pending)return;const pending=this.pending,generation=this.generation;this.busy.set(true);this.error.set('');this.notice.set('');
  pending.execute().pipe(takeUntil(this.cancel)).subscribe({next:value=>{if(generation!==this.generation)return;this.busy.set(false);this.uncertain.set(false);this.pending=null;pending.done(value);},error:e=>{if(generation!==this.generation)return;this.busy.set(false);
   if(e.status>=400&&e.status<500){this.pending=null;this.uncertain.set(false);this.confirmed='';this.group.set(null);this.heads.set([]);this.error.set('Action refusée ou version modifiée. Rechargez le collectif et préparez à nouveau si nécessaire.');}
   else{this.uncertain.set(true);this.error.set('Résultat collectif non confirmé. Reprenez uniquement la même action, sans changer le texte ni les destinataires.');}
  }});
 }
}
