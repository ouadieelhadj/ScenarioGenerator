import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, Subject, Subscription, filter, forkJoin, takeUntil } from 'rxjs';
import { MerchantCommercialService } from '../../core/services/merchant-commercial.service';
import { CommercialCommand, CommercialConfirmation, CommercialHistory, CommercialReference, CommercialResult, CommercialWorkspace } from '../../core/models/merchant-commercial.models';
import { CommercialConfirmationHandoffService } from '../../core/auth/commercial-confirmation-handoff.service';

/** Shared Web/APK screen. The backend controls every permission and transition. */
@Component({
  selector: 'app-merchant-commercial-workspace', standalone: true, imports: [ReactiveFormsModule, DatePipe],
  styleUrls: ['./merchant-commercial-workspace.component.css'],
  template: `
    <section class="commercial" aria-labelledby="commercial-title">
      <header><h2 id="commercial-title">Revue commerciale</h2><button type="button" (click)="reload()" [disabled]="busy()">Actualiser la revue</button></header>
      @if (loading()) { <p role="status">Chargement de la revue commerciale…</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (message()) { <p class="success" role="status">{{ message() }}</p> }
      @if (workspace(); as view) {
        @if (view.state; as state) {
          <dl><dt>Parcours</dt><dd>{{ state.origin === 'AUTO' ? 'Auto-affiliation' : 'Affiliation accompagnée' }}</dd>
            <dt>Étape</dt><dd>{{ phaseLabel(state.phase) }}</dd><dt>Responsable</dt><dd>{{ view.responsible?.login || 'En attente d’affectation' }}</dd>
            <dt>Équipe</dt><dd>{{ view.responsible?.team || 'Non affectée' }}</dd>
            @if (view.initiator) { <dt>Commercial initiateur</dt><dd>{{ view.initiator.login }}</dd> }
            <dt>Version du dossier</dt><dd>{{ view.caseVersion }}</dd></dl>
          @if (state.phase === 'ASSIGNMENT_EXCEPTION') { <p class="warning">Aucun responsable actif n’a été trouvé. Le dossier ne sera pas envoyé directement au KYC.</p> }
          @if (state.phase === 'TO_DISTRIBUTE') { <p class="warning">Le responsable de secours doit répartir ce dossier avant sa revue.</p> }
          @if (state.phase === 'REEXAMINATION') { <p class="warning">Le dossier a changé. Une nouvelle confirmation et une nouvelle revue sont nécessaires ; le responsable est conservé.</p> }
          @if (state.assignmentReason === 'EXPLICIT_DEPARTURE_REASSIGNMENT') { <p role="status">Ce dossier a été repris après l’indisponibilité du commercial. L’initiateur d’origine est conservé ; la reprise ne remplace pas la confirmation personnelle ni la validation KYC.</p> }
          @if (state.phase === 'TRANSMITTED_BO') { <p>La revue commerciale est terminée. Le dossier a été transmis au Back-office pour le contrôle KYC.</p> }
        } @else { <p>Ce dossier historique n’a pas encore de phase commerciale. Aucune approbation ne sera ajoutée automatiquement.</p> }
        @if (has('SUBMIT') || has('CONFIRM')) {
          <form [formGroup]="criteria" class="criteria">
            <label>Région<select formControlName="region" (change)="regionChanged()"><option value="">Choisir une région</option>@for (r of regions(); track r.code) { <option [value]="r.code">{{ r.label }}</option> }</select></label>
            <label>Ville<select formControlName="city"><option value="">Choisir une ville</option>@for (c of filteredCities(); track c.code) { <option [value]="c.code">{{ c.label }}</option> }</select></label>
            <label>Activité<select formControlName="activity"><option value="">Choisir une activité</option>@for (a of activities(); track a.code) { <option [value]="a.code">{{ a.label }}</option> }</select></label>
          </form>
          @if (has('SUBMIT')) { <button type="button" (click)="act('SUBMIT')" [disabled]="busy() || criteria.invalid || criteriaLoading()">Confirmer et soumettre à la revue commerciale</button> }
          @if (has('CONFIRM')) {
            <p>La confirmation doit provenir du lien personnel reçu. Elle ne remplace ni l’activation du compte ni la validation KYC.</p>
            <button type="button" (click)="confirm()" [disabled]="busy() || criteria.invalid || criteriaLoading() || !hasProtectedLink()">Confirmer personnellement le dossier</button>
          }
        }
        @if (confirmation(); as link) { <p>Confirmation : {{ confirmationLabel(link.status) }} — échéance {{ link.expiresAt | date:'dd/MM/yyyy HH:mm' }}.</p>
          <p role="status">Envoi du lien : {{ deliveryLabel(link.deliveryState) }}</p> }
        @if (has('PREPARE_CONFIRMATION')) { <button type="button" (click)="act('PREPARE_CONFIRMATION')" [disabled]="busy()">Préparer la confirmation personnelle</button> }
        @if (has('READY')) { <button type="button" (click)="act('READY')" [disabled]="busy()">Valider la revue commerciale</button> }
        @if (has('TRANSMIT')) { <button type="button" (click)="act('TRANSMIT')" [disabled]="busy()">Transmettre au Back-office KYC</button> }
        @if (has('REASSIGN') || has('COMPLEMENTS') || has('RENEW_CONFIRMATION') || has('ADOPT_HISTORY')) {
          <form [formGroup]="decision" class="decision">
            @if (has('REASSIGN')) { <label>Nouveau responsable<select formControlName="target"><option value="">Choisir un responsable autorisé</option>@for (p of view.targets; track p.id) { <option [value]="p.id">{{ p.login }} — {{ p.team }}</option> }</select></label> }
            <label>Motif obligatoire<textarea formControlName="reason" maxlength="1000" rows="3"></textarea></label>
          </form>
          @if (has('REASSIGN')) { <button type="button" (click)="act('REASSIGN')" [disabled]="busy() || !decision.controls.target.value || !validReason()">Réaffecter avec ce motif</button> }
          @if (has('COMPLEMENTS')) { <button type="button" (click)="act('COMPLEMENTS')" [disabled]="busy() || !validReason()">Demander des compléments</button> }
          @if (has('RENEW_CONFIRMATION')) { <button type="button" (click)="act('RENEW_CONFIRMATION')" [disabled]="busy() || !validReason()">Renouveler le lien personnel</button> }
          @if (has('ADOPT_HISTORY')) { <button type="button" (click)="act('ADOPT_HISTORY')" [disabled]="busy() || !validReason()">Initialiser la phase commerciale avec ce motif</button> }
        }
        <h3>Historique commercial</h3>
        @if (historyError()) { <p class="error" role="alert">L’historique n’a pas pu être chargé. Actualisez pour réessayer.</p> }
        @else if (!history().length) { <p>Aucune transition commerciale enregistrée.</p> }
        @else { <div class="table-scroll" tabindex="0" role="region" aria-label="Historique commercial défilant"><table><thead><tr><th>Version</th><th>Date</th><th>Action</th><th>Auteur</th><th>Étape</th><th>Motif</th></tr></thead><tbody>
          @for (event of history(); track event.version) { <tr><td>{{ event.version }}</td><td>{{ event.at | date:'dd/MM/yyyy HH:mm' }}</td><td>{{ actionLabel(event.command) }}</td><td>{{ event.actor }}</td><td>{{ phaseLabel(event.toPhase) }}</td><td>{{ event.reason || '—' }}</td></tr> }
        </tbody></table></div> }
      }
    </section>
  `,
  styles: [`.commercial{padding:18px;margin:16px 0;border:1px solid #d3dce5;border-radius:12px;background:var(--sg-bg-surface,#fff);color:var(--sg-text-primary,#213547)}header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}dl{display:grid;grid-template-columns:minmax(120px,1fr) 2fr;gap:8px}dt{font-weight:600}dd{margin:0}.criteria,.decision{display:grid;gap:12px;margin:16px 0}label{display:grid;gap:6px;font-weight:600}select,textarea{width:100%;padding:10px;border:1px solid #c5cfda;border-radius:8px;background:inherit;color:inherit}button{margin:5px;padding:10px 14px;border:1px solid #52738e;border-radius:8px;background:#eaf3fa;color:#163e5d;cursor:pointer}button:disabled{opacity:.55;cursor:default}.error{color:#b42318}.success{color:#126537}.warning{padding:10px;background:#fff8e3;color:#72520f}.table-scroll{overflow-x:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:8px;border-bottom:1px solid #d3dce5;overflow-wrap:anywhere}@media(min-width:900px){.criteria{grid-template-columns:repeat(3,1fr)}}`],
})
export class MerchantCommercialWorkspaceComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) caseId = '';
  @Input() refreshKey = 0;
  @Output() changed = new EventEmitter<void>();
  @Output() capabilities = new EventEmitter<CommercialWorkspace>();
  @Output() editable = new EventEmitter<boolean>();
  private readonly service = inject(MerchantCommercialService);
  private readonly confirmationHandoff=inject(CommercialConfirmationHandoffService);
  private readonly router=inject(Router);
  private readonly navigation: Subscription;
  private readonly changedLink=()=>this.receiveLink();
  private readonly fb = inject(FormBuilder);
  private readonly cancellation = new Subject<void>();
  private token: string|null = null;
  private confirmationId: string|null = null;
  private pending: { action: string; signature: string; command: CommercialCommand }|null = null;
  readonly workspace = signal<CommercialWorkspace|null>(null); readonly history = signal<CommercialHistory[]>([]);
  readonly confirmation = signal<CommercialConfirmation|null>(null); readonly loading = signal(false); readonly busy = signal(false);
  readonly error = signal(''); readonly message = signal(''); readonly historyError = signal(false); readonly criteriaLoading = signal(false);
  readonly regions = signal<CommercialReference[]>([]); readonly cities = signal<CommercialReference[]>([]); readonly activities = signal<CommercialReference[]>([]);
  readonly criteria = this.fb.nonNullable.group({ region: ['', Validators.required], city: ['', Validators.required], activity: ['', Validators.required] });
  readonly decision = this.fb.nonNullable.group({ target: [''], reason: [''] });
  constructor(){
    window.addEventListener('hashchange',this.changedLink);
    this.navigation=this.router.events.pipe(filter(event=>event instanceof NavigationEnd)).subscribe(()=>{
      this.receiveLink();
      // Browser history and Router state must both be clean. Otherwise a later
      // Angular navigation can restore a fragment already removed by hashchange.
      const address=this.router.parseUrl(this.router.url),fragment=new URLSearchParams(address.fragment||'');
      if(fragment.has('commercialToken')||fragment.has('commercialConfirmation')){
        fragment.delete('commercialToken');fragment.delete('commercialConfirmation');address.fragment=fragment.toString()||null;
        void this.router.navigateByUrl(address,{replaceUrl:true});
      }
    });
  }
  ngOnChanges(changes: SimpleChanges): void {
    if(changes['caseId'] && changes['caseId'].previousValue !== changes['caseId'].currentValue) {
      this.token = null; this.confirmationId = null; this.pending = null; this.message.set(''); this.error.set('');
      this.criteria.reset(); this.decision.reset(); this.regions.set([]); this.cities.set([]); this.activities.set([]);
    }
    this.readProtectedLink(); this.reload();
  }
  ngOnDestroy(): void { this.navigation.unsubscribe();window.removeEventListener('hashchange',this.changedLink);this.cancellation.next(); this.cancellation.complete(); this.token = null; this.confirmationId = null; this.pending = null; this.confirmationHandoff.clearFor(this.caseId); }
  private receiveLink():void{
    if(this.readProtectedLink()){this.pending=null;this.message.set('');this.error.set('');this.reload();}
  }
  private readProtectedLink(): boolean {
    const address=new URL(window.location.href);
    if(!this.caseId||!['/merchant/dossier','/merchant/dossier/'+this.caseId].includes(address.pathname))return false;
    const values=new URLSearchParams(address.hash.slice(1)),supplied=values.has('commercialToken')||values.has('commercialConfirmation');
    // Reuse the strict, dossier-bound parser also used before authentication.
    // An invalid replacement clears the previous intent, never reuses its token.
    if(supplied)this.confirmationHandoff.capture(address.pathname+address.search+address.hash);
    const remembered=this.confirmationHandoff.take(this.caseId);
    if(!supplied&&!remembered)return false;
    const token=remembered?.token||null,id=remembered?.confirmationId||null;
    const changed=token!==this.token||id!==this.confirmationId;
    this.token=token;this.confirmationId=id;return changed;
  }
  reload(): void {
    this.cancellation.next(); this.editable.emit(false); this.busy.set(false); this.workspace.set(null); this.loading.set(true); this.history.set([]); this.historyError.set(false); this.confirmation.set(null);
    if (!this.caseId) { this.loading.set(false); return; }
    this.service.workspace(this.caseId).pipe(takeUntil(this.cancellation)).subscribe({ next: view => {
      this.workspace.set(view); this.capabilities.emit(view); this.editable.emit(view.actions.includes('EDIT')); this.loading.set(false);
      if (view.state?.criteria) this.criteria.patchValue(view.state.criteria);
      this.service.history(this.caseId).pipe(takeUntil(this.cancellation)).subscribe({ next: h => this.history.set(h), error: () => this.historyError.set(true) });
      this.service.confirmation(this.caseId).pipe(takeUntil(this.cancellation)).subscribe({ next: c => this.confirmation.set(c), error: () => this.error.set('Le statut de confirmation est indisponible. Actualisez avant de poursuivre.') });
      if (this.has('SUBMIT') || this.has('CONFIRM')) {
        this.criteriaLoading.set(true);
        forkJoin({ regions: this.service.references(view.acquirerCode, 'REGION'), cities: this.service.references(view.acquirerCode, 'CITY'), activities: this.service.references(view.acquirerCode, 'ACTIVITY') })
          .pipe(takeUntil(this.cancellation)).subscribe({ next: refs => { this.regions.set(refs.regions); this.cities.set(refs.cities); this.activities.set(refs.activities); this.criteriaLoading.set(false); },
            error: () => { this.criteriaLoading.set(false); this.error.set('Les référentiels sont indisponibles. Aucune valeur ne sera déduite automatiquement.'); this.criteria.reset(); } });
      }
    }, error: () => { this.loading.set(false);this.token=null;this.confirmationId=null;this.confirmationHandoff.clearFor(this.caseId); this.error.set('Revue commerciale indisponible ou accès refusé.'); } });
  }
  has(action: string): boolean { return !!this.workspace()?.actions.includes(action); }
  hasProtectedLink(): boolean { return !!this.token && !!this.confirmationId; }
  validReason(): boolean { const reason = this.decision.controls.reason.value.trim(); return reason.length > 0 && reason.length <= 1000 && !/[\x00-\x1f\x7f]/.test(reason); }
  filteredCities(): CommercialReference[] { return this.cities().filter(c => c.extra === this.criteria.controls.region.value); }
  regionChanged(): void { this.criteria.controls.city.setValue(''); }
  act(action: string): void {
    if (!this.has(action) || this.busy()) return;
    if (['REASSIGN','COMPLEMENTS','RENEW_CONFIRMATION','ADOPT_HISTORY'].includes(action) && !this.validReason()) return;
    if (action === 'SUBMIT' && (this.criteria.invalid || this.criteriaLoading())) return;
    const command = this.commandFor(action); this.execute(action, this.service.command(this.caseId, action, command));
  }
  confirm(): void {
    if (!this.has('CONFIRM') || this.busy() || this.criteria.invalid || this.criteriaLoading() || !this.token || !this.confirmationId) return;
    this.execute('CONFIRM', this.service.confirm(this.caseId, this.commandFor('CONFIRM'), this.confirmationId, this.token));
  }
  private commandFor(action: string): CommercialCommand {
    const view = this.workspace()!;
    const signature = JSON.stringify({ action, caseId: this.caseId, caseVersion: view.caseVersion, workflowVersion: view.state?.version ?? -1,
      criteria: this.criteria.getRawValue(), decision: this.decision.getRawValue() });
    if (this.pending?.signature === signature) return this.pending.command;
    const command: CommercialCommand = { requestId: crypto.randomUUID(), expectedWorkflowVersion: view.state?.version ?? -1, expectedCaseVersion: view.caseVersion };
    if (action === 'SUBMIT' || action === 'CONFIRM') command.criteria = this.criteria.getRawValue();
    if (action === 'REASSIGN') command.targetMemberId = this.decision.controls.target.value;
    if (['REASSIGN','COMPLEMENTS','RENEW_CONFIRMATION','ADOPT_HISTORY'].includes(action)) command.reason = this.decision.controls.reason.value.trim();
    this.pending = { action, signature, command }; return command;
  }
  private execute(action: string, request: Observable<CommercialResult>): void {
    this.busy.set(true); this.error.set(''); this.message.set('');
    request.pipe(takeUntil(this.cancellation)).subscribe({ next: () => {
      this.busy.set(false); this.pending = null; this.decision.reset();
      if (action === 'CONFIRM') { this.token = null; this.confirmationId = null; }
      this.message.set(action === 'PREPARE_CONFIRMATION' || action === 'RENEW_CONFIRMATION' ?
        'Lien préparé et protégé. Sa préparation ne constitue pas une preuve d’envoi ou de réception.' : 'Action commerciale enregistrée.');
      this.reload(); this.changed.emit();
    }, error: e => {
      this.busy.set(false); this.error.set(e?.status === 409 ? 'Le dossier a changé ou cette action n’est plus disponible. Actualisez avant de poursuivre.' :
        e?.status === 403 ? 'Votre profil ne permet pas cette action sur ce dossier.' : 'L’action n’a pas été confirmée. Vous pouvez réessayer sans créer une nouvelle commande.');
      if (e?.status === 409 || e?.status === 403) { this.pending = null; this.reload(); }
    } });
  }
  phaseLabel(phase: string): string { return ({ DRAFT: 'Brouillon', AWAITING_CONFIRMATION: 'Confirmation personnelle attendue', IN_REVIEW: 'En revue commerciale', TO_DISTRIBUTE: 'À répartir', ASSIGNMENT_EXCEPTION: 'Exception d’affectation', COMPLEMENTS_REQUIRED: 'Compléments demandés', REEXAMINATION: 'À réexaminer', READY_FOR_BO: 'Prêt pour le Back-office', TRANSMITTED_BO: 'Transmis au Back-office' } as Record<string,string>)[phase] || phase; }
  actionLabel(action: string): string { return ({ INITIALIZE:'Création de la phase', ADOPT_HISTORY:'Adoption historique explicite', SUBMIT_AUTO:'Confirmation et soumission', PREPARE_ASSISTED:'Préparation du lien', RENEW_ASSISTED:'Renouvellement du lien', CONFIRM_ASSISTED:'Confirmation personnelle', REQUEST_COMPLEMENTS:'Demande de compléments', REASSIGN:'Réaffectation motivée', MARK_READY:'Validation commerciale', TRANSFER_BO:'Transmission au Back-office', DOSSIER_CHANGED:'Correction du dossier' } as Record<string,string>)[action] || action; }
  confirmationLabel(status: string): string { return ({PREPARED:'Préparée, envoi non attesté',EXPIRED:'Expirée',REVOKED:'Révoquée',CONFIRMED:'Confirmée'} as Record<string,string>)[status] || status; }
  deliveryLabel(status:string|undefined):string {return ({NOT_ATTEMPTED:'En attente de prise en charge',SENDING:'Tentative en cours',ACCEPTED:'Accepté par le relais SMTP ; réception et lecture non attestées',UNCERTAIN:'Résultat incertain ; aucun renvoi automatique. Un renouvellement explicite est nécessaire',BLOCKED:'Envoi bloqué ; vérifier les coordonnées du destinataire'} as Record<string,string>)[status||'']||'Envoi non attesté';}
}
