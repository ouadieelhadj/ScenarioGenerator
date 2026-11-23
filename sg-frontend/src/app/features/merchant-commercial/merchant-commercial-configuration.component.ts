import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, Subject, forkJoin, takeUntil } from 'rxjs';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { MerchantCommercialService } from '../../core/services/merchant-commercial.service';
import { AllowedAcquirer } from '../../core/models/merchant-onboarding.models';
import { CommercialConfiguration, CommercialPreview, CommercialReference, CommercialReferenceKind, CommercialRule, CommercialTeam, CommercialTeamMember } from '../../core/models/merchant-commercial.models';

type Section='references'|'teams'|'members'|'rules'|'fallback'|'preview';
const codePattern=/^[A-Z0-9][A-Z0-9_-]{0,63}$/;
@Component({selector:'app-merchant-commercial-configuration',standalone:true,imports:[ReactiveFormsModule],
 template:`
  <section class="configuration"><h1>Administration commerciale</h1>
    <p>Référentiels, équipes et règles sont propres à l’acquéreur sélectionné. Une simulation ne crée ni dossier ni affectation.</p>
    <form [formGroup]="owner"><label>Acquéreur autorisé<select formControlName="code" (change)="changeOwner()" [attr.disabled]="busy()?true:null"><option value="">Choisir un acquéreur</option>@for(a of acquirers();track a.code){<option [value]="a.code">{{a.name}}</option>}</select></label></form>
    @if(loading()){<p role="status">Chargement de la configuration…</p>}
    @if(error()){<p role="alert" class="error">{{error()}}</p>}
    @if(message()){<p role="status" class="success">{{message()}}</p>}
    @if(configuration();as config){
      <nav aria-label="Rubriques commerciales">@for(tab of sections;track tab.id){<button type="button" [attr.aria-pressed]="section()===tab.id" (click)="selectSection(tab.id)" [disabled]="busy()">{{tab.label}}</button>}</nav>
      @switch(section()){
        @case('references'){
          <h2>Référentiels internes</h2><form [formGroup]="reference" (ngSubmit)="saveReference()">
            <label>Type de référentiel<select formControlName="kind" (change)="newReference()"><option value="REGION">Régions</option><option value="CITY">Villes</option><option value="ACTIVITY">Activités</option></select></label>
            <label>Code interne<input formControlName="code" maxlength="64" [readOnly]="referenceVersion()>=0" /></label><label>Libellé<input formControlName="label" maxlength="160" /></label>
            @if(reference.controls.kind.value==='CITY'){<label>Région de la ville<select formControlName="extra"><option value="">Choisir une région active</option>@for(r of regions();track r.code){<option [value]="r.code" [disabled]="!r.active">{{r.label}}</option>}</select></label>}
            @if(reference.controls.kind.value==='ACTIVITY'){<label>MCC explicite, facultatif<input formControlName="extra" maxlength="4" /></label>}
            <label class="check"><input type="checkbox" formControlName="active" />Actif</label>
            <button type="submit" [disabled]="busy()||reference.invalid||!validExtra()">Enregistrer le référentiel</button><button type="button" (click)="newReference()" [disabled]="busy()">Nouveau référentiel</button>
          </form><div class="scroll"><table><thead><tr><th>Code</th><th>Libellé</th><th>Parent / MCC</th><th>État</th><th>Version</th><th>Action</th></tr></thead><tbody>@for(r of referenceRows();track r.code){<tr><td>{{r.code}}</td><td>{{r.label}}</td><td>{{r.extra||'—'}}</td><td>{{r.active?'Actif':'Inactif'}}</td><td>{{r.version}}</td><td><button type="button" (click)="editReference(r)" [disabled]="busy()">Modifier {{r.code}}</button></td></tr>}</tbody></table></div>
        }
        @case('teams'){
          <h2>Équipes commerciales</h2><form [formGroup]="team" (ngSubmit)="saveTeam()"><label>Code équipe<input formControlName="code" maxlength="64" /></label><label>Libellé équipe<input formControlName="label" maxlength="160" /></label><label class="check"><input type="checkbox" formControlName="active" />Équipe active</label><button type="submit" [disabled]="busy()||team.invalid">Enregistrer l’équipe</button><button type="button" (click)="newTeam()" [disabled]="busy()">Nouvelle équipe</button></form>
          <div class="scroll"><table><thead><tr><th>Code</th><th>Équipe</th><th>État</th><th>Version</th><th>Action</th></tr></thead><tbody>@for(t of config.teams;track t.id){<tr><td>{{t.code}}</td><td>{{t.label}}</td><td>{{t.active?'Active':'Inactive'}}</td><td>{{t.version}}</td><td><button type="button" (click)="editTeam(t)" [disabled]="busy()">Modifier {{t.code}}</button></td></tr>}</tbody></table></div>
        }
        @case('members'){
          <h2>Membres et habilitations des équipes</h2><p>Le rôle Chef ne donne pas implicitement le droit de revoir un dossier. Les identités proviennent de cet acquéreur.</p>
          <form [formGroup]="member" (ngSubmit)="saveMember()"><label>Équipe du membre<select formControlName="teamId"><option value="">Choisir une équipe</option>@for(t of config.teams;track t.id){<option [value]="t.id" [disabled]="!t.active">{{t.label}}</option>}</select></label><label>Identité commerciale<select formControlName="userId" [attr.disabled]="memberVersion()>=0?true:null"><option value="">Choisir une identité</option>@for(u of config.users;track u.id){<option [value]="u.id" [disabled]="!u.active">{{u.login}}</option>}</select></label>
            <label>Rôle dans l’équipe<select formControlName="teamRole"><option value="FIELD">Commercial terrain</option><option value="LEAD">Chef d’équipe</option></select></label><label class="check"><input type="checkbox" formControlName="canReview" />Autoriser la revue des dossiers affectés</label><label class="check"><input type="checkbox" formControlName="active" />Membre actif</label><button type="submit" [disabled]="busy()||member.invalid">Enregistrer le membre</button><button type="button" (click)="newMember()" [disabled]="busy()">Nouveau membre</button></form>
          <div class="scroll"><table><thead><tr><th>Identité</th><th>Équipe</th><th>Rôle</th><th>Revue</th><th>État</th><th>Action</th></tr></thead><tbody>@for(m of config.members;track m.id){<tr><td>{{userLabel(m.userId)}}</td><td>{{teamLabel(m.teamId)}}</td><td>{{m.teamRole==='LEAD'?'Chef':'Terrain'}}</td><td>{{m.canReview?'Autorisée':'Non autorisée'}}</td><td>{{m.active?'Actif':'Inactif'}}</td><td><button type="button" (click)="editMember(m)" [disabled]="busy()">Modifier {{userLabel(m.userId)}}</button></td></tr>}</tbody></table></div>
        }
        @case('rules'){
          <h2>Règles d’affectation</h2><p>La priorité numérique la plus petite est examinée en premier. Deux règles actives de même priorité ne peuvent pas se chevaucher.</p>
          <form [formGroup]="rule" (ngSubmit)="saveRule()"><label>Code règle<input formControlName="code" maxlength="64" /></label><label>Priorité numérique<input type="number" formControlName="priority" min="0" max="2147483647" step="1" /></label>
            <label>Région de la règle<select formControlName="region" (change)="rule.controls.city.setValue('')"><option value="">Toutes les régions</option>@for(r of regions();track r.code){<option [value]="r.code" [disabled]="!r.active">{{r.label}}</option>}</select></label>
            <label>Ville de la règle<select formControlName="city"><option value="">Toutes les villes</option>@for(c of ruleCities();track c.code){<option [value]="c.code" [disabled]="!c.active">{{c.label}}</option>}</select></label>
            <label>Activité de la règle<select formControlName="activity"><option value="">Toutes les activités</option>@for(a of activities();track a.code){<option [value]="a.code" [disabled]="!a.active">{{a.label}}</option>}</select></label>
            <label>Responsable cible<select formControlName="targetMemberId"><option value="">Choisir un responsable</option>@for(m of config.members;track m.id){<option [value]="m.id" [disabled]="!available(m)">{{userLabel(m.userId)}} — {{teamLabel(m.teamId)}}</option>}</select></label><label class="check"><input type="checkbox" formControlName="active" />Règle active</label><button type="submit" [disabled]="busy()||rule.invalid">Enregistrer la règle</button><button type="button" (click)="newRule()" [disabled]="busy()">Nouvelle règle</button></form>
          <div class="scroll"><table><thead><tr><th>Code</th><th>Priorité</th><th>Critères</th><th>Responsable</th><th>État</th><th>Version</th><th>Action</th></tr></thead><tbody>@for(r of config.rules;track r.id){<tr><td>{{config.ruleCodes[r.id]}}</td><td>{{r.priority}}</td><td>{{r.region||'Toutes régions'}} / {{r.city||'Toutes villes'}} / {{r.activity||'Toutes activités'}}</td><td>{{memberLabel(r.target)}}</td><td>{{r.active?'Active':'Inactive'}}</td><td>{{r.version}}</td><td><button type="button" (click)="editRule(r)" [disabled]="busy()">Modifier {{config.ruleCodes[r.id]}}</button></td></tr>}</tbody></table></div>
        }
        @case('fallback'){
          <h2>Responsable de secours</h2><p>Sans secours actif, les dossiers sans cible passent en exception visible ; aucun transfert direct au KYC n’est effectué.</p>
          <form [formGroup]="fallback" (ngSubmit)="saveFallback()"><label>Secours explicite<select formControlName="memberId"><option value="">Aucun secours</option>@for(m of config.members;track m.id){<option [value]="m.id" [disabled]="!available(m)">{{userLabel(m.userId)}} — {{teamLabel(m.teamId)}}</option>}</select></label><button type="submit" [disabled]="busy()">Enregistrer le secours</button></form>
          <p>Secours enregistré : {{config.fallback?.memberId?memberLabel(config.fallback!.memberId!):'Aucun'}}. Version : {{config.fallback?.version ?? 'Non configurée'}}.</p>
        }
        @case('preview'){
          <h2>Simulation de l’affectation</h2><p>Aucun dossier réel n’est créé ou modifié par cette vérification.</p><form [formGroup]="preview" (ngSubmit)="simulate()"><label>Région à vérifier<select formControlName="region" (change)="preview.controls.city.setValue('')"><option value="">Choisir une région</option>@for(r of regions();track r.code){<option [value]="r.code" [disabled]="!r.active">{{r.label}}</option>}</select></label><label>Ville à vérifier<select formControlName="city"><option value="">Choisir une ville</option>@for(c of previewCities();track c.code){<option [value]="c.code" [disabled]="!c.active">{{c.label}}</option>}</select></label><label>Activité à vérifier<select formControlName="activity"><option value="">Choisir une activité</option>@for(a of activities();track a.code){<option [value]="a.code" [disabled]="!a.active">{{a.label}}</option>}</select></label><button type="submit" [disabled]="busy()||preview.invalid">Vérifier l’affectation</button></form>
          @if(previewResult();as result){<dl><dt>Résultat</dt><dd>{{routeLabel(result.route)}}</dd><dt>Responsable</dt><dd>{{result.target?memberLabel(result.target.id):'Aucun'}}</dd><dt>Motif</dt><dd>{{reasonLabel(result.reason)}}</dd><dt>Règle</dt><dd>{{result.rule?config.ruleCodes[result.rule.id]:'Sans règle correspondante'}}</dd></dl>}
        }
      }
    }
  </section>`,
 styles:[`.configuration{padding:20px;color:var(--sg-text-primary,#213547)}nav{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0}form{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;align-items:end;margin:15px 0}label{display:grid;gap:6px}.check{display:flex;align-items:center}input,select,button{padding:10px;border:1px solid #c5cfda;border-radius:8px}button{cursor:pointer}button:disabled{opacity:.55;cursor:default}button[aria-pressed=true]{background:#dcedff}.scroll{overflow-x:auto}table{width:100%;border-collapse:collapse}td,th{padding:10px;border-bottom:1px solid #d3dce5;text-align:left}dl{display:grid;grid-template-columns:160px 1fr;gap:10px}.error{color:#b42318}.success{color:#126537}`]
})
export class MerchantCommercialConfigurationComponent implements OnInit,OnDestroy {
  private readonly onboarding=inject(MerchantOnboardingService);private readonly service=inject(MerchantCommercialService);private readonly fb=inject(FormBuilder);
  private readonly destroy=new Subject<void>();private readonly cancel=new Subject<void>();private entityId='';
  readonly sections:Array<{id:Section;label:string}>=[{id:'references',label:'Référentiels'},{id:'teams',label:'Équipes'},{id:'members',label:'Membres et droits'},{id:'rules',label:'Règles'},{id:'fallback',label:'Secours'},{id:'preview',label:'Vérifier une affectation'}];
  readonly section=signal<Section>('references');readonly acquirers=signal<AllowedAcquirer[]>([]);readonly configuration=signal<CommercialConfiguration|null>(null);
  readonly regions=signal<CommercialReference[]>([]);readonly cities=signal<CommercialReference[]>([]);readonly activities=signal<CommercialReference[]>([]);
  readonly loading=signal(false);readonly busy=signal(false);readonly error=signal('');readonly message=signal('');readonly previewResult=signal<CommercialPreview|null>(null);
  readonly referenceVersion=signal(-1);readonly teamVersion=signal(-1);readonly memberVersion=signal(-1);readonly ruleVersion=signal(-1);
  readonly owner=this.fb.nonNullable.group({code:['']});
  readonly reference=this.fb.nonNullable.group({kind:['REGION' as CommercialReferenceKind],code:['',[Validators.required,Validators.pattern(codePattern)]],label:['',[Validators.required,Validators.maxLength(160)]],extra:[''],active:[true]});
  readonly team=this.fb.nonNullable.group({code:['',[Validators.required,Validators.pattern(codePattern)]],label:['',[Validators.required,Validators.maxLength(160)]],active:[true]});
  readonly member=this.fb.nonNullable.group({teamId:['',Validators.required],userId:['',Validators.required],teamRole:['FIELD' as 'FIELD'|'LEAD'],canReview:[false],active:[true]});
  readonly rule=this.fb.nonNullable.group({code:['',[Validators.required,Validators.pattern(codePattern)]],priority:[0,[Validators.required,Validators.min(0),Validators.max(2147483647),Validators.pattern(/^[0-9]+$/)]],region:[''],city:[''],activity:[''],targetMemberId:['',Validators.required],active:[true]});
  readonly fallback=this.fb.nonNullable.group({memberId:['']});readonly preview=this.fb.nonNullable.group({region:['',Validators.required],city:['',Validators.required],activity:['',Validators.required]});
  ngOnInit():void{this.onboarding.allowedAcquirers().pipe(takeUntil(this.destroy)).subscribe({next:rows=>{this.acquirers.set(rows);if(rows.length===1){this.owner.controls.code.setValue(rows[0].code);this.changeOwner();}},error:()=>this.error.set('Les acquéreurs autorisés sont indisponibles.')});}
  ngOnDestroy():void{this.cancel.next();this.cancel.complete();this.destroy.next();this.destroy.complete();}
  changeOwner():void{this.cancel.next();this.busy.set(false);this.message.set('');this.error.set('');this.configuration.set(null);this.regions.set([]);this.cities.set([]);this.activities.set([]);this.preview.reset();this.previewResult.set(null);this.selectSection('references');this.load();}
  selectSection(section:Section):void{this.section.set(section);this.newReference();this.newTeam();this.newMember();this.newRule();this.entityId='';}
  private load():void{const code=this.owner.controls.code.value;this.cancel.next();this.loading.set(false);
    if(!this.acquirers().some(a=>a.code===code)){this.configuration.set(null);return;}
    this.configuration.set(null);this.loading.set(true);forkJoin({configuration:this.service.configuration(code),regions:this.service.references(code,'REGION',false),cities:this.service.references(code,'CITY',false),activities:this.service.references(code,'ACTIVITY',false)})
      .pipe(takeUntil(this.cancel)).subscribe({next:data=>{this.configuration.set(data.configuration);this.regions.set(data.regions);this.cities.set(data.cities);this.activities.set(data.activities);this.fallback.controls.memberId.setValue(data.configuration.fallback?.memberId||'');this.loading.set(false);},error:()=>{this.loading.set(false);this.configuration.set(null);this.error.set('Configuration indisponible ou administration non autorisée. Aucune donnée d’un autre acquéreur ne sera affichée.');}});
  }
  referenceRows():CommercialReference[]{return this.reference.controls.kind.value==='REGION'?this.regions():this.reference.controls.kind.value==='CITY'?this.cities():this.activities();}
  ruleCities():CommercialReference[]{return this.cities().filter(c=>c.extra===this.rule.controls.region.value);}
  previewCities():CommercialReference[]{return this.cities().filter(c=>c.extra===this.preview.controls.region.value);}
  userLabel(id:string):string{return this.configuration()?.users.find(u=>u.id===id)?.login||'Identité indisponible';}
  teamLabel(id:string):string{return this.configuration()?.teams.find(t=>t.id===id)?.label||'Équipe indisponible';}
  memberLabel(id:string):string{const m=this.configuration()?.members.find(m=>m.id===id);return m?this.userLabel(m.userId):'Responsable indisponible';}
  available(m:CommercialTeamMember):boolean{const c=this.configuration();return !!(m.active&&c?.users.some(u=>u.id===m.userId&&u.active)&&c?.teams.some(t=>t.id===m.teamId&&t.active));}
  newReference():void{const kind=this.reference.controls.kind.value;this.reference.reset({kind,code:'',label:'',extra:'',active:true});this.referenceVersion.set(-1);}
  editReference(r:CommercialReference):void{this.reference.patchValue({...r,extra:r.extra||''});this.referenceVersion.set(r.version);}
  validExtra():boolean{const r=this.reference.getRawValue();return r.kind==='REGION'||r.kind==='CITY'&&this.regions().some(region=>region.code===r.extra&&region.active)||r.kind==='ACTIVITY'&&(!r.extra||/^[0-9]{4}$/.test(r.extra));}
  saveReference():void{if(this.reference.invalid||!this.validExtra())return;const r=this.reference.getRawValue();this.send(this.service.saveReference(this.owner.controls.code.value,r.kind,r.code,{label:r.label.trim(),extra:r.extra||null,active:r.active,expectedVersion:this.referenceVersion()}));}
  newTeam():void{this.team.reset({code:'',label:'',active:true});this.teamVersion.set(-1);this.entityId='';}
  editTeam(t:CommercialTeam):void{this.team.patchValue(t);this.teamVersion.set(t.version);this.entityId=t.id;}
  saveTeam():void{if(this.team.invalid)return;this.send(this.service.saveConfiguration(this.owner.controls.code.value,'teams',this.id(),{...this.team.getRawValue(),label:this.team.controls.label.value.trim(),expectedVersion:this.teamVersion()}));}
  newMember():void{this.member.reset({teamId:'',userId:'',teamRole:'FIELD',canReview:false,active:true});this.memberVersion.set(-1);this.entityId='';}
  editMember(m:CommercialTeamMember):void{this.member.patchValue(m);this.memberVersion.set(m.version);this.entityId=m.id;}
  saveMember():void{if(this.member.invalid)return;this.send(this.service.saveConfiguration(this.owner.controls.code.value,'members',this.id(),{...this.member.getRawValue(),expectedVersion:this.memberVersion()}));}
  newRule():void{this.rule.reset({code:'',priority:0,region:'',city:'',activity:'',targetMemberId:'',active:true});this.ruleVersion.set(-1);this.entityId='';}
  editRule(r:CommercialRule):void{this.rule.patchValue({...r,code:this.configuration()!.ruleCodes[r.id],region:r.region||'',city:r.city||'',activity:r.activity||'',targetMemberId:r.target});this.ruleVersion.set(r.version);this.entityId=r.id;}
  saveRule():void{if(this.rule.invalid)return;const r=this.rule.getRawValue();this.send(this.service.saveConfiguration(this.owner.controls.code.value,'rules',this.id(),{...r,region:r.region||null,city:r.city||null,activity:r.activity||null,expectedVersion:this.ruleVersion()}));}
  saveFallback():void{this.send(this.service.saveFallback(this.owner.controls.code.value,this.fallback.controls.memberId.value||null,this.configuration()?.fallback?.version??-1));}
  private id():string{return this.entityId||(this.entityId=crypto.randomUUID());}
  private send(request:Observable<unknown>):void{if(this.busy()||!this.configuration())return;this.busy.set(true);this.message.set('');this.error.set('');
    request.pipe(takeUntil(this.cancel)).subscribe({next:()=>{this.busy.set(false);this.message.set('Configuration enregistrée et auditée.');this.selectSection(this.section());this.load();},error:e=>{this.busy.set(false);this.error.set(e?.status===403?'Administration refusée pour votre profil.':e?.status===409?'Conflit de priorité ou version modifiée. Rechargez la configuration avant de corriger.':e?.status===400?'Vérifiez les champs, références et valeurs obligatoires.':'Enregistrement non confirmé. La configuration est rechargée avant toute nouvelle action.');this.load();}});
  }
  simulate():void{if(this.preview.invalid||this.busy())return;this.busy.set(true);this.error.set('');this.previewResult.set(null);
    this.service.preview(this.owner.controls.code.value,this.preview.getRawValue()).pipe(takeUntil(this.cancel)).subscribe({next:r=>{this.busy.set(false);this.previewResult.set(r);},error:()=>{this.busy.set(false);this.error.set('Simulation refusée ou références indisponibles. Aucun dossier n’a été modifié.');}});
  }
  routeLabel(route:string):string{return({FIELD:'Commercial terrain',LEAD:'Chef d’équipe',FALLBACK:'Secours : dossier à répartir',EXCEPTION:'Exception : aucun responsable disponible'} as Record<string,string>)[route]||'Résultat indisponible';}
  reasonLabel(reason:string):string{return({MATCHED_RULE:'Règle active correspondante',NO_MATCH_EXPLICIT_FALLBACK:'Aucune règle, secours explicite',TARGET_UNAVAILABLE_EXPLICIT_FALLBACK:'Cible indisponible, secours explicite',FALLBACK_UNAVAILABLE:'Secours absent ou indisponible',RULE_PRIORITY_CONFLICT:'Conflit de règles',RETURN_TO_INITIATOR:'Retour au commercial initiateur',INITIATOR_UNAVAILABLE:'Commercial initiateur indisponible'} as Record<string,string>)[reason]||'Motif à vérifier';}
}
