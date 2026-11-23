import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { AllowedAcquirer } from '../../core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';
import { MerchantCommercialService } from '../../core/services/merchant-commercial.service';
import { CommercialPhase } from '../../core/models/merchant-commercial.models';

@Component({
  selector: 'app-merchant-commercial-queue', standalone: true, imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="queue"><h1>Dossiers commerciaux</h1><p>Les dossiers sont limités à vos affectations, à votre équipe et à votre acquéreur autorisé.</p>
      <form [formGroup]="filters" (ngSubmit)="firstPage()">
        <label>Acquéreur<select formControlName="acquirer" (change)="firstPage()"><option value="">Choisir un acquéreur autorisé</option>@for (a of acquirers(); track a.code) { <option [value]="a.code">{{ a.name }}</option> }</select></label>
        <label>Étape<select formControlName="phase"><option value="">Toutes les étapes autorisées</option>
          <option value="DRAFT">Brouillon</option><option value="AWAITING_CONFIRMATION">Confirmation personnelle attendue</option>
          <option value="IN_REVIEW">En revue commerciale</option><option value="TO_DISTRIBUTE">À répartir</option><option value="ASSIGNMENT_EXCEPTION">Exception d’affectation</option>
          <option value="COMPLEMENTS_REQUIRED">Compléments demandés</option><option value="REEXAMINATION">À réexaminer</option><option value="READY_FOR_BO">Prêt pour le Back-office</option><option value="TRANSMITTED_BO">Transmis au Back-office</option>
        </select></label><button type="submit" [disabled]="loading() || !filters.controls.acquirer.value">Actualiser les dossiers</button>
      </form>
      @if (loading()) { <p role="status">Chargement des dossiers…</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (!loading() && !error()) {
        <p>{{ total() }} dossier(s) dans votre périmètre.</p>
        @if (!rows().length) { <p>Aucun dossier pour ces critères.</p> }
        @else { <div class="scroll"><table><thead><tr><th>Référence</th><th>Commerce</th><th>Parcours</th><th>Étape</th><th>Action</th></tr></thead><tbody>
          @for (row of rows(); track row.id) { <tr><td>{{ row.reference }}</td><td>{{ row.legalName || 'Dossier à compléter' }}</td><td>{{ row.origin === 'AUTO' ? 'Auto-affiliation' : 'Accompagnée' }}</td><td>{{ phaseLabel(row.phase) }}</td><td><a [routerLink]="['/commercial/dossiers',row.id]">Ouvrir le dossier</a></td></tr> }
        </tbody></table></div> }
        <nav aria-label="Pagination des dossiers"><button type="button" (click)="page(-1)" [disabled]="offset() === 0">Précédent</button><button type="button" (click)="page(1)" [disabled]="offset()+25 >= total()">Suivant</button></nav>
      }
    </section>
  `,
  styles: [`.queue{padding:18px;color:var(--sg-text-primary,#213547)}form{display:flex;flex-wrap:wrap;align-items:end;gap:12px}label{display:grid;gap:6px}select,button{padding:10px;border:1px solid #c5cfda;border-radius:8px}button{cursor:pointer}button:disabled{opacity:.55;cursor:default}.scroll{overflow-x:auto}table{border-collapse:collapse;width:100%;margin:16px 0}td,th{padding:10px;border-bottom:1px solid #d3dce5;text-align:left;overflow-wrap:anywhere}nav{display:flex;gap:12px}.error{color:#b42318}`],
})
export class MerchantCommercialQueueComponent implements OnInit, OnDestroy {
  private readonly onboarding=inject(MerchantOnboardingService); private readonly commercial=inject(MerchantCommercialService);
  private readonly cancel=new Subject<void>(); private readonly destroy=new Subject<void>(); private readonly fb=inject(FormBuilder);
  readonly acquirers=signal<AllowedAcquirer[]>([]); readonly rows=signal<Array<{id:string;reference:string;legalName:string|null;phase:CommercialPhase;origin:string}>>([]);
  readonly total=signal(0);readonly offset=signal(0);readonly loading=signal(false);readonly error=signal('');
  readonly filters=this.fb.nonNullable.group({acquirer:[''],phase:['']});
  ngOnInit():void {this.onboarding.allowedAcquirers().pipe(takeUntil(this.destroy)).subscribe({next:rows=>{
    this.acquirers.set(rows);if(rows.length===1){this.filters.controls.acquirer.setValue(rows[0].code);this.firstPage();}
  },error:()=>this.error.set('Les acquéreurs autorisés sont indisponibles.')});}
  ngOnDestroy():void{this.cancel.next();this.cancel.complete();this.destroy.next();this.destroy.complete();}
  firstPage():void{this.offset.set(0);this.load();}
  page(direction:number):void{this.offset.update(value=>Math.max(0,value+direction*25));this.load();}
  private load():void{
    this.cancel.next();this.rows.set([]);this.total.set(0);this.error.set('');const acquirer=this.filters.controls.acquirer.value;
    if(!this.acquirers().some(a=>a.code===acquirer)){this.loading.set(false);return;}
    this.loading.set(true);this.commercial.queue(acquirer,this.filters.controls.phase.value as CommercialPhase|'',this.offset(),25)
      .pipe(takeUntil(this.cancel)).subscribe({next:page=>{this.rows.set(page.items);this.total.set(page.total);this.loading.set(false);},error:()=>{
        this.loading.set(false);this.error.set('Liste indisponible ou accès refusé. Aucune donnée d’un autre acquéreur ne sera affichée.');}});
  }
  phaseLabel(phase:string):string{return({DRAFT:'Brouillon',AWAITING_CONFIRMATION:'Confirmation personnelle attendue',IN_REVIEW:'En revue commerciale',TO_DISTRIBUTE:'À répartir',ASSIGNMENT_EXCEPTION:'Exception d’affectation',COMPLEMENTS_REQUIRED:'Compléments demandés',REEXAMINATION:'À réexaminer',READY_FOR_BO:'Prêt pour le Back-office',TRANSMITTED_BO:'Transmis au Back-office'} as Record<string,string>)[phase]||phase;}
}
