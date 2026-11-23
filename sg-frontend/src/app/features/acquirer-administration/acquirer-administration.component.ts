import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/models/auth.models';
import {
  AcquirerChangeRequest, AcquirerUserRole, PlatformAcquirer,
  PlatformAcquirerRequest, PlatformAcquirerUser,
} from '../../core/models/merchant-onboarding.models';
import { AcquirerAdministrationService } from '../../core/services/acquirer-administration.service';

@Component({
  selector: 'app-acquirer-administration',
  standalone: true,
  imports: [FormsModule],
  template: `
    <header class="page-header">
      <div><span class="eyebrow">ADMINISTRATION FUTURPAYMENT</span><h1>Acquéreurs</h1>
        <p>Référentiel, configuration, utilisateurs et validation Maker/Checker.</p></div>
      <button type="button" class="secondary" (click)="reload()">Actualiser</button>
    </header>
    @if (message()) { <p class="message success">{{ message() }}</p> }
    @if (error()) { <p class="message error">{{ error() }}</p> }

    <section class="panel">
      <div class="section-title"><h2>Référentiel des acquéreurs</h2><button type="button" (click)="newAcquirer()">Nouvel acquéreur</button></div>
      <table><thead><tr><th>Code</th><th>Raison sociale</th><th>Nom commercial</th><th>Statut</th><th>Actions</th></tr></thead>
        <tbody>@for (item of acquirers(); track item.id) {<tr>
          <td><strong>{{ item.code }}</strong></td><td>{{ item.legalName }}</td><td>{{ item.tradeName }}</td>
          <td><span class="badge" [class.active]="item.status === 'ACTIVE'">{{ item.status }}</span></td>
          <td class="actions"><button type="button" class="link" (click)="edit(item)">Consulter / modifier</button>
            @if (canAdmin()) {<button type="button" class="link" (click)="toggle(item)">{{ item.status === 'ACTIVE' ? 'Désactiver' : 'Activer' }}</button>}</td>
        </tr>} @empty {<tr><td colspan="5">Aucun acquéreur enregistré.</td></tr>}</tbody>
      </table>
    </section>

    @if (showForm()) {
      <section class="panel">
        <h2>{{ editingId ? 'Modification soumise au workflow' : 'Créer un acquéreur' }}</h2>
        <form (ngSubmit)="save()" class="form-grid">
          <label>Code unique<input name="code" [(ngModel)]="form.code" pattern="[A-Z][A-Z0-9_-]{2,63}" [readonly]="!!editingId" required /></label>
          <label>Raison sociale<input name="legalName" [(ngModel)]="form.legalName" required /></label>
          <label>Nom commercial<input name="tradeName" [(ngModel)]="form.tradeName" required /></label>
          <label>Email de contact<input name="contactEmail" type="email" [(ngModel)]="form.contactEmail" required /></label>
          <label>Téléphone<input name="contactPhone" [(ngModel)]="form.contactPhone" /></label>
          <label>Adresse<input name="postalAddress" [(ngModel)]="form.postalAddress" /></label>
          <label>URL du logo<input name="logoUrl" type="url" [(ngModel)]="form.logoUrl" /></label>
          <label>Couleur principale<input name="primaryColor" [(ngModel)]="form.primaryColor" pattern="#[0-9A-Fa-f]{6}" placeholder="#0057B8" /></label>
          <div class="wide"><div class="section-title"><h3>Produits, canaux, devises et règles de règlement</h3><button type="button" class="secondary" (click)="addConfiguration()">Ajouter</button></div>
            @for (config of form.configurations; track $index) {<div class="config-row">
              <select [name]="'type-' + $index" [(ngModel)]="config.type" required><option value="PRODUCT">Produit</option><option value="CHANNEL">Canal</option><option value="CURRENCY">Devise</option><option value="SETTLEMENT_RULE">Règle de règlement</option></select>
              <input [name]="'config-code-' + $index" [(ngModel)]="config.code" placeholder="Code" required />
              <input [name]="'config-label-' + $index" [(ngModel)]="config.label" placeholder="Libellé" required />
              <input [name]="'config-json-' + $index" [(ngModel)]="config.configurationJson" placeholder="Paramètres JSON facultatifs" />
              <button type="button" class="danger" (click)="removeConfiguration($index)">Retirer</button>
            </div>}
          </div>
          <div class="wide actions"><button type="submit">{{ editingId ? 'Soumettre la modification' : 'Créer' }}</button><button type="button" class="secondary" (click)="cancel()">Annuler</button></div>
        </form>
      </section>
    }

    @if (selected(); as acquirer) {
      <section class="panel"><h2>Utilisateurs de {{ acquirer.tradeName }}</h2>
        <table><thead><tr><th>Login</th><th>Sujet d'identité</th><th>Rôle</th><th>Actif</th><th>Action</th></tr></thead><tbody>
          @for (user of users(); track user.id) {<tr><td>{{ user.login }}</td><td>{{ user.identitySubject }}</td>
            @if (canAdmin()) {
              <td><select #roleInput [value]="user.role">@for (role of roles; track role) {<option [value]="role">{{ role }}</option>}</select></td>
              <td><input #activeInput type="checkbox" [checked]="user.active" aria-label="Utilisateur actif" /></td>
              <td><button type="button" class="link" (click)="updateUser(user, roleInput.value, activeInput.checked)">Soumettre</button></td>
            } @else {
              <td>{{ user.role }}</td><td>{{ user.active ? 'Oui' : 'Non' }}</td><td>Consultation</td>
            }
          </tr>}
          @empty {<tr><td colspan="5">Aucun utilisateur rattaché.</td></tr>}
        </tbody></table>
        @if (canAdmin()) {<form class="user-row" (ngSubmit)="addUser()">
          <input name="userLogin" [(ngModel)]="userForm.login" placeholder="Login" required />
          <input name="identitySubject" [(ngModel)]="userForm.identitySubject" placeholder="Identifiant Keycloak / IdP" required />
          <select name="userRole" [(ngModel)]="userForm.role" required>@for (role of roles; track role) {<option [value]="role">{{ role }}</option>}</select>
          <button type="submit">Soumettre le rattachement</button>
        </form>}
      </section>
    }

    <section class="panel"><h2>Demandes en attente de validation</h2>
      <table><thead><tr><th>Action</th><th>Acquéreur</th><th>Maker</th><th>Date</th><th>Décision</th></tr></thead><tbody>
        @for (change of changes(); track change.id) {<tr><td>{{ change.action }}</td><td>{{ acquirerCode(change.acquirerId) }}</td><td>{{ change.requestedBy }}</td><td>{{ change.requestedAt }}</td>
          <td class="actions">@if (canCheck()) {<button type="button" class="link" (click)="approve(change)">Approuver</button><button type="button" class="link danger-text" (click)="reject(change)">Rejeter</button>} @else {Validation Checker requise}</td></tr>}
        @empty {<tr><td colspan="5">Aucune demande en attente.</td></tr>}
      </tbody></table>
    </section>
  `,
  styles: [`
    .page-header,.section-title,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.page-header{margin-bottom:20px}.eyebrow{font-size:12px;font-weight:700;letter-spacing:.12em;color:var(--sg-color-primary)}h1{margin:6px 0}.panel{margin-bottom:18px;padding:20px;background:var(--sg-bg-surface);border:1px solid var(--sg-border);border-radius:var(--sg-radius-lg);overflow:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:10px;border-bottom:1px solid var(--sg-border)}button{padding:9px 13px;border:0;border-radius:var(--sg-radius);background:var(--sg-color-primary);color:#fff;cursor:pointer}.secondary,.link{background:transparent;color:var(--sg-color-primary);border:1px solid var(--sg-border)}.danger{background:#b42318}.danger-text{color:#b42318}.badge{padding:4px 9px;border-radius:12px;background:#eee}.badge.active{background:#dcfae6;color:#067647}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(220px,1fr));gap:14px}.form-grid label{display:grid;gap:6px}.wide{grid-column:1/-1}input,select{padding:9px;border:1px solid var(--sg-border-strong);border-radius:var(--sg-radius);background:var(--sg-bg-surface);color:var(--sg-text-primary)}.config-row,.user-row{display:grid;grid-template-columns:1fr 1fr 2fr 2fr auto;gap:8px;margin:8px 0}.user-row{grid-template-columns:1fr 2fr 1fr auto;margin-top:16px}.message{padding:10px;border-radius:var(--sg-radius)}.success{background:#ecfdf3;color:#067647}.error{background:#fff1f0;color:#b42318}@media(max-width:900px){.form-grid{grid-template-columns:1fr}.config-row,.user-row{grid-template-columns:1fr}.page-header,.section-title,.actions{align-items:flex-start;flex-wrap:wrap}}
  `],
})
export class AcquirerAdministrationComponent implements OnInit {
  private readonly service = inject(AcquirerAdministrationService);
  private readonly auth = inject(AuthService);
  readonly acquirers = signal<PlatformAcquirer[]>([]);
  readonly users = signal<PlatformAcquirerUser[]>([]);
  readonly changes = signal<AcquirerChangeRequest[]>([]);
  readonly selected = signal<PlatformAcquirer | null>(null);
  readonly showForm = signal(false);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly roles: AcquirerUserRole[] = ['ADMIN','MAKER','CHECKER','COMMERCIAL','BACK_OFFICE','VIEWER','ACCOUNTING'];
  editingId: string | null = null;
  form = this.emptyForm();
  userForm: { identitySubject: string; login: string; role: AcquirerUserRole } = { identitySubject: '', login: '', role: 'VIEWER' };

  ngOnInit(): void { this.reload(); }
  canAdmin(): boolean { return this.auth.hasPermission(Permission.ACQUIRER_PLATFORM_ADMIN) || this.auth.hasRole('PLATFORM_ADMIN'); }
  canCheck(): boolean { return this.auth.hasPermission(Permission.ACQUIRER_PLATFORM_CHECKER) || this.auth.hasRole('PLATFORM_ADMIN'); }
  reload(): void {
    this.clearMessages();
    forkJoin({ acquirers: this.service.list(), changes: this.service.pending() }).subscribe({
      next: result => { this.acquirers.set(result.acquirers); this.changes.set(result.changes); },
      error: () => this.error.set("Le référentiel des acquéreurs n'a pas pu être chargé."),
    });
  }
  newAcquirer(): void { this.editingId = null; this.form = this.emptyForm(); this.selected.set(null); this.users.set([]); this.showForm.set(true); }
  edit(value: PlatformAcquirer): void {
    this.editingId = value.id; this.selected.set(value); this.showForm.set(true);
    this.form = { code:value.code,legalName:value.legalName,tradeName:value.tradeName,contactEmail:value.contactEmail,
      contactPhone:value.contactPhone,postalAddress:value.postalAddress,logoUrl:value.logoUrl,primaryColor:value.primaryColor,
      configurations:value.configurations.map(item=>({ ...item })) };
    this.service.users(value.id).subscribe({next:users=>this.users.set(users),error:()=>this.error.set('Les utilisateurs ne peuvent pas être chargés.')});
  }
  cancel(): void { this.showForm.set(false); this.editingId = null; }
  addConfiguration(): void { this.form.configurations.push({type:'PRODUCT',code:'',label:'',configurationJson:null}); }
  removeConfiguration(index: number): void { this.form.configurations.splice(index, 1); }
  save(): void {
    this.clearMessages(); const request=this.normalizedRequest();
    const call: Observable<unknown> = this.editingId
      ? this.service.update(this.editingId,request) : this.service.create(request);
    call.subscribe({next:()=>{const text=this.editingId?'Modification soumise au Checker.':'Acquéreur créé en brouillon.';this.cancel();this.reload();this.message.set(text);},error:(e:{error?:{message?:string}})=>this.fail(e)});
  }
  toggle(value: PlatformAcquirer): void {
    const call=value.status==='ACTIVE'?this.service.deactivate(value.id):this.service.activate(value.id);
    call.subscribe({next:()=>{this.reload();this.message.set('Changement de statut soumis au Checker.');},error:e=>this.fail(e)});
  }
  addUser(): void {
    const value=this.selected(); if(!value)return;
    this.service.addUser(value.id,{...this.userForm}).subscribe({next:()=>{this.userForm={identitySubject:'',login:'',role:'VIEWER'};this.reload();this.message.set('Rattachement utilisateur soumis au Checker.');},error:e=>this.fail(e)});
  }
  updateUser(user: PlatformAcquirerUser, role: string, active: boolean): void {
    const value=this.selected(); if(!value)return;
    this.service.updateUser(value.id,user.id,{role:role as AcquirerUserRole,active}).subscribe({next:()=>{this.reload();this.message.set('Modification utilisateur soumise au Checker.');},error:e=>this.fail(e)});
  }
  approve(change: AcquirerChangeRequest): void { this.service.approve(change.id).subscribe({next:()=>{this.reload();this.message.set('Demande approuvée.');},error:e=>this.fail(e)}); }
  reject(change: AcquirerChangeRequest): void { const reason=window.prompt('Motif du rejet');if(!reason?.trim())return;this.service.reject(change.id,reason.trim()).subscribe({next:()=>{this.reload();this.message.set('Demande rejetée.');},error:e=>this.fail(e)}); }
  acquirerCode(id: string): string { return this.acquirers().find(item=>item.id===id)?.code ?? id; }
  private emptyForm(): PlatformAcquirerRequest { return {code:'',legalName:'',tradeName:'',contactEmail:'',contactPhone:null,postalAddress:null,logoUrl:null,primaryColor:null,configurations:[]}; }
  private normalizedRequest(): PlatformAcquirerRequest { return {...this.form,code:this.form.code.trim().toUpperCase(),legalName:this.form.legalName.trim(),tradeName:this.form.tradeName.trim(),contactEmail:this.form.contactEmail.trim(),contactPhone:this.form.contactPhone?.trim()||null,postalAddress:this.form.postalAddress?.trim()||null,logoUrl:this.form.logoUrl?.trim()||null,primaryColor:this.form.primaryColor?.trim()||null,configurations:this.form.configurations.map(item=>({...item,code:item.code.trim().toUpperCase(),label:item.label.trim(),configurationJson:item.configurationJson?.trim()||null}))}; }
  private clearMessages(): void { this.message.set(null);this.error.set(null); }
  private fail(error: { error?: { message?: string } }): void { this.error.set(error.error?.message ?? "L'opération a échoué."); }
}
