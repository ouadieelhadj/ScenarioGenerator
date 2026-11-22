import { Component, Input, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { AllowedAcquirer } from '../../core/models/merchant-onboarding.models';
import { MerchantIncidentsService, IncidentView, IncidentMessage, IncidentAttachment, IncidentCategory, IncidentAction, IncidentCreation, IncidentContext, IncidentTarget, IncidentMetrics, IncidentDraftEdit, IncidentNotice, IncidentHistory, IncidentSummary } from '../../core/services/merchant-incidents.service';
import { IncidentAttachmentPolicy, readAttachmentPolicy, attachmentRefusal, attachmentSizeLabel, canCaptureIncidentPhoto } from '../../core/services/incident-attachment-policy';
import { readIncidentFields } from '../../core/services/incident-field-schema';
import { IncidentReferenceKind,IncidentReferenceOption,incidentReferenceKinds,readReferenceOptions,referencePayload } from '../../core/services/incident-business-references';
import {IncidentCollectivesComponent} from './incident-collectives.component';
import {exportIncidentAttachment} from '../../core/services/merchant-incident-file-export';

@Component({
 selector:'app-merchant-incidents',standalone:true,imports:[CommonModule,FormsModule,IncidentCollectivesComponent],
 templateUrl:'./merchant-incidents.component.html',styleUrl:'./merchant-incidents.component.scss',
})
export class MerchantIncidentsComponent implements OnInit,OnDestroy {
 @Input() mobile=false;
 private readonly api=inject(MerchantIncidentsService);private readonly onboarding=inject(MerchantOnboardingService);private readonly route=inject(ActivatedRoute);
 private readonly cancel=new Subject<void>();private readonly destroyed=new Subject<void>();
 private detailRequest=0;private listRequest=0;
 readonly acquirers=signal<AllowedAcquirer[]>([]);readonly tickets=signal<IncidentView[]>([]);readonly ticket=signal<IncidentView|null>(null);
 readonly messages=signal<IncidentMessage[]>([]);readonly attachments=signal<IncidentAttachment[]>([]);readonly catalog=signal<IncidentCategory[]>([]);
 readonly attachmentPolicy=signal<IncidentAttachmentPolicy|null>(null);readonly attachmentsLoaded=signal(false);private policyRequest=0;
 readonly downloading=signal(false);private downloadGeneration=0;
 readonly contexts=signal<Array<{context:IncidentContext;label:string}>>([]);readonly total=signal(0);readonly loading=signal(false);readonly error=signal('');readonly notice=signal('');
 readonly targets=signal<IncidentTarget[]>([]);readonly metrics=signal<IncidentMetrics|null>(null);
 readonly summary=signal<IncidentSummary|null>(null);readonly summaryLoading=signal(false);readonly summaryError=signal('');
 readonly summaryLoadedAt=signal<Date|null>(null);
 readonly statistics=signal<Array<{status:string;category:string;priority:string;volume:number}>>([]);private summaryRequest=0;
 readonly editing=signal<IncidentView|null>(null);private pendingDraft:{id:string;input:IncidentDraftEdit}|null=null;
 readonly referenceKinds=incidentReferenceKinds;referenceKind:IncidentReferenceKind='TERMINAL';referenceIndex='';referenceOffset=0;
 readonly referenceOptions=signal<IncidentReferenceOption[]>([]);readonly selectedReferences=signal<IncidentReferenceOption[]>([]);readonly linkedReferences=signal<IncidentReferenceOption[]>([]);
 readonly referencesLoaded=signal(false);readonly referencesLoading=signal(false);readonly referenceError=signal('');private referenceRequest=0;
 acquirer='';status='';offset=0;contextIndex='';category='OTHER';subject='';description='';impact='UNKNOWN';fields:Record<string,string>={};
 filterCategory='';filterPriority='';filterAssignee='';filterQueue='';filterNode='';
 readonly notices=signal<IncidentNotice[]>([]);readonly noticesLoaded=signal(false);selectedNotice='';relayEvidence='';deliveryDecision='DELIVERED';reconciliationConfirmed=false;private pendingReconciliation:object|null=null;
 readonly history=signal<IncidentHistory[]>([]);
 action:IncidentAction='REPLY';text='';priority='NORMAL';assignmentTeam='';assignee='';relatedId='';linkKind='DUPLICATE';
 creating=false;private pendingCreation:IncidentCreation|null=null;private pendingCommand:{key:string;input:object;action:IncidentAction}|null=null;
 readonly states=['DRAFT','OPEN','IN_PROGRESS','WAITING_MERCHANT','WAITING_THIRD_PARTY','RESOLVED','CLOSED','CANCELLED'];
 ngOnInit(){this.onboarding.allowedAcquirers().pipe(takeUntil(this.destroyed)).subscribe({next:rows=>{
  this.acquirers.set(rows);const selected=this.route.snapshot.queryParamMap.get('acquirer');
  if(selected&&rows.some(r=>r.code===selected))this.acquirer=selected;else if(rows.length===1)this.acquirer=rows[0].code;
  if(this.acquirer)this.changeAcquirer();
 },error:()=>this.error.set('Les acquéreurs autorisés sont indisponibles.')});}
 ngOnDestroy(){this.invalidateDownload();this.cancel.next();this.cancel.complete();this.destroyed.next();this.destroyed.complete();}
 changeAcquirer(){
  this.detailRequest++;this.listRequest++;
  this.invalidateDownload();
  this.cancel.next();this.ticket.set(null);this.tickets.set([]);this.messages.set([]);this.attachments.set([]);this.catalog.set([]);this.contexts.set([]);this.total.set(0);
  this.policyRequest++;this.attachmentPolicy.set(null);this.attachmentsLoaded.set(false);
  this.error.set('');this.notice.set('');this.offset=0;this.creating=false;this.resetForm();
  this.targets.set([]);this.metrics.set(null);
  this.summaryRequest++;this.summary.set(null);this.summaryLoadedAt.set(null);this.statistics.set([]);this.summaryError.set('');this.summaryLoading.set(false);
  this.clearNotices();
  this.filterCategory='';this.filterPriority='';this.filterAssignee='';this.filterQueue='';this.filterNode='';this.status='';
  if(!this.acquirers().some(a=>a.code===this.acquirer))return;
  this.refresh();this.api.catalog(this.acquirer,this.mobile).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>this.catalog.set(r),error:()=>this.fail('Catalogue indisponible.')});
  this.api.contexts(this.acquirer,this.mobile).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{
   this.contexts.set(r);const id=this.route.snapshot.queryParamMap.get('dossier');const index=r.findIndex(x=>x.context.dossierId===id);if(index>=0){this.contextIndex=String(index);this.creating=true;}
  },error:()=>this.fail('Les dossiers autorisés sont indisponibles.')});
 }
 refresh(){if(!this.acquirer)return;const code=this.acquirer,request=++this.listRequest,selected=this.ticket();this.loadAttachmentPolicy();this.loading.set(true);this.error.set('');this.api.list(code,this.mobile,this.offset,this.status,{category:this.filterCategory,priority:this.filterPriority,assignee:this.filterAssignee,queue:this.filterQueue,nodeId:this.filterNode})
  .pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:p=>{
   if(request!==this.listRequest||code!==this.acquirer)return;
   this.tickets.set(p.items);this.total.set(p.total);this.loading.set(false);
   if(selected&&this.ticket()?.id===selected.id){
    const current=p.items.find(row=>row.id===selected.id);
    if(current){const acknowledgement=this.notice();this.open(current);this.notice.set(acknowledgement);}
    else this.clearSelectedIncident();
   }
  },error:e=>{if(request!==this.listRequest||code!==this.acquirer)return;if(e.status===401||e.status===403)this.clearSelectedIncident();this.fail('Liste indisponible, module désactivé ou accès refusé.');}});}
 filter(){this.offset=0;this.refresh();}page(delta:number){this.offset=Math.max(0,this.offset+25*delta);this.refresh();}
 loadSummary(){if(!this.acquirer||this.summaryLoading())return;const code=this.acquirer,generation=++this.summaryRequest;this.summary.set(null);this.statistics.set([]);this.summaryError.set('');this.summaryLoading.set(true);
  this.api.summary(code,this.mobile).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:row=>{if(generation===this.summaryRequest&&code===this.acquirer){this.summary.set(row);this.summaryLoadedAt.set(new Date());this.summaryLoading.set(false);}},error:()=>{if(generation===this.summaryRequest){this.summaryLoading.set(false);this.summaryError.set('Indicateurs indisponibles. Le suivi des tickets reste possible.');}}});
  this.api.statistics(code,this.mobile).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:rows=>{if(generation===this.summaryRequest&&code===this.acquirer)this.statistics.set(rows);},error:()=>{if(generation===this.summaryRequest)this.statistics.set([]);}});
 }
 open(row:IncidentView){const request=++this.detailRequest,code=this.acquirer;this.invalidateDownload();this.ticket.set(null);this.error.set('');this.notice.set('');this.creating=false;this.editing.set(null);this.pendingDraft=null;this.loading.set(true);this.messages.set([]);this.attachments.set([]);
  this.referenceContextChanged();this.linkedReferences.set([]);this.referencesLoaded.set(false);
  this.attachmentsLoaded.set(false);this.loadAttachmentPolicy();
  this.clearNotices();
  this.api.detail(code,this.mobile,row.id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:t=>{
   if(request!==this.detailRequest||code!==this.acquirer)return;
   this.ticket.set(t);this.category=t.category;this.priority=t.priority;this.pendingCommand=null;this.selectAction(t);this.loading.set(false);this.loadConversation(t.id);
  },error:()=>{if(request!==this.detailRequest||code!==this.acquirer)return;this.clearSelectedIncident();this.fail('Incident indisponible dans votre périmètre.') }});
 }
 private clearSelectedIncident(){
  this.detailRequest++;this.invalidateDownload();this.ticket.set(null);this.messages.set([]);this.attachments.set([]);this.attachmentsLoaded.set(false);
  this.metrics.set(null);this.targets.set([]);this.clearNotices();this.creating=false;this.resetForm();this.notice.set('');
  this.summaryRequest++;this.summary.set(null);this.summaryLoadedAt.set(null);this.summaryLoading.set(false);this.statistics.set([]);this.summaryError.set('');
 }
 private loadConversation(id:string){
  this.referencesLoaded.set(false);this.linkedReferences.set([]);
  this.api.references(this.acquirer,this.mobile,id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:rows=>{if(this.ticket()?.id!==id)return;const safe=readReferenceOptions(rows);if(!safe){this.referenceError.set('Références indisponibles. Les échanges restent possibles.');return;}this.linkedReferences.set(safe);this.referencesLoaded.set(true);if(this.editing()?.id===id)this.selectedReferences.set(safe);},error:()=>{if(this.ticket()?.id===id)this.referenceError.set('Références indisponibles. Les échanges restent possibles.');}});
  this.attachmentsLoaded.set(false);this.attachments.set([]);
  this.api.messages(this.acquirer,this.mobile,id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{if(this.ticket()?.id===id)this.messages.set(r);},error:()=>this.fail('Conversation indisponible.')});
  this.api.attachments(this.acquirer,this.mobile,id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{if(this.ticket()?.id===id){this.attachments.set(r);this.attachmentsLoaded.set(true);}},error:()=>{if(this.ticket()?.id===id){this.attachmentsLoaded.set(false);this.fail('Pièces indisponibles.')}}});
  this.api.levels(this.acquirer,this.mobile,id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{if(this.ticket()?.id===id)this.metrics.set(r);},error:()=>this.metrics.set(null)});
  this.targets.set([]);if(this.ticket()?.actions.includes('ASSIGN'))this.api.targets(this.acquirer,this.mobile,id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{if(this.ticket()?.id===id)this.targets.set(r);},error:()=>this.fail('Équipes autorisées indisponibles.')});
 }
 assignmentMembers(){return this.targets().find(t=>t.id===this.assignmentTeam)?.members||[];}
 private clearNotices(){this.notices.set([]);this.history.set([]);this.noticesLoaded.set(false);this.selectedNotice='';this.relayEvidence='';this.reconciliationConfirmed=false;this.pendingReconciliation=null;}
 loadHistory(){const ticket=this.ticket();if(!ticket)return;this.api.history(this.acquirer,this.mobile,ticket.id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{if(this.ticket()?.id===ticket.id)this.history.set(r);},error:()=>this.fail('Historique indisponible dans votre périmètre.')});}
 loadNotices(){const ticket=this.ticket();if(!ticket)return;this.api.notices(this.acquirer,this.mobile,ticket.id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{if(this.ticket()?.id===ticket.id){this.notices.set(r);this.noticesLoaded.set(true);}},error:()=>this.fail('Suivi des avis indisponible dans votre périmètre.')});}
 reconcileNotice(){const ticket=this.ticket(),row=this.notices()[Number(this.selectedNotice)];
  if(!ticket||this.selectedNotice===''||!row||row.state!=='UNCERTAIN'||!row.attempt_id||!row.recipient_fingerprint||this.loading())return;
  if(!this.reconciliationConfirmed||this.relayEvidence.trim().length<8){this.fail('Vérifiez le résultat dans les traces du relais SMTP et renseignez la référence de preuve, sans secret.');return;}
  const payload={eventVersion:row.version,recipientFingerprint:row.recipient_fingerprint,attemptId:row.attempt_id,decision:this.deliveryDecision,relayEvidence:this.relayEvidence};
  if(this.pendingReconciliation&&JSON.stringify({...this.pendingReconciliation,requestId:undefined})!==JSON.stringify(payload)){this.fail('Rapprochement non confirmé : réessayez le même contenu ou rechargez le suivi.');return;}
  this.pendingReconciliation??={requestId:crypto.randomUUID(),...payload};this.loading.set(true);this.api.reconcileNotice(this.acquirer,this.mobile,ticket.id,this.pendingReconciliation).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:()=>{this.clearNotices();this.loading.set(false);this.notice.set('Rapprochement enregistré. Aucune opération métier rejouée.');this.loadNotices();},error:e=>{this.loading.set(false);this.error.set('Rapprochement non confirmé ou refusé. Actualisez le suivi avant une autre décision.');if(e.status>=400&&e.status<500)this.pendingReconciliation=null;}});
 }
 export(){this.api.export(this.acquirer,this.mobile).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:blob=>{const address=URL.createObjectURL(blob);const link=document.createElement('a');link.href=address;link.download='incidents.csv';link.click();URL.revokeObjectURL(address);},error:()=>this.fail('Export indisponible dans votre périmètre.')});}
 newIncident(){this.detailRequest++;this.invalidateDownload();this.ticket.set(null);this.attachments.set([]);this.attachmentsLoaded.set(false);this.creating=true;this.resetForm();this.notice.set('');}
 private resetForm(){this.pendingCreation=null;this.pendingCommand=null;this.pendingDraft=null;this.editing.set(null);this.contextIndex='';this.subject='';this.description='';this.category='OTHER';this.fields={};this.text='';this.referenceContextChanged();this.linkedReferences.set([]);this.referencesLoaded.set(false);}
 startEdit(t:IncidentView){this.creating=true;this.editing.set(t);this.subject=t.subject;this.description=t.description;this.impact=t.impact;this.category=t.category;this.fields={...(t.fields||{})};this.notice.set('');this.referenceContextChanged();this.selectedReferences.set(this.referencesLoaded()?this.linkedReferences():[]);}
 private saveEditedDraft(){const t=this.editing();if(!t)return;
  const payload={expectedVersion:t.version,subject:this.subject,description:this.description,impact:this.impact,category:this.category,fields:this.fields,...(this.referencesLoaded()?{references:referencePayload(this.selectedReferences())}:{})};
  if(this.pendingDraft&&JSON.stringify({...this.pendingDraft.input,requestId:undefined})!==JSON.stringify(payload)){this.fail('Édition non confirmée : réessayez le même contenu ou actualisez le dossier.');return;}
  this.pendingDraft??={id:t.id,input:{requestId:crypto.randomUUID(),...payload}};this.loading.set(true);this.error.set('');
  this.api.editDraft(this.acquirer,this.mobile,this.pendingDraft.id,this.pendingDraft.input).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{
   this.pendingDraft=null;this.editing.set(null);this.creating=false;this.ticket.set(r);this.selectAction(r);this.loading.set(false);this.notice.set('Brouillon modifié. Aucun avis envoyé.');this.refresh();this.loadConversation(r.id);
  },error:e=>{this.loading.set(false);this.error.set(e.status===0?'Édition non confirmée. Réessayez le même contenu.':'Édition refusée ou brouillon déjà modifié : actualisez le dossier.');if(e.status>=400&&e.status<500)this.pendingDraft=null;}});
 }
 fieldDefinitions(){return readIncidentFields(this.catalog().find(c=>c.code===this.category)?.fields_json||'{}')||[];}
 categoryLabel(category:IncidentCategory){return category.parent_label?category.parent_label+' / '+category.label:category.label;}
 categoryName(code:string){const category=this.catalog().find(row=>row.code===code);return category?this.categoryLabel(category):code;}
 priorityName(value:string){return ({NORMAL:'Normale',HIGH:'Haute',CRITICAL:'Critique'} as Record<string,string>)[value]||value;}
 hours(value:number|null|undefined){return value==null||!Number.isFinite(value)||value<0?'non disponible':new Intl.NumberFormat('fr-FR',{maximumFractionDigits:1}).format(value/3600)+' h';}
 fieldLabel(key:string,category:string){return readIncidentFields(this.catalog().find(c=>c.code===category)?.fields_json||'{}')?.find(field=>field.key===key)?.label||key;}
 send(draft:boolean){
  if(this.loading())return;if(this.editing()){this.saveEditedDraft();return;}
  if(this.loading())return;const context=this.contexts()[Number(this.contextIndex)]?.context;
  if(this.contextIndex===''||!context||this.subject.trim().length<3||this.description.trim().length<5){this.fail('Choisissez un dossier, puis renseignez l’objet et la description.');return;}
  const payload={context,category:this.category,subject:this.subject,description:this.description,impact:this.impact,fields:this.fields,draft,references:referencePayload(this.selectedReferences())};
  // An uncertain send keeps exactly the same request ID and body. Explicit reset is required before changing it.
  if(this.pendingCreation&&JSON.stringify({...this.pendingCreation,requestId:undefined})!==JSON.stringify(payload)){
   this.fail('Envoi précédent non confirmé. Réessayez le même contenu ou actualisez la liste avant de préparer un autre dépôt.');return;
  }
  this.pendingCreation??={requestId:crypto.randomUUID(),...payload};this.loading.set(true);this.error.set('');
  this.api.create(this.acquirer,this.mobile,this.pendingCreation).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:t=>{
   this.pendingCreation=null;this.creating=false;this.ticket.set(t);this.selectAction(t);this.loading.set(false);this.notice.set(draft?'Brouillon enregistré.':'Dépôt confirmé : '+t.reference);this.refresh();this.loadConversation(t.id);
  },error:e=>{this.loading.set(false);this.error.set(e.status===0?'Envoi non confirmé. Ne créez pas un second dépôt : réessayez le même envoi.':'Déclaration refusée : vérifiez le formulaire, les droits et l’absence de données sensibles.');if(e.status>=400&&e.status<500)this.pendingCreation=null;}});
 }
 act(){const t=this.ticket();if(!t||this.loading()||!t.actions.includes(this.action))return;
  if(this.action==='EDIT_DRAFT'){this.startEdit(t);return;}
  const input={expectedVersion:t.version,text:this.text||null,category:this.action==='LINK'?this.linkKind:this.category,priority:this.priority,
   teamId:this.assignmentTeam||null,assignee:this.assignee||null,relatedId:this.relatedId||null};const key=JSON.stringify([t.id,this.action,input]);
  if(this.pendingCommand&&this.pendingCommand.key!==key){this.fail('Commande précédente non confirmée : actualisez le dossier avant une action différente.');return;}
  this.pendingCommand??={key,input:{requestId:crypto.randomUUID(),...input},action:this.action};this.loading.set(true);this.error.set('');
  this.api.command(this.acquirer,this.mobile,t,this.pendingCommand.action,this.pendingCommand.input).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:r=>{
   this.pendingCommand=null;this.ticket.set(r);this.selectAction(r);this.text='';this.loading.set(false);this.notice.set('Action enregistrée.');this.loadConversation(r.id);this.refresh();
  },error:e=>{this.loading.set(false);this.error.set(e.status===0?'Résultat non confirmé. Réessayez la même action.':'Action refusée ou version modifiée. Actualisez le dossier.');if(e.status>=400&&e.status<500)this.pendingCommand=null;}});
 }
 addFile(event:Event){const t=this.ticket();const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file||!t)return;
  if(this.loading())return;
  const policy=this.attachmentPolicy(),refusal=attachmentRefusal(policy,this.attachmentsLoaded()?this.attachments().length:null,file);
  if(refusal){
   const reasons:Record<string,string>={POLICY_UNAVAILABLE:'Limites des pièces indisponibles : actualisez le dossier avant un ajout.',COUNT_UNAVAILABLE:'La liste des pièces doit être chargée avant un ajout.',DISABLED:'L’ajout de pièces est désactivé pour cet acquéreur.',LIMIT_REACHED:'Le nombre maximal de pièces de ce dossier est atteint.',INVALID_TYPE:'Choisissez un PDF, JPEG ou PNG autorisé.',EMPTY:'Le fichier est vide ou sa taille est inconnue.',TOO_LARGE:'Fichier trop volumineux : maximum '+attachmentSizeLabel(policy?.maxBytes||0)+' par pièce.'};
   input.value='';this.fail(reasons[refusal]);return;
  }
  this.loading.set(true);this.api.upload(this.acquirer,this.mobile,t,file,crypto.randomUUID()).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:a=>{
   input.value='';this.open(t);this.notice.set(a.scanState==='CLEAN'?'Pièce contrôlée.':a.scanState==='REJECTED'?'Pièce rejetée par le contrôle de sécurité. Aucun téléchargement autorisé.':'Pièce reçue ; consultation bloquée tant que le contrôle de sécurité n’est pas validé.');
  },error:()=>this.fail('Ajout non confirmé ou refusé. Actualisez les pièces avant un nouvel envoi.')});
 }
 private invalidateDownload(){this.downloadGeneration++;this.downloading.set(false);}
 download(file:IncidentAttachment){const t=this.ticket();if(!t||file.scanState!=='CLEAN'||this.downloading())return;
  const generation=++this.downloadGeneration,code=this.acquirer;const current=()=>generation===this.downloadGeneration&&code===this.acquirer&&this.ticket()?.id===t.id;
  this.downloading.set(true);this.error.set('');this.notice.set('');
  this.api.download(code,this.mobile,t.id,file.id).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:blob=>{
   if(!current())return;
   exportIncidentAttachment(blob,file,current).then(result=>{if(!current())return;this.notice.set(result==='CANCELLED'?'Enregistrement annulé. Le dossier reste disponible.':result==='SAVED'?'Copie enregistrée.':result==='WEB_STARTED'?'Téléchargement préparé. Vérifiez son enregistrement.':'');}).catch(()=>{if(current())this.error.set('Copie non confirmée ou refusée. Vérifiez le fichier avant une nouvelle demande.');}).finally(()=>{if(current())this.downloading.set(false);});
  },error:()=>{if(current()){this.downloading.set(false);this.error.set('Téléchargement refusé ou pièce non disponible.');}}});}
 label(value:string):string{return ({DRAFT:'Brouillon',OPEN:'Ouvert',IN_PROGRESS:'En cours',WAITING_MERCHANT:'En attente du commerçant',WAITING_THIRD_PARTY:'En attente d’un tiers',RESOLVED:'Résolu',CLOSED:'Clôturé',CANCELLED:'Annulé',SUBMIT:'Envoyer le brouillon',REPLY:'Répondre',INTERNAL_NOTE:'Ajouter une note interne',TAKE:'Prendre en charge',ASSIGN:'Réaffecter',QUALIFY:'Requalifier',WAIT_MERCHANT:'Demander un complément',WAIT_THIRD_PARTY:'Attendre un tiers',RESOLVE:'Proposer une résolution',CONFIRM:'Confirmer personnellement la résolution',CLOSE:'Clôturer avec motif',REOPEN:'Contester et réouvrir',CANCEL:'Annuler avec motif',LINK:'Lier un incident',BIND_MERCHANT:'Rattacher au commerce créé',EDIT_DRAFT:'Modifier la description du brouillon'} as Record<string,string>)[value]||value;}
 private loadAttachmentPolicy(){const request=++this.policyRequest;this.attachmentPolicy.set(null);this.api.attachmentPolicy(this.acquirer,this.mobile).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:value=>{if(request===this.policyRequest)this.attachmentPolicy.set(readAttachmentPolicy(value));},error:()=>{if(request===this.policyRequest)this.attachmentPolicy.set(null);}});}
 attachmentSize(bytes:number){return attachmentSizeLabel(bytes);}
 canAddAttachment(){const policy=this.attachmentPolicy();return !!policy&&this.attachmentsLoaded()&&policy.maxFiles>0&&this.attachments().length<policy.maxFiles;}
 canTakePhoto(){return this.mobile&&!this.loading()&&canCaptureIncidentPhoto(this.attachmentPolicy(),this.attachmentsLoaded()?this.attachments().length:null);}
 private selectAction(ticket:IncidentView){this.action=ticket.actions.includes('REPLY')?'REPLY':ticket.actions[0]||'REPLY';}
 referenceContext(){return this.editing()?.context||(this.contextIndex!==''?this.contexts()[Number(this.contextIndex)]?.context:null);}
 referenceContextChanged(){this.referenceRequest++;this.referenceIndex='';this.referenceOffset=0;this.referenceOptions.set([]);this.selectedReferences.set([]);this.referencesLoading.set(false);this.referenceError.set('');}
 referenceKindChanged(){this.referenceRequest++;this.referenceIndex='';this.referenceOffset=0;this.referenceOptions.set([]);this.referencesLoading.set(false);this.referenceError.set('');}
 loadReferenceOptions(offset=0){const context=this.referenceContext();if(!context||this.editing()&&!this.referencesLoaded())return;
  const request=++this.referenceRequest,kind=this.referenceKind;this.referenceIndex='';this.referenceOffset=offset;this.referencesLoading.set(true);this.referenceError.set('');
  this.api.referenceOptions(this.acquirer,this.mobile,context,kind,offset).pipe(takeUntil(this.cancel),takeUntil(this.destroyed)).subscribe({next:rows=>{if(request!==this.referenceRequest)return;this.referencesLoading.set(false);const safe=readReferenceOptions(rows,kind);this.referenceOptions.set(safe||[]);if(!safe)this.referenceError.set('Liste indisponible. Vous pouvez déclarer sans référence.');},error:()=>{if(request!==this.referenceRequest)return;this.referencesLoading.set(false);this.referenceOptions.set([]);this.referenceError.set('Liste indisponible ou hors périmètre. Vous pouvez déclarer sans référence.');}});
 }
 addReference(){const option=this.referenceOptions()[Number(this.referenceIndex)];if(this.referenceIndex===''||!option||this.selectedReferences().length>=5)return;
  if(!this.selectedReferences().some(r=>r.kind===option.kind&&r.id===option.id))this.selectedReferences.update(rows=>[...rows,option]);this.referenceIndex='';}
 removeReference(index:number){this.selectedReferences.update(rows=>rows.filter((_,i)=>i!==index));}
 private fail(message:string){this.error.set(message);this.loading.set(false);}
}
