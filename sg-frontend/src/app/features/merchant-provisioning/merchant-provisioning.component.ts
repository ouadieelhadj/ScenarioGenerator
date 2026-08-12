import { Component, inject, OnInit, signal } from '@angular/core';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { FuturPaymentCandidate, Way4ExportCandidate } from '../../core/models/merchant-onboarding.models';

@Component({
  standalone: true,
  template: `
    <header><h1>Provisionnement commerçants</h1><p>Génération manuelle WAY4 et relance FuturPayment.</p></header>
    @if (message()) { <p class="notice">{{ message() }}</p> }
    @if (error()) { <p class="error">{{ error() }}</p> }
    <section>
      <div class="title"><div><h2>WAY4 — fichiers XML</h2><p>Un fichier peut contenir plusieurs commerçants.</p></div>
        <button (click)="generate()" [disabled]="busy() || selected().size === 0">Générer le fichier ({{ selected().size }})</button></div>
      <table><thead><tr><th></th><th>Dossier</th><th>Commerçant</th><th>RegNumber</th><th>État</th></tr></thead><tbody>
        @for (item of way4(); track item.caseId) { <tr>
          <td><input type="checkbox" [checked]="selected().has(item.caseId)" (change)="toggle(item.caseId)"></td>
          <td>{{ item.reference }}</td><td>{{ item.legalName }}<small>{{ item.registrationNumber }}</small></td>
          <td>{{ item.applicationRegNumber }}</td><td>{{ item.status }} @if(item.lastErrorMessage){<small>{{ item.lastErrorMessage }}</small>}</td>
        </tr> } @empty { <tr><td colspan="5">Aucun commerçant WAY4 à générer ou recycler.</td></tr> }
      </tbody></table>
    </section>
    <section>
      <div class="title"><div><h2>FuturPayment Acquiring</h2><p>Relance via l’API de provisionnement existante.</p></div></div>
      <table><thead><tr><th>Dossier</th><th>Commerçant</th><th>État</th><th></th></tr></thead><tbody>
        @for (item of futurPayment(); track item.caseId) { <tr><td>{{ item.reference }}</td>
          <td>{{ item.legalName }}<small>{{ item.registrationNumber }}</small></td><td>{{ item.status }} ({{ item.attempts }})<small>{{ item.lastErrorMessage }}</small></td>
          <td><button (click)="resend(item)" [disabled]="busy() || item.status !== 'FAILED_FINAL'">Renvoyer</button></td></tr>
        } @empty { <tr><td colspan="4">Aucun dossier FuturPayment à renvoyer.</td></tr> }
      </tbody></table>
    </section>`,
  styles: [`header,section{padding:18px;border:1px solid var(--sg-border);border-radius:12px;background:var(--sg-bg-surface);margin-bottom:18px}.title{display:flex;justify-content:space-between;gap:16px;align-items:center}table{border-collapse:collapse;width:100%}th,td{padding:10px;text-align:left;border-bottom:1px solid var(--sg-border)}small{display:block;color:var(--sg-text-muted);margin-top:3px}button{padding:9px 12px;border:0;border-radius:8px;background:var(--sg-color-primary);color:#fff;cursor:pointer}button:disabled{opacity:.5}.notice,.error{padding:12px;border-radius:8px}.notice{border:1px solid #16803c}.error{border:1px solid #b42318;color:#b42318}@media(max-width:760px){.title{align-items:flex-start;flex-direction:column}table{display:block;overflow:auto}}`],
})
export class MerchantProvisioningComponent implements OnInit {
  private readonly service=inject(MerchantOnboardingService);
  readonly way4=signal<Way4ExportCandidate[]>([]); readonly futurPayment=signal<FuturPaymentCandidate[]>([]);
  readonly selected=signal(new Set<string>()); readonly busy=signal(false); readonly message=signal(''); readonly error=signal('');
  ngOnInit():void{this.reload();}
  reload():void{this.service.way4Candidates().subscribe(items=>this.way4.set(items));this.service.futurPaymentCandidates().subscribe(items=>this.futurPayment.set(items));}
  toggle(id:string):void{this.selected.update(current=>{const next=new Set(current);next.has(id)?next.delete(id):next.add(id);return next;});}
  generate():void{if(!this.selected().size)return;this.busy.set(true);this.error.set('');this.service.generateWay4Batch([...this.selected()]).subscribe({next:result=>{const blob=new Blob([result.xml],{type:'application/xml'});const href=URL.createObjectURL(blob);const link=document.createElement('a');link.href=href;link.download=result.fileName;link.click();URL.revokeObjectURL(href);this.message.set(`${result.fileName} généré : ${result.merchantCount} commerçant(s), XSD validé.`);this.selected.set(new Set());this.busy.set(false);this.reload();},error:err=>{this.error.set(err?.error?.message||'Échec de génération WAY4. Les dossiers restent recyclables.');this.busy.set(false);this.reload();}});}
  resend(item:FuturPaymentCandidate):void{this.busy.set(true);this.error.set('');this.service.resendFuturPayment(item.eventId).subscribe({next:()=>{this.message.set(`${item.reference} remis en attente pour FuturPayment.`);this.busy.set(false);this.reload();},error:err=>{this.error.set(err?.error?.message||'Échec de la relance FuturPayment.');this.busy.set(false);}});}
}
