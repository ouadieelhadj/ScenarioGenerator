import { Component, OnInit, Input, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MerchantOnboardingService, PersonalProspectAccepted, PersonalProspectTracking } from '../../core/services/merchant-onboarding.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-merchant-prospect',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <header><span class="eyebrow">ESPACE COMMERCIAL</span><h1>Créer un prospect commerçant</h1><p>FuturPayment attribue un identifiant personnel FP-U. Le compte reste inactif jusqu’à son activation par le commerçant.</p></header>
    @if (!created()) {<section class="panel">
      <form #prospectForm="ngForm" (ngSubmit)="submit()">
        <label>Adresse e-mail de la personne<input name="email" type="email" [(ngModel)]="email" required email maxlength="100" autocomplete="off" /></label>
        <label>Acquéreur
          <select name="acquirerId" [(ngModel)]="acquirerId" required>
            <option value="" disabled>Sélectionner un acquéreur autorisé</option>
            @for (acquirer of allowedAcquirers(); track acquirer.id) {
              <option [value]="acquirer.code">{{ acquirer.name }} ({{ acquirer.code }})</option>
            }
          </select>
        </label>
        @if (!loadingAcquirers() && allowedAcquirers().length === 0) {
          <div class="message error">Aucun acquéreur actif n'est autorisé pour votre identité.</div>
        }
        @if (error()) { <div class="message error">{{ error() }}</div> }
        <label class="consent"><input name="consent" type="checkbox" [(ngModel)]="consent" required /> Le consentement de la personne pour l’ouverture du dossier a été recueilli.</label>
        <button type="submit" [disabled]="loading() || prospectForm.invalid || !acquirerId">{{ loading() ? 'Enregistrement…' : 'Créer et inviter' }}</button>
      </form>
    </section>}
    @if (created()) {
      <section class="result" aria-live="polite">
        <i class="pi pi-check-circle"></i><div><h2>Demande enregistrée</h2><p>La préparation de l’invitation est suivie séparément. Cet accusé ne confirme ni l’envoi du courriel ni l’activation.</p>
        @if (tracking(); as current) {
          <p>État : <strong>{{ stateLabel(current.state) }}</strong></p>
          @if (current.personalId) {<p>Identifiant personnel : <strong>{{ current.personalId }}</strong></p>}
          @if (current.caseReference) {<p>Dossier : <strong>{{ current.caseReference }}</strong></p>}
          @if (current.caseId) {<a [routerLink]="[dossierRoute, current.caseId]">Ouvrir le dossier commercial</a>}
        }
        <p>Le lien personnel n’est jamais affiché au commercial.</p>
        @if (error()) {<p class="message error" role="alert">{{ error() }}</p>}
        <div class="actions"><button type="button" (click)="refresh()" [disabled]="refreshing()">Actualiser le suivi</button>
        <button type="button" (click)="newProspect()" [disabled]="refreshing()">Créer un autre prospect</button></div></div>
      </section>
    }
  `,
  styles: [`
    :host{display:block;color:var(--sg-text-primary,#172b43)}header{margin-bottom:20px}.eyebrow{font-size:12px;font-weight:700;letter-spacing:.12em;color:var(--sg-color-primary,#0758ad)}h1{margin:8px 0}p{color:var(--sg-text-muted,#526174)}.panel,.result{max-width:720px;padding:22px;background:var(--sg-bg-surface,#fff);border:1px solid var(--sg-border,#d8e0ea);border-radius:var(--sg-radius-lg,14px)}form,label{display:grid;gap:7px}form{gap:16px}input,select{padding:10px;border:1px solid var(--sg-border-strong,#a9b9cd);border-radius:var(--sg-radius,8px);background:var(--sg-bg-surface,#fff);color:var(--sg-text-primary,#172b43);min-width:0}button{justify-self:start;padding:11px 18px;border:0;border-radius:var(--sg-radius,8px);background:var(--sg-color-primary,#0758ad);color:white;cursor:pointer}button:disabled{opacity:.6}.result{display:flex;gap:14px;margin-top:18px}.result>i{font-size:28px;color:#16803c}.result h2{margin:0}.result>div{min-width:0;overflow-wrap:anywhere}.result a{color:var(--sg-color-primary,#0758ad);font-weight:600}.actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:14px}.consent{display:flex;gap:10px;align-items:flex-start;line-height:1.5}.consent input{margin-top:4px;flex-shrink:0;accent-color:var(--sg-color-primary,#0758ad)}.message,.warning{padding:10px;border-radius:var(--sg-radius,8px)}.error{background:#fff1f0;color:#b42318}.warning{background:#fff8e6;color:#8a4b00}
  `],
})
export class MerchantProspectComponent implements OnInit {
  @Input() dossierRoute='/merchant/dossier';
  private readonly service = inject(MerchantOnboardingService);
  private readonly destroyRef=inject(DestroyRef);
  private id=crypto.randomUUID();private lastContent='';
  email = '';
  acquirerId = '';
  consent=false;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly created = signal<PersonalProspectAccepted | null>(null);
  readonly tracking=signal<PersonalProspectTracking|null>(null);readonly refreshing=signal(false);
  readonly allowedAcquirers = signal<Array<{ id: string; code: string; name: string }>>([]);
  readonly loadingAcquirers = signal(true);

  ngOnInit(): void {
    this.service.allowedAcquirers().pipe(takeUntilDestroyed(this.destroyRef),finalize(() => this.loadingAcquirers.set(false))).subscribe({
      next: values => {
        this.allowedAcquirers.set(values);
        if (values.length === 1) this.acquirerId = values[0].code;
      },
      error: () => this.error.set('Le catalogue des acquéreurs autorisés est indisponible.'),
    });
  }

  submit(): void {
    if(this.loading()||!this.consent||!this.email||!this.acquirerId)return;
    const request={email:this.email.trim(),acquirerCode:this.acquirerId,consent:this.consent};const content=JSON.stringify(request);
    if(this.lastContent&&content!==this.lastContent)this.id=crypto.randomUUID();this.lastContent=content;
    const id=this.id;
    this.loading.set(true); this.error.set(null); this.created.set(null);
    this.tracking.set(null);
    this.service.createPersonalProspect(id,request)
      .pipe(takeUntilDestroyed(this.destroyRef),finalize(() => this.loading.set(false))).subscribe({
        next: value => {if(value.id!==id||value.status!=='REQUEST_RECEIVED'){this.error.set('La demande n’a pas été confirmée. Réessayez avec la même référence.');return;}
          this.created.set(value);this.id=crypto.randomUUID();this.lastContent='';this.refresh();},
        error: error => this.error.set(error.status===403?'Votre profil n’est pas autorisé pour cet acquéreur.':error.status===409?'La référence correspond à une demande différente. Vérifiez le suivi.':'La création n’a pas été confirmée. Vous pouvez réessayer sans doubler la demande.'),
      });
  }
  refresh():void{
    const request=this.created();if(!request||this.refreshing())return;this.refreshing.set(true);this.error.set(null);
    this.service.personalProspectTracking(request.id).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.refreshing.set(false))).subscribe({
      next:current=>{if(this.created()?.id===current.id)this.tracking.set(current);},
      error:()=>this.error.set('Le suivi est momentanément indisponible ou non autorisé. Aucun nouveau dossier n’a été créé.')});
  }
  newProspect():void{this.created.set(null);this.tracking.set(null);this.error.set(null);this.email='';this.consent=false;this.id=crypto.randomUUID();this.lastContent='';}
  stateLabel(state:string):string{return ({WAIT_IDENTITY:'Identité en préparation',IDENTITY_WORKING:'Identité en cours de préparation',MAIL_PENDING:'Invitation prête à envoyer',MAIL_SENDING:'Envoi en cours',MAIL_ACCEPTED:'Invitation acceptée par le relais de messagerie',MAIL_UNCERTAIN:'Envoi à vérifier par le Back-office',ACTIVATED:'Compte activé',EXPIRED:'Invitation expirée',NEEDS_ATTENTION:'Contrôle du Back-office nécessaire'} as Record<string,string>)[state]??'État à vérifier';}
}
