import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { PersonalMerchantAssociationService, PersonalAssociation, PersonalContext } from '../../core/services/personal-merchant-association.service';
import type { PersonalContextSession } from '../../core/auth/personal-context-session';

/** One functional view for Web and APK; session persistence is owned by each host. */
@Component({
  selector: 'app-personal-merchant-associations', standalone: true, imports: [DatePipe],
  template: `
    <section aria-labelledby="personal-associations-title">
      <h1 id="personal-associations-title">Mes rattachements</h1>
      <p>Identifiant personnel : <strong>{{ currentLogin }}</strong></p>
      <p>Contexte actif : <strong>{{ currentAcquirer || 'Aucun' }}</strong></p>
      <p>Un même compte peut accéder à plusieurs commerces autorisés. Accepter un rattachement ne crée ni compte ni mot de passe.</p>
      <button type="button" (click)="refresh()" [disabled]="busy()">Actualiser</button>
      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
      @if (notice()) { <p role="status" class="notice">{{ notice() }}</p> }
      @if (busy()) { <p role="status">Traitement en cours…</p> }
      <h2>Demandes et droits accordés</h2>
      @if (!busy() && rows().length === 0) { <p>Aucun rattachement sur cette page.</p> }
      @for (row of rows(); track row.id) {
        <article class="association" [attr.data-association-id]="row.id">
          <h3>{{ row.hierarchyLabel }}</h3>
          <dl><dt>Acquéreur</dt><dd>{{ row.acquirerCode }}</dd><dt>Rôle proposé</dt><dd>{{ role(row.role) }}</dd>
            <dt>État</dt><dd>{{ state(row.state) }}</dd>
            @if (row.acceptedAt) { <dt>Accepté le</dt><dd>{{ row.acceptedAt | date:'dd/MM/yyyy HH:mm' }}</dd> }
            @if (row.revokedAt) { <dt>Révoqué le</dt><dd>{{ row.revokedAt | date:'dd/MM/yyyy HH:mm' }}</dd> }
          </dl>
          @if (row.state === 'PENDING') {
            <button type="button" [disabled]="busy()" (click)="confirmation.set(row)">Examiner et accepter</button>
          }
        </article>
      }
      <nav aria-label="Pagination des rattachements">
        <button type="button" (click)="page=page-1; refresh()" [disabled]="busy() || page===0">Précédent</button>
        <span>Page {{ page+1 }}</span>
        <button type="button" (click)="page=page+1; refresh()" [disabled]="busy() || rows().length<50">Suivant</button>
      </nav>
      @if (confirmation(); as row) {
        <section class="confirmation" role="region" aria-label="Confirmation du rattachement">
          <h2>Confirmer votre rattachement</h2>
          <p>Vous acceptez le rôle <strong>{{ role(row.role) }}</strong> pour <strong>{{ row.hierarchyLabel }}</strong>
            chez <strong>{{ row.acquirerCode }}</strong>, avec votre compte {{ currentLogin }}.</p>
          <button type="button" (click)="accept(row)" [disabled]="busy()">Confirmer le rattachement</button>
          <button type="button" (click)="confirmation.set(null)" [disabled]="busy()">Annuler</button>
        </section>
      }
      <h2>Choisir mon acquéreur actif</h2>
      @for (context of contexts(); track context.id) {
        <article class="context"><h3>{{ context.label }}</h3><p>{{ context.code }}</p>
          <button type="button" (click)="select(context.code)" [disabled]="busy() || context.code===currentAcquirer">
            {{ context.code===currentAcquirer ? 'Contexte actif' : 'Utiliser ce contexte' }}</button>
        </article>
      }
    </section>
  `,
  styles: [`:host{display:block}section{max-width:960px;margin:auto;padding:12px}button{padding:10px 14px;margin:4px;border:1px solid #075aa7;border-radius:6px;background:#075aa7;color:#fff;cursor:pointer}button:disabled{opacity:.55;cursor:default}.association,.context,.confirmation{border:1px solid #cbd5e1;border-radius:10px;padding:16px;margin:12px 0;background:#fff;color:#17253a}.confirmation{border:2px solid #075aa7}dl{display:grid;grid-template-columns:minmax(100px,1fr) 2fr;gap:8px}dd{margin:0;overflow-wrap:anywhere}h1,h2,h3{line-height:1.3}.error{color:#a40019}.notice{color:#075d37}nav{display:flex;align-items:center;gap:8px;flex-wrap:wrap}`],
})
export class PersonalMerchantAssociationsView implements OnInit {
  @Input() currentLogin = ''; @Input() currentAcquirer = '';
  @Output() contextSelected = new EventEmitter<{ session: PersonalContextSession; requested: string }>();
  private readonly api = inject(PersonalMerchantAssociationService); private readonly destroy = inject(DestroyRef);
  readonly rows = signal<PersonalAssociation[]>([]); readonly contexts = signal<PersonalContext[]>([]);
  readonly confirmation = signal<PersonalAssociation | null>(null);
  readonly busy = signal(false); readonly error = signal(''); readonly notice = signal(''); page = 0;
  ngOnInit(): void { this.refresh(); }
  refresh(): void {
    if (this.busy()) return;
    this.busy.set(true); this.rows.set([]); this.contexts.set([]); this.error.set(''); this.confirmation.set(null);
    forkJoin({ rows: this.api.mine(this.page), contexts: this.api.contexts() }).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => { this.rows.set(result.rows); this.contexts.set(result.contexts); this.busy.set(false); },
      error: error => { this.busy.set(false); this.failure(error.status); },
    });
  }
  accept(row: PersonalAssociation): void {
    if (this.busy() || this.confirmation()?.id !== row.id) return;
    this.busy.set(true); this.error.set('');
    this.api.accept(row).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.busy.set(false); this.notice.set('Rattachement accepté. Vos autres droits sont conservés.'); this.refresh(); },
      error: error => { this.busy.set(false); this.failure(error.status); },
    });
  }
  select(requested: string): void {
    if (this.busy() || !this.contexts().some(c => c.code === requested)) return;
    this.busy.set(true); this.error.set('');
    this.api.select(requested).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: session => { this.busy.set(false); this.contextSelected.emit({ session, requested }); },
      error: error => { this.busy.set(false); this.failure(error.status); },
    });
  }
  rejectContext(): void { this.error.set('Réponse de contexte refusée. Actualisez ou reconnectez-vous.'); }
  role(value: string): string { return ({ ADMIN:'Administrateur', MAKER:'Préparateur (Maker)', CHECKER:'Valideur (Checker)', VIEWER:'Consultation', ACCOUNTING:'Comptabilité' } as Record<string,string>)[value] || 'Rôle à vérifier'; }
  state(value: string): string { return ({ PENDING:'À accepter', ACTIVE:'Accepté', REVOKED:'Révoqué' } as Record<string,string>)[value] || 'État à vérifier'; }
  private failure(status: number): void {
    this.notice.set('');
    this.error.set(status === 403 ? 'Vous n’êtes pas autorisé à effectuer cette action.' : status === 409
      ? 'Le rattachement a changé. Actualisez avant de réessayer.' : 'Action indisponible. Actualisez le statut avant de réessayer.');
  }
}
