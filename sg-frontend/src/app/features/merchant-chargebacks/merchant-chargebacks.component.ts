import {Component,DestroyRef,inject,signal} from '@angular/core';import {DatePipe} from '@angular/common';import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';import {takeUntilDestroyed} from '@angular/core/rxjs-interop';import {Observable} from 'rxjs';import {RouterLink,ActivatedRoute} from '@angular/router';
import {MerchantChargebacksService} from '../../core/services/merchant-chargebacks.service';import {MerchantOnboardingService} from '../../core/services/merchant-onboarding.service';
import {PortalPage,portalMoney} from '../../core/models/merchant-activity.models';import {PortalChargeback,ChargebackResponse,ChargebackDocument,ChargebackDelivery} from '../../core/models/merchant-chargebacks.models';
import {MerchantRequestAssignmentComponent} from '../merchant-operations/merchant-request-assignment.component';
@Component({selector:'app-merchant-chargebacks',standalone:true,imports:[FormsModule,DatePipe,RouterLink,MerchantRequestAssignmentComponent],templateUrl:'./merchant-chargebacks.component.html',styleUrl:'./merchant-chargebacks.component.scss'})
export class MerchantChargebacksComponent {
  private readonly api=inject(MerchantChargebacksService);private readonly onboarding=inject(MerchantOnboardingService);private readonly destroy=inject(DestroyRef);
  private readonly route=inject(ActivatedRoute);
  readonly acquirers=signal<{code:string;name:string}[]>([]);readonly rows=signal<PortalPage<PortalChargeback>|null>(null);readonly detail=signal<PortalChargeback|null>(null);
  readonly busy=signal(false);readonly actionBusy=signal(false);readonly error=signal('');readonly notice=signal('');readonly money=portalMoney;
  readonly mailTracking=signal<ChargebackDelivery[]|null>(null);readonly mailBusy=signal(false);readonly retryTarget=signal<ChargebackDelivery|null>(null);relayEvidence='';
  acquirer='';state='';from=new Date(Date.now()-30*86400000).toISOString().slice(0,10);to=new Date().toISOString().slice(0,10);
  response:'ACCEPT'|'CONTEST'='CONTEST';comment='';reason='';file:File|null=null;private key=crypto.randomUUID();private generation=0;private detailGeneration=0;
  constructor(){this.onboarding.allowedAcquirers().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:a=>{this.acquirers.set(a);const requested=this.route.snapshot.queryParamMap.get('acquirer');this.acquirer=a.find(v=>v.code===requested)?.code||a[0]?.code||'';this.selectAcquirer();},error:e=>this.error.set(this.message(e))});}
  selectAcquirer():void{++this.generation;this.closeDetail();this.rows.set(null);this.busy.set(false);this.error.set('');this.notice.set('');if(this.acquirer){this.refresh();const id=this.route.snapshot.queryParamMap.get('case');
    if(this.route.snapshot.queryParamMap.get('acquirer')===this.acquirer&&id&&/^[0-9a-f-]{36}$/i.test(id))this.openDetailId(id);}else this.error.set('Aucun acquéreur actif autorisé.');}
  refresh(page=0):void{if(this.busy()||!this.acquirer)return;const first=new Date(this.from+'T00:00:00Z'),last=new Date(this.to+'T00:00:00Z');last.setUTCDate(last.getUTCDate()+1);
    if(!Number.isFinite(first.getTime())||!Number.isFinite(last.getTime())||last<=first||last.getTime()-first.getTime()>366*86400000){this.error.set('Choisissez une période valide d’un an maximum.');return;}
    const generation=++this.generation;this.busy.set(true);this.error.set('');this.api.list(this.acquirer,first.toISOString(),last.toISOString(),this.state,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:r=>{if(generation===this.generation){this.rows.set(r);this.busy.set(false);}},error:e=>{if(generation===this.generation){this.rows.set(null);this.busy.set(false);this.error.set(this.message(e));}}});}
  openDetail(row:PortalChargeback):void{this.openDetailId(row.id);}
  openDetailId(id:string):void{const code=this.acquirer,generation=++this.detailGeneration;this.detail.set(null);this.mailTracking.set(null);this.mailBusy.set(false);this.cancelRetry();this.comment='';this.reason='';this.file=null;this.key=crypto.randomUUID();
    this.api.detail(code,id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(code===this.acquirer&&generation===this.detailGeneration)this.detail.set(r);},error:e=>{if(code===this.acquirer&&generation===this.detailGeneration)this.error.set(this.message(e));}});}
  closeDetail():void{++this.detailGeneration;this.detail.set(null);this.mailTracking.set(null);this.mailBusy.set(false);this.cancelRetry();this.comment='';this.reason='';this.file=null;}
  responsible(response:ChargebackResponse):boolean{return (response.assignment?.responsibleSubject??response.createdBy)===this.detail()?.capabilities.subject;}
  independent(response:ChargebackResponse):boolean{return response.assignment?.independent??response.createdBy!==this.detail()?.capabilities.subject;}
  checkerResponsible(response:ChargebackResponse):boolean{return !response.assignment?.supervision?.checkerSubject||response.assignment.supervision.checkerSubject===this.detail()?.capabilities.subject;}
  assignmentResource(response:ChargebackResponse):string{return '/api/merchant-portal/v1/acquirers/'+encodeURIComponent(this.acquirer)+'/chargebacks/'+encodeURIComponent(this.detail()!.id)+'/responses/'+encodeURIComponent(response.id);}
  loadMailTracking():void{const row=this.detail(),code=this.acquirer,generation=this.detailGeneration;if(!row?.capabilities.backoffice||this.mailBusy())return;this.mailBusy.set(true);this.error.set('');
    this.api.notifications(code,row.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(code===this.acquirer&&generation===this.detailGeneration){this.mailTracking.set(r);this.mailBusy.set(false);}},error:e=>{if(code===this.acquirer&&generation===this.detailGeneration){this.mailBusy.set(false);this.error.set(this.message(e));}}});}
  prepareRetry(delivery:ChargebackDelivery):void{if(this.detail()?.capabilities.backoffice&&delivery.state==='UNCERTAIN'){this.retryTarget.set(delivery);this.relayEvidence='';}}
  cancelRetry():void{this.retryTarget.set(null);this.relayEvidence='';}
  confirmRetry():void{const row=this.detail(),delivery=this.retryTarget(),code=this.acquirer,generation=this.detailGeneration;if(!row?.capabilities.backoffice||!delivery||this.mailBusy())return;
    if(!this.relayEvidence.trim()||this.relayEvidence.length>1000){this.error.set('Une preuve de non-acceptation de 1 à 1 000 caractères est requise.');return;}
    this.mailBusy.set(true);this.error.set('');this.api.retryNotification(code,row.id,delivery,this.relayEvidence.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:r=>{if(code===this.acquirer&&generation===this.detailGeneration){this.mailTracking.set(r);this.mailBusy.set(false);this.cancelRetry();this.notice.set('Nouvelle tentative autorisée après contrôle du relais ; aucun e-mail déclaré reçu.');}},
      error:e=>{if(code===this.acquirer&&generation===this.detailGeneration){this.mailBusy.set(false);this.error.set(this.message(e));}}});}
  mailLabel(state:string):string{return ({PENDING:'En attente de livraison',PROCESSING:'Traitement en cours',SENDING:'Tentative réservée',ACCEPTED:'Accepté par le serveur mail — réception non confirmée',UNCERTAIN:'Résultat incertain — contrôle du relais requis',CANCELLED:'Annulé',PARTIAL:'Acceptation partielle — destinataires annulés'} as Record<string,string>)[state]||'État à vérifier';}
  mailBlock(reason:string):string{return ({NO_AUTHORIZED_RECIPIENT:'Aucun destinataire actif habilité : vérifier les comptes et leurs périmètres.',INVALID_RECIPIENT:'Adresse de notification invalide : faire corriger le compte concerné.',NODE_NOT_ACTIVE:'Périmètre commercial inactif : vérifier la hiérarchie source.'} as Record<string,string>)[reason]||'Configuration à vérifier par le Back-office.';}
  canRespond():boolean{const r=this.detail();return !!r&&r.capabilities.maker&&r.data.canonicalStatus==='OPEN'&&Date.now()<Date.parse(r.data.deadlineAt)
    &&!r.responses.some(s=>['DRAFT','MERCHANT_REVIEW','BACKOFFICE_REVIEW','READY_FOR_EXTERNAL_BO'].includes(s.state));}
  createResponse():void{const r=this.detail();if(!r||!this.canRespond())return;if(!this.comment.trim()||this.comment.length>1000){this.error.set('Un commentaire de 1 à 1 000 caractères est requis.');return;}
    this.run(this.api.createResponse(this.acquirer,r.id,this.response,this.comment.trim(),this.key),'Réponse en brouillon ; le statut officiel du litige reste inchangé.');}
  selectFile(e:Event):void{this.file=(e.target as HTMLInputElement).files?.[0]||null;}
  upload(response:ChargebackResponse):void{const r=this.detail();if(!r||!this.file)return;if(this.file.size>10000000){this.error.set('Pièce limitée à 10 Mo.');return;}
    this.run(this.api.upload(this.acquirer,r.id,response,this.file),'Justificatif protégé enregistré.');}
  submit(response:ChargebackResponse):void{const r=this.detail();if(r)this.run(this.api.submit(this.acquirer,r.id,response),'Réponse transmise pour validation indépendante.');}
  merchantDecision(response:ChargebackResponse,approve:boolean):void{const r=this.detail();if(r&&this.validReason())this.run(this.api.merchantDecision(this.acquirer,r.id,response,approve,this.reason.trim()),approve?'Réponse validée côté commerçant, en attente du Back-office.':'Réponse refusée côté commerçant.');}
  backofficeDecision(response:ChargebackResponse,decision:string):void{const r=this.detail();if(r&&this.validReason())this.run(this.api.backofficeDecision(this.acquirer,r.id,response,decision,this.reason.trim()),decision==='VALIDATE'?'Réponse préparée pour le Back-office externe. Aucun appel réseau effectué.':decision==='COMPLEMENT'?'Compléments demandés : créez une nouvelle version de réponse.':'Réponse refusée par le Back-office.');}
  withdraw(response:ChargebackResponse):void{const r=this.detail();if(r)this.run(this.api.withdraw(this.acquirer,r.id,response),'Version obsolète retirée ; son historique est conservé.');}
  actionable():boolean{const r=this.detail();return !!r&&r.data.canonicalStatus==='OPEN'&&Date.now()<Date.parse(r.data.deadlineAt);}
  invalidate(response:ChargebackResponse):void{const r=this.detail();if(r&&this.validReason())this.run(this.api.invalidate(this.acquirer,r.id,response,this.reason.trim()),'Remise locale invalidée. Les décisions antérieures et le statut officiel sont conservés.');}
  download(response:ChargebackResponse,doc:ChargebackDocument):void{const r=this.detail(),code=this.acquirer;if(!r||this.actionBusy())return;this.actionBusy.set(true);this.api.download(code,r.id,response,doc).pipe(takeUntilDestroyed(this.destroy)).subscribe({
    next:blob=>{this.actionBusy.set(false);if(code!==this.acquirer)return;const href=URL.createObjectURL(blob),a=document.createElement('a');a.href=href;a.download='justificatif-'+doc.id+(doc.contentType==='application/pdf'?'.pdf':doc.contentType==='image/png'?'.png':'.jpg');a.click();setTimeout(()=>URL.revokeObjectURL(href),1000);},
    error:e=>{this.actionBusy.set(false);if(code===this.acquirer)this.error.set(this.message(e));}});}
  private run(work:Observable<PortalChargeback>,notice:string):void{if(this.actionBusy())return;const code=this.acquirer;this.actionBusy.set(true);this.error.set('');this.notice.set('');work.pipe(takeUntilDestroyed(this.destroy)).subscribe({
    next:r=>{this.actionBusy.set(false);if(code===this.acquirer){this.notice.set(notice);this.detail.set(r);this.comment='';this.reason='';this.file=null;this.key=crypto.randomUUID();this.refresh(this.rows()?.number||0);}},
    error:e=>{this.actionBusy.set(false);if(code===this.acquirer)this.error.set(this.message(e));}});}
  private validReason():boolean{if(!this.reason.trim()||this.reason.length>1000){this.error.set('Une justification de 1 à 1 000 caractères est requise.');return false;}return true;}
  label(state:string):string{return ({OPEN:'Ouvert',CLOSED:'Clôturé par la source',CANCELLED:'Annulé par la source',UNKNOWN:'Statut source à qualifier',DRAFT:'Brouillon',MERCHANT_REVIEW:'Validation commerçant',BACKOFFICE_REVIEW:'Revue Back-office',
    READY_FOR_EXTERNAL_BO:'Prêt pour traitement externe Back-office',COMPLEMENT_REQUIRED:'Compléments requis',REJECTED:'Réponse refusée',WITHDRAWN:'Version obsolète retirée',BO_INVALIDATED:'Remise obsolète invalidée par le Back-office'} as Record<string,string>)[state]||'État inconnu';}
  private message(e:HttpErrorResponse):string{if(e.status===403)return 'Accès interdit à ce dossier ou à cette action.';return typeof e.error?.error==='string'?e.error.error.slice(0,500):'Impossible de terminer cette action. Consultez le statut avant un rejeu.';}
}
