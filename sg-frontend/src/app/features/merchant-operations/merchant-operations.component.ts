import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { MerchantActivityService } from '../../core/services/merchant-activity.service';
import { MerchantOperationsService } from '../../core/services/merchant-operations.service';
import { PortalNode, PortalPage } from '../../core/models/merchant-activity.models';
import { OperationCapabilities, OperationKind, OperationDocument, PortalOperation } from '../../core/models/merchant-operations.models';
import {MerchantRequestAssignmentComponent} from './merchant-request-assignment.component';
@Component({selector:'app-merchant-operations',standalone:true,imports:[FormsModule,DatePipe,MerchantRequestAssignmentComponent],
  templateUrl:'./merchant-operations.component.html',styleUrl:'./merchant-operations.component.scss'})
export class MerchantOperationsComponent {
  private readonly api = inject(MerchantOperationsService); private readonly activity = inject(MerchantActivityService);
  private readonly onboarding = inject(MerchantOnboardingService); private readonly destroy = inject(DestroyRef);
  readonly acquirers = signal<{code:string;name:string}[]>([]); readonly nodes = signal<PortalNode[]>([]);
  readonly rows = signal<PortalPage<PortalOperation>|null>(null); readonly detail = signal<PortalOperation|null>(null);
  readonly permissions = signal<OperationCapabilities>({maker:false,checker:false,backoffice:false,subject:''});
  readonly busy = signal(false); readonly actionBusy = signal(false); readonly error = signal(''); readonly notice = signal('');
  readonly creating = signal(false); readonly revisionTarget = signal<PortalOperation|null>(null); acquirer=''; nodeId=''; kind:OperationKind='ADD_TERMINAL';
  label=''; terminalModel=''; connectionType='IP'; currency='MAD'; location=''; contract=''; holder=''; bankCoordinates=''; reason=''; file:File|null=null;
  private generation=0; private detailGeneration=0; private key=crypto.randomUUID();
  constructor(){this.onboarding.allowedAcquirers().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:v=>{
    this.acquirers.set(v);this.acquirer=v[0]?.code||'';this.selectAcquirer();},error:e=>this.error.set(this.message(e))});}
  selectAcquirer():void{++this.generation;this.closeDetail();this.rows.set(null);this.nodes.set([]);this.closeForm();this.busy.set(false);this.error.set('');this.notice.set('');
    if(!this.acquirer){this.error.set('Aucun acquéreur actif autorisé.');return;}this.loadNodes(this.acquirer,0,[]);this.refresh();}
  private loadNodes(code:string,page:number,all:PortalNode[]):void{this.activity.hierarchy(code,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{
    if(code!==this.acquirer)return;const nodes=[...all,...r.content];if(r.number+1<r.totalPages)this.loadNodes(code,page+1,nodes);else{
      this.nodes.set(nodes);this.nodeId=nodes.find(n=>n.active&&(n.nodeType==='OUTLET'||n.nodeType==='PDV'))?.id||nodes[0]?.id||'';this.checkPermissions();}
    },error:e=>{if(code===this.acquirer)this.error.set(this.message(e));}});}
  checkPermissions():void{const code=this.acquirer,id=this.nodeId;this.permissions.set({maker:false,checker:false,backoffice:false,subject:''});
    if(!code||!id)return;this.api.permissions(code,id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:p=>{
      if(code===this.acquirer&&id===this.nodeId)this.permissions.set(p);},error:e=>{if(code===this.acquirer&&id===this.nodeId)this.error.set(this.message(e));}});}
  refresh(page=0):void{if(!this.acquirer||this.busy())return;const generation=++this.generation;this.busy.set(true);this.error.set('');
    this.api.list(this.acquirer,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(generation===this.generation){this.rows.set(r);this.busy.set(false);}},
      error:e=>{if(generation===this.generation){this.rows.set(null);this.busy.set(false);this.error.set(this.message(e));}}});}
  openDetail(row:PortalOperation):void{this.openDetailId(row.id);}
  openDetailId(id:string):void{const generation=++this.detailGeneration,code=this.acquirer;this.detail.set(null);this.reason='';this.file=null;
    this.api.detail(code,id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(code===this.acquirer&&generation===this.detailGeneration)this.detail.set(r);},
      error:e=>{if(code===this.acquirer&&generation===this.detailGeneration)this.error.set(this.message(e));}});}
  closeDetail():void{++this.detailGeneration;this.detail.set(null);this.reason='';this.file=null;}
  openForm():void{this.closeForm();this.creating.set(true);this.closeDetail();this.error.set('');}
  openRevision(row:PortalOperation):void{if(this.actionBusy()||row.state!=='COMPLEMENTS_REQUIRED'||!row.capabilities.maker||!this.responsible(row))return;
    this.closeForm();this.revisionTarget.set(row);this.kind=row.kind;this.nodeId=row.hierarchyNodeId;const d=row.details;
    this.label=d?.label||'';this.terminalModel=d?.terminalModel||'';this.connectionType=d?.connectionType||'IP';this.currency=d?.currency||'MAD';this.location=d?.location||'';
    this.contract=d?.settlementContractReference||'';this.holder=d?.accountHolder||'';this.bankCoordinates='';this.creating.set(true);this.closeDetail();this.checkPermissions();this.error.set('');}
  closeForm():void{this.creating.set(false);this.revisionTarget.set(null);this.bankCoordinates='';this.holder='';this.contract='';this.label='';this.terminalModel='';this.location='';this.key=crypto.randomUUID();}
  create():void{const previous=this.revisionTarget();if(this.actionBusy()||!(previous?previous.capabilities.maker:this.permissions().maker))return;
    if(!this.nodeId){this.error.set('Sélectionnez le périmètre concerné.');return;}
    const payload=this.kind==='ADD_TERMINAL'?{label:this.label,terminalModel:this.terminalModel,connectionType:this.connectionType,currency:this.currency,location:this.location}
      :{settlementContractReference:this.contract,accountHolder:this.holder,bankCoordinates:this.bankCoordinates};
    const command=previous?this.api.revise(this.acquirer,previous,payload,this.key):this.api.create(this.acquirer,this.nodeId,this.kind,payload,this.key);
    this.run(command,previous?'Révision enregistrée ; redéposez les justificatifs et refaites toutes les validations.':'Brouillon enregistré ; déposez les justificatifs avant la soumission.',()=>this.closeForm());}
  requestComplement():void{const r=this.detail();if(r&&this.validReason())this.run(this.api.requestComplement(this.acquirer,r,this.reason.trim()),'Compléments demandés ; aucune modification de l’acquisition n’a été effectuée.');}
  selectFile(event:Event):void{this.file=(event.target as HTMLInputElement).files?.[0]||null;}
  upload():void{const r=this.detail();if(!r||!this.file)return;if(this.file.size>10000000){this.error.set('Pièce limitée à 10 Mo.');return;}
    this.run(this.api.upload(this.acquirer,r.id,this.file),'Justificatif protégé enregistré.');}
  submit():void{const r=this.detail();if(r)this.run(this.api.submit(this.acquirer,r),'Demande soumise ; aucune modification de l’acquisition n’a encore eu lieu.');}
  decide(approve:boolean,backoffice:boolean):void{const r=this.detail();if(!r||!this.validReason())return;
    this.run(this.api.decide(this.acquirer,r,approve,this.reason.trim(),backoffice),approve?'Validation enregistrée ; la confirmation source reste distincte.':'Demande refusée.');}
  reviewDocument(doc:OperationDocument,approve:boolean):void{const r=this.detail();if(!r||!this.validReason())return;
    this.run(this.api.reviewDocument(this.acquirer,r,doc,approve,this.reason.trim()),approve?'Examen documentaire enregistré.':'Justificatif refusé.');}
  execute():void{const r=this.detail();if(r)this.run(this.api.execute(this.acquirer,r),'Résultat d’exécution enregistré ; consultez le statut et la référence source.');}
  sourceStatus():void{const r=this.detail();if(r)this.run(this.api.sourceStatus(this.acquirer,r),'État source interrogé sans renvoyer la demande.');}
  download(doc:OperationDocument):void{const r=this.detail(),code=this.acquirer;if(!r||this.actionBusy())return;this.actionBusy.set(true);
    this.api.document(code,r.id,doc.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:blob=>{this.actionBusy.set(false);if(code!==this.acquirer)return;
      const href=URL.createObjectURL(blob),a=document.createElement('a');a.href=href;a.download='justificatif-'+doc.id+(doc.contentType==='application/pdf'?'.pdf':doc.contentType==='image/png'?'.png':'.jpg');
      a.click();setTimeout(()=>URL.revokeObjectURL(href),1000);},error:e=>{this.actionBusy.set(false);if(code===this.acquirer)this.error.set(this.message(e));}});}
  private run(work:Observable<PortalOperation>,notice:string,after?:()=>void):void{if(this.actionBusy())return;const code=this.acquirer;this.actionBusy.set(true);this.error.set('');this.notice.set('');
    work.pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.actionBusy.set(false);if(code!==this.acquirer)return;after?.();this.notice.set(notice);this.openDetail(r);this.refresh(this.rows()?.number||0);},
      error:e=>{this.actionBusy.set(false);if(code===this.acquirer){this.error.set(this.message(e));if(e.status===409&&this.detail())this.openDetail(this.detail()!);}}});}
  private validReason():boolean{if(!this.reason.trim()||this.reason.length>1000){this.error.set('Une justification de 1 à 1 000 caractères est nécessaire.');return false;}return true;}
  nodeLabel(id:string):string{return this.nodes().find(n=>n.id===id)?.label||'Périmètre autorisé';}
  responsible(row:PortalOperation):boolean{return (row.assignment?.responsibleSubject??row.createdBy)===row.capabilities.subject;}
  independent(row:PortalOperation):boolean{return row.assignment?.independent??row.createdBy!==row.capabilities.subject;}
  checkerResponsible(row:PortalOperation):boolean{return !row.assignment?.supervision?.checkerSubject||row.assignment.supervision.checkerSubject===row.capabilities.subject;}
  assignmentResource(row:PortalOperation):string{return '/api/merchant-portal/v1/acquirers/'+encodeURIComponent(this.acquirer)+'/operations/'+encodeURIComponent(row.id);}
  labelForState(state:string):string{return ({DRAFT:'Brouillon',MERCHANT_REVIEW:'Validation commerçant',BACKOFFICE_REVIEW:'Revue Back-office',APPROVED:'Approuvée, non exécutée',REJECTED:'Refusée',
    EXECUTING:'Exécution en cours',UNCERTAIN:'Résultat incertain — ne pas renvoyer',SOURCE_PENDING:'En attente de confirmation source',CONFIRMED:'Confirmée par l’acquisition',SOURCE_REJECTED:'Refusée par l’acquisition',
    COMPLEMENTS_REQUIRED:'Compléments demandés',SUPERSEDED:'Remplacée par une révision'} as Record<string,string>)[state]||'État inconnu';}
  private message(e:HttpErrorResponse):string{if(e.status===403)return 'Vous n’êtes pas autorisé à effectuer cette action sur ce périmètre.';
    return typeof e.error?.error==='string'?e.error.error.slice(0,500):'L’action n’a pas pu aboutir. Actualisez le statut avant de réessayer.';}
}
