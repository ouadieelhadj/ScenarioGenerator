import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { UserService } from '../../core/services/user.service';
import { User } from '../../core/models/admin.models';
import { TranslatePipe } from '@ngx-translate/core';
import { RoleService, UserProfileSelection } from '../../core/services/role.service';
import { RoleSummary, PermissionSummary } from '../../core/models/role.models';

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [TranslatePipe, FormsModule],
  template: `
    <div class="page-header">
      <div>
        <h1><i class="pi pi-shield"></i> {{ 'roles.title' | translate }}</h1>
        <p>{{ 'roles.subtitle' | translate }}</p>
      </div>
    </div>

    @if (error()) {
      <div class="alert-error"><i class="pi pi-exclamation-circle"></i> {{ 'roles.loadError' | translate }}</div>
    }

    @if(message()){<p role="status">{{message()}}</p>}
    @if(actionError()){<p role="alert" class="alert-error">{{actionError()}}</p>}
    @if(canWrite()){
      <p>Les droits d’un profil s’appliquent à tous ses utilisateurs. Les changements prennent effet après reconnexion.</p>
      @if(auth.hasPermission('USER_MANAGE')){
        <section class="card assignment"><h2>Affecter des profils à un utilisateur</h2>
          <label>Rechercher un utilisateur<input [(ngModel)]="userSearch" /></label>
          <label>Utilisateur<select [(ngModel)]="userId" (change)="openUser()" [disabled]="busy()"><option value="">Choisir un utilisateur</option>@for(u of filteredUsers();track u.id){<option [value]="u.id">{{u.login}}</option>}</select></label>
          @if(userSelection();as selection){
            <p>Utilisateur : {{selection.login}}. Le rôle principal et le mot de passe sont conservés.</p>
            @for(role of roles();track role.id){<label class="choice"><input type="checkbox" [checked]="role.id===selection.primaryRoleId||selectedProfiles().includes(role.id)" [disabled]="busy()||role.id===selection.primaryRoleId" (change)="toggleProfile(role.id,$any($event.target).checked)" />{{role.label}} — {{role.code}} @if(role.id===selection.primaryRoleId){(rôle principal)}</label>}
            <button (click)="saveUserProfiles()" [disabled]="busy()">Enregistrer les profils</button>
            <button (click)="selectedProfiles.set([])" [disabled]="busy()">Rétablir le rôle principal seul</button>
          }
        </section>
      }
    }
    @if(editingRole();as selected){
      <section class="card permission-editor"><h2>Droits du profil {{selected.label}} — {{selected.code}}</h2>
        <label>Rechercher un droit<input [(ngModel)]="permissionSearch" /></label>
        @for(permission of filteredPermissions();track permission.id){<label class="choice"><input type="checkbox" [checked]="selectedPermissions().includes(permission.id)" [disabled]="busy()" (change)="togglePermission(permission.id,$any($event.target).checked)" />{{permission.label}} <small>{{permission.code}} · {{permission.category}}</small></label>}
        <button (click)="savePermissions()" [disabled]="busy()">Enregistrer les droits du profil</button><button (click)="editingRole.set(null)" [disabled]="busy()">Annuler</button>
      </section>
    }
    <div class="card">
      @if (loading()) {
        <div class="empty"><i class="pi pi-spin pi-spinner"></i> {{ 'common.loading' | translate }}</div>
      } @else if (!roles().length) {
        <div class="empty">{{ 'roles.none' | translate }}</div>
      } @else {
        <table class="data-table">
          <thead><tr><th>{{ 'roles.code' | translate }}</th><th>{{ 'roles.label' | translate }}</th><th>{{ 'roles.permissions' | translate }}</th>@if(canWrite()){<th>Action</th>}</tr></thead>
          <tbody>
            @for (role of roles(); track role.id) {
              <tr>
                <td><strong>{{ role.code }}</strong></td>
                <td>{{ role.label }}</td>
                <td class="permissions">
                  @for (permission of role.permissions; track permission.id) {
                    <span>{{ permission.code }}</span>
                  }
                </td>
                @if(canWrite()){<td><button (click)="editRole(role)" [disabled]="busy()">Configurer les droits</button></td>}
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    .choice{display:flex;gap:10px;align-items:center;margin:9px 0}.choice small{color:var(--sg-text-secondary)}.assignment,.permission-editor{margin:14px 0}.assignment label,.permission-editor>label{margin:10px 0}input,select,button{padding:8px}button{margin:8px 8px 0 0}
    .page-header { display:flex; justify-content:space-between; margin-bottom:1rem; }
    h1 { margin:0; color:var(--sg-text-primary); } .page-header p { color:var(--sg-text-secondary); }
    .card { padding:1rem; border:1px solid var(--sg-border); border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .data-table { width:100%; border-collapse:collapse; }
    th, td { padding:.75rem; text-align:left; border-bottom:1px solid var(--sg-border); vertical-align:top; }
    .permissions { display:flex; flex-wrap:wrap; gap:.35rem; }
    .permissions span { padding:.2rem .5rem; border-radius:999px; color:var(--sg-color-primary);
      background:color-mix(in srgb, var(--sg-color-primary) 10%, transparent); font-size:.75rem; }
    .empty { padding:2rem; text-align:center; color:var(--sg-text-secondary); }
    .alert-error { margin-bottom:1rem; padding:.75rem; color:var(--sg-color-danger); }
  `],
})
export class RolesComponent implements OnInit {
  private service = inject(RoleService);
  private usersService=inject(UserService);readonly auth=inject(AuthService);
  readonly catalog=signal<PermissionSummary[]>([]);readonly users=signal<User[]>([]);
  readonly busy=signal(false);readonly actionError=signal('');readonly message=signal('');
  readonly editingRole=signal<RoleSummary|null>(null);readonly selectedPermissions=signal<number[]>([]);
  readonly userSelection=signal<UserProfileSelection|null>(null);readonly selectedProfiles=signal<number[]>([]);
  userId='';userSearch='';permissionSearch='';
  canWrite():boolean{return this.auth.hasPermission('ROLE_MANAGE')&&(this.auth.hasRole('ADMIN')||this.auth.hasRole('PLATFORM_ADMIN'));}
  filteredUsers():User[]{const q=this.userSearch.toLocaleLowerCase();return this.users().filter(u=>u.login.toLocaleLowerCase().includes(q));}
  filteredPermissions():PermissionSummary[]{const q=this.permissionSearch.toLocaleLowerCase();return this.catalog().filter(p=>(p.code+' '+p.label+' '+p.category).toLocaleLowerCase().includes(q));}
  editRole(role:RoleSummary):void{this.editingRole.set(role);this.selectedPermissions.set(role.permissions.map(p=>p.id));this.actionError.set('');this.message.set('');}
  togglePermission(id:number,on:boolean):void{this.selectedPermissions.update(values=>on?[...values,id]:values.filter(v=>v!==id));}
  toggleProfile(id:number,on:boolean):void{this.selectedProfiles.update(values=>on?[...values,id]:values.filter(v=>v!==id));}
  private fail(error:any):void{this.busy.set(false);this.actionError.set(error?.status===409?'Les habilitations ont changé ou cette action retirerait vos propres droits. Rechargez les données avant de poursuivre.':'La modification des habilitations a été refusée. Aucun mot de passe n’a été changé.');}
  savePermissions():void{const role=this.editingRole();if(!role||!this.canWrite()||this.busy())return;this.busy.set(true);this.actionError.set('');
    this.service.updatePermissions(role.id,role.permissions.map(p=>p.id),this.selectedPermissions()).subscribe({next:()=>{this.busy.set(false);this.editingRole.set(null);this.message.set('Droits du profil enregistrés. Une reconnexion des utilisateurs est nécessaire.');this.loadRoles();},error:e=>this.fail(e)});}
  openUser():void{this.userSelection.set(null);if(!this.userId)return;this.busy.set(true);this.service.userProfiles(Number(this.userId)).subscribe({next:value=>{this.userSelection.set(value);this.selectedProfiles.set([...value.profileIds]);this.busy.set(false);},error:e=>this.fail(e)});}
  saveUserProfiles():void{const user=this.userSelection();if(!user||!this.canWrite()||this.busy())return;this.busy.set(true);this.actionError.set('');this.service.updateUserProfiles(user.userId,user.profileIds,this.selectedProfiles()).subscribe({next:value=>{this.userSelection.set(value);this.selectedProfiles.set([...value.profileIds]);this.busy.set(false);this.message.set('Profils enregistrés. Le rôle principal et le mot de passe sont conservés.');},error:e=>this.fail(e)});}
  private loadRoles():void{forkJoin({roles:this.service.findAll(),catalog:this.service.permissions()}).subscribe({next:value=>{this.roles.set(value.roles);this.catalog.set(value.catalog);this.loading.set(false);},error:()=>{this.error.set(true);this.loading.set(false);}});}

  readonly roles = signal<RoleSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  ngOnInit(): void {this.loadRoles();if(this.canWrite()&&this.auth.hasPermission('USER_MANAGE'))this.usersService.findAll().subscribe({next:users=>this.users.set(users),error:()=>this.actionError.set('Liste des utilisateurs indisponible.')});}
}
