import { Component,inject,signal,OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { url } from '../../core/config/api.config';
import { AuthService } from '../../core/auth/auth.service';
interface Merchant {id:string;acquirerId:string;legalName:string;tradingName:string;status:string;}
@Component({standalone:true,imports:[FormsModule],template:`
 <main><h1>Statut des commerçants</h1><p>Suspension métier dans FuturPayment Acquiring. Les comptes utilisateurs et l’historique sont conservés.</p>
 <label>Acquéreur<select aria-label="Acquéreur" [(ngModel)]="code" (change)="load()" [disabled]="busy()">@for(bank of banks();track bank.code){<option [value]="bank.code">{{bank.name}} ({{bank.code}})</option>}</select></label>
 <button (click)="load()" [disabled]="busy()">Actualiser</button>
 @if(error()){<p role="alert">{{error()}}</p>}@if(notice()){<p role="status">{{notice()}}</p>}
 <table><thead><tr><th>Commerce</th><th>Raison sociale</th><th>Statut</th><th>Action</th></tr></thead><tbody>
 @for(m of merchants();track m.id){<tr><td>{{m.tradingName}}</td><td>{{m.legalName}}</td><td>{{m.status}}</td><td>
 @if(m.status==='ACTIVE'&&auth.hasPermission('MERCHANT_SUSPEND')){<button (click)="choose(m)" [disabled]="busy()">Suspendre le commerçant</button>}<button (click)="history(m)" [disabled]="busy()">Historique de suspension</button>
 </td></tr>}@empty{<tr><td colspan="4">Aucun commerçant disponible dans le système d’acquisition pour cet acquéreur.</td></tr>}</tbody></table>
 @if(target();as m){<section class="confirmation"><h2>Confirmer la suspension de {{m.tradingName}}</h2>
 <p>Le statut métier deviendra SUSPENDED. Cette action ne supprime ni l’identité ni l’historique et ne peut pas être annulée depuis cet écran.</p>
 <label>Motif de suspension<textarea aria-label="Motif de suspension" [(ngModel)]="reason" maxlength="1000" [disabled]="busy()"></textarea></label>
 <button (click)="suspend()" [disabled]="busy()||!reason.trim()">Confirmer la suspension</button><button (click)="cancel()" [disabled]="busy()">Annuler</button></section>}
 @if(historyTarget();as m){<section><h2>Historique de {{m.tradingName}}</h2><ul>@for(h of events();track $index){<li>{{h.date}} — {{h.actor}} : {{h.reason}}</li>}@empty{<li>Aucune suspension enregistrée.</li>}</ul></section>}
 </main>`,styles:[`main{padding:24px;color:#172b4d}label{display:block;margin:16px 0}select,textarea,button{padding:10px;margin:4px}table{border-collapse:collapse;width:100%;margin-top:20px}td,th{padding:14px;text-align:left;border-bottom:1px solid #ccd5df}textarea{display:block;width:min(600px,95%);min-height:90px}.confirmation{border:1px solid #d5a55b;padding:20px;margin-top:24px}[role=alert]{color:#9f1d20}[role=status]{color:#065f46}`]})
export class MerchantLifecycleComponent implements OnInit {
 readonly auth=inject(AuthService);private readonly http=inject(HttpClient);
 readonly historyTarget=signal<Merchant|null>(null);readonly events=signal<Array<{date:string;actor:string;reason:string}>>([]);
 readonly banks=signal<Array<{code:string;name:string}>>([]);readonly merchants=signal<Merchant[]>([]);
 readonly target=signal<Merchant|null>(null);readonly busy=signal(false);readonly error=signal('');readonly notice=signal('');code='';reason='';
 private endpoint(path:string){return url.onboarding('/api/merchant-lifecycle/v1'+path);}
 ngOnInit(){this.http.get<Array<{code:string;name:string}>>(this.endpoint('/acquirers')).subscribe({next:rows=>{this.banks.set(rows);this.code=rows.find(x=>x.code==='DAMANCASH')?.code||rows[0]?.code||'';this.load();},error:()=>this.error.set('Liste des acquéreurs indisponible.')});}
 load(){if(!this.code)return;this.busy.set(true);this.error.set('');this.notice.set('');this.target.set(null);this.historyTarget.set(null);this.events.set([]);this.http.get<Merchant[]>(this.endpoint('/acquirers/'+encodeURIComponent(this.code)+'/merchants')).subscribe({next:rows=>{this.merchants.set(rows);this.busy.set(false);},error:()=>{this.merchants.set([]);this.busy.set(false);this.error.set('Consultation indisponible ou non autorisée.');}});}
 history(m:Merchant){this.busy.set(true);this.error.set('');this.http.get<Array<{date:string;actor:string;reason:string}>>(this.endpoint('/acquirers/'+encodeURIComponent(this.code)+'/merchants/'+encodeURIComponent(m.id)+'/suspensions')).subscribe({next:rows=>{this.historyTarget.set(m);this.events.set(rows);this.busy.set(false);},error:()=>{this.busy.set(false);this.error.set('Historique indisponible ou non autorisé.');}});}
 choose(m:Merchant){this.target.set(m);this.reason='';this.notice.set('');}cancel(){this.target.set(null);this.reason='';}
 suspend(){const m=this.target();if(!m||this.busy()||!this.reason.trim())return;this.busy.set(true);this.error.set('');
  this.http.post<Merchant>(this.endpoint('/acquirers/'+encodeURIComponent(this.code)+'/merchants/'+encodeURIComponent(m.id)+'/suspensions'),{reason:this.reason.trim()}).subscribe({next:reply=>{this.merchants.update(rows=>rows.map(row=>row.id===reply.id?reply:row));this.cancel();this.busy.set(false);this.notice.set('Le commerçant est suspendu. Son historique est conservé.');},error:failure=>{this.busy.set(false);this.error.set(failure.status===409?'L’état du commerçant a changé. Actualisez avant de reprendre.':'Résultat à vérifier : actualisez avant toute nouvelle tentative.');}});
 }
}
