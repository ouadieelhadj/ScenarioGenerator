import {Component,DestroyRef,inject,signal} from '@angular/core';
import {DatePipe} from '@angular/common';import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MerchantOnboardingService} from '../../core/services/merchant-onboarding.service';
import {MerchantHierarchyService,PortalHierarchyIdentity,PortalHierarchyPermissions,PortalScopeGrant,PortalScopeRole} from '../../core/services/merchant-hierarchy.service';
import {PortalNode,PortalPage} from '../../core/models/merchant-activity.models';
import {PersonalMerchantAssociationService} from '../../core/services/personal-merchant-association.service';
@Component({selector:'app-merchant-hierarchy',standalone:true,imports:[FormsModule,DatePipe],templateUrl:'./merchant-hierarchy.component.html',styleUrl:'./merchant-hierarchy.component.scss'})
export class MerchantHierarchyComponent{
  private readonly api=inject(MerchantHierarchyService);private readonly onboarding=inject(MerchantOnboardingService);private readonly destroy=inject(DestroyRef);
  private readonly associations=inject(PersonalMerchantAssociationService);
  personalLogin='';personalRole:PortalScopeRole='VIEWER';private personalCommand='';
  readonly acquirers=signal<{code:string;name:string}[]>([]);readonly nodes=signal<PortalPage<PortalNode>|null>(null);
  readonly selected=signal<PortalNode|null>(null);readonly permissions=signal<PortalHierarchyPermissions|null>(null);
  readonly grants=signal<PortalPage<PortalScopeGrant>|null>(null);readonly identity=signal<PortalHierarchyIdentity|null>(null);readonly revokeTarget=signal<PortalScopeGrant|null>(null);
  readonly busy=signal(false);readonly detailBusy=signal(false);readonly actionBusy=signal(false);readonly error=signal('');readonly notice=signal('');
  acquirer='';login='';role:PortalScopeRole='VIEWER';private generation=0;private detailGeneration=0;private identityGeneration=0;
  constructor(){this.onboarding.allowedAcquirers().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:a=>{this.acquirers.set(a);this.acquirer=a[0]?.code||'';this.selectAcquirer();},error:e=>this.error.set(this.message(e))});}
  selectAcquirer():void{++this.generation;this.closeDetail();this.nodes.set(null);this.busy.set(false);this.notice.set('');this.error.set('');if(this.acquirer)this.refresh();else this.error.set('Aucun acquéreur actif autorisé.');}
  refresh(page=0):void{if(this.busy()||this.actionBusy()||!this.acquirer)return;const gen=++this.generation;this.busy.set(true);this.error.set('');
    this.api.nodes(this.acquirer,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(gen===this.generation){this.nodes.set(r);this.busy.set(false);}},error:e=>{if(gen===this.generation){this.nodes.set(null);this.busy.set(false);this.error.set(this.message(e));}}});}
  open(node:PortalNode):void{if(this.actionBusy())return;this.closeDetail();this.selected.set(node);this.detailBusy.set(true);const code=this.acquirer,gen=this.detailGeneration;
    this.api.permissions(code,node.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:p=>{if(this.current(code,gen)){this.permissions.set(p);this.detailBusy.set(false);if(p.manageGrants)this.loadGrants();}},error:e=>{if(this.current(code,gen)){this.detailBusy.set(false);this.error.set(this.message(e));}}});}
  closeDetail():void{++this.detailGeneration;++this.identityGeneration;this.selected.set(null);this.permissions.set(null);this.grants.set(null);this.identity.set(null);this.revokeTarget.set(null);this.detailBusy.set(false);this.login='';this.role='VIEWER';this.personalLogin='';this.personalRole='VIEWER';this.personalCommand='';}
  personalBindingChanged():void{this.personalCommand='';}
  requestPersonalAssociation():void{
    const node=this.selected();if(!node||!this.permissions()?.manageGrants||this.actionBusy())return;
    if(!this.personalLogin.trim()||this.personalLogin.length>96){this.error.set('Identifiant personnel requis (96 caractères maximum).');return;}
    if(!this.personalCommand)this.personalCommand=crypto.randomUUID();
    const code=this.acquirer,gen=this.detailGeneration;this.actionBusy.set(true);this.error.set('');
    this.associations.request(code,node.id,this.personalCommand,this.personalLogin.trim(),this.personalRole).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{this.actionBusy.set(false);if(this.current(code,gen)){this.notice.set('Demande enregistrée. La personne doit l’accepter depuis Mes rattachements. Aucun nouveau compte ni e-mail n’a été créé ou envoyé.');this.personalCommand='';this.personalLogin='';}},
      error:e=>{this.actionBusy.set(false);if(this.current(code,gen))this.error.set(this.message(e));},
    });
  }
  loadGrants(page=0):void{const node=this.selected();if(!node||!this.permissions()?.manageGrants)return;const code=this.acquirer,gen=this.detailGeneration;this.detailBusy.set(true);this.error.set('');
    this.api.grants(code,node.id,page).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(this.current(code,gen)){this.grants.set(r);this.detailBusy.set(false);}},error:e=>{if(this.current(code,gen)){this.grants.set(null);this.detailBusy.set(false);this.error.set(this.message(e));}}});}
  identityChanged():void{++this.identityGeneration;this.identity.set(null);}
  resolveIdentity():void{const node=this.selected();if(!node||!this.permissions()?.manageGrants||this.actionBusy())return;
    if(!this.login.trim()||this.login.length>96){this.error.set('Une connexion utilisateur de 1 à 96 caractères est requise.');return;}
    const code=this.acquirer,gen=this.detailGeneration,identityGen=++this.identityGeneration;this.identity.set(null);this.actionBusy.set(true);this.error.set('');
    this.api.identity(code,node.id,this.login.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:i=>{this.actionBusy.set(false);if(this.current(code,gen)&&identityGen===this.identityGeneration)this.identity.set(i);},error:e=>{this.actionBusy.set(false);if(this.current(code,gen)&&identityGen===this.identityGeneration)this.error.set(this.message(e));}});}
  grant():void{const node=this.selected(),identity=this.identity();if(!node||!identity||!this.permissions()?.manageGrants||this.actionBusy())return;const code=this.acquirer,gen=this.detailGeneration;
    this.actionBusy.set(true);this.error.set('');this.api.grant(code,node.id,identity,this.role).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.actionBusy.set(false);if(this.current(code,gen)){this.notice.set('Habilitation enregistrée dans le portail. Aucun droit Keycloak ou acquisition n’a été modifié.');this.open(node);}},error:e=>{this.actionBusy.set(false);if(this.current(code,gen))this.error.set(this.message(e));}});}
  askRevoke(grant:PortalScopeGrant):void{if(this.permissions()?.manageGrants&&grant.active&&!this.actionBusy())this.revokeTarget.set(grant);}
  revoke():void{const grant=this.revokeTarget(),node=this.selected();if(!node||!grant||!this.permissions()?.manageGrants||this.actionBusy())return;const code=this.acquirer,gen=this.detailGeneration;this.actionBusy.set(true);this.error.set('');
    this.api.revoke(code,grant).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.actionBusy.set(false);if(this.current(code,gen)){this.notice.set('Habilitation désactivée ; la trace et les autres droits sont conservés.');this.open(node);}},error:e=>{this.actionBusy.set(false);if(this.current(code,gen))this.error.set(this.message(e));}});}
  label(type:string):string{return ({MERCHANT:'Commerçant',CHAIN_GROUP:'Groupe de chaînes',CHAIN:'Chaîne',OUTLET:'Outlet',PDV:'Point de vente',TERMINAL:'Terminal',ADMIN:'Administrateur',MAKER:'Préparateur (Maker)',CHECKER:'Valideur (Checker)',VIEWER:'Consultation',ACCOUNTING:'Comptabilité'} as Record<string,string>)[type]||'Valeur à qualifier';}
  private current(code:string,gen:number):boolean{return code===this.acquirer&&gen===this.detailGeneration;}
  private message(e:HttpErrorResponse):string{if(e.status===403)return 'Accès interdit : niveau, utilisateur ou action hors de votre périmètre autorisé.';
    return typeof e.error?.error==='string'?e.error.error.slice(0,500):'Consultation ou action indisponible. Actualisez le statut avant de réessayer.';}
}
