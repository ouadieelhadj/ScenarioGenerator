import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MerchantDocument, MerchantDossier } from '../../core/models/merchant-onboarding.models';
import { MerchantOnboardingService } from '../../core/services/merchant-onboarding.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <header><h1>Revue KYC</h1><p>Controle des pieces par un acteur distinct du deposant.</p></header>
    @if (message()) { <div class="notice">{{ message() }}</div> }
    <div class="layout"><aside><h2>File a traiter</h2>@for (item of queue(); track item.id) { <button (click)="select(item)"><strong>{{ item.reference }}</strong><span>{{ item.legalName }}</span></button> } @empty { <p>Aucun dossier KYC en attente.</p> }</aside>
    <main>@if (dossier(); as item) { <h2>{{ item.reference }} — {{ item.legalName }}</h2><p>{{ item.registrationNumber }} · MCC {{ item.mcc }}</p>
      @for (document of documents(); track document.id) { <article><div><strong>{{ document.type }}</strong><p>v{{ document.version }} · {{ document.contentType }} · {{ document.contentLength }} octets</p><small>{{ document.reviewStatus }}</small></div><div><button (click)="openDocument(document)">Consulter</button><button (click)="review(document,true)" [disabled]="document.reviewStatus === 'ACCEPTED'">Accepter</button><button class="danger" (click)="review(document,false)" [disabled]="document.reviewStatus === 'REJECTED'">Rejeter</button></div></article> }
      <label>Motif de complements ou rejet<textarea [(ngModel)]="reason" rows="3"></textarea></label>
      <div class="actions"><button (click)="validate()" [disabled]="!allAccepted()">Valider le KYC</button><button (click)="complements()" [disabled]="!reason.trim()">Demander des complements</button><button class="danger" (click)="rejectKyc()" [disabled]="!reason.trim()">Rejeter le KYC</button></div>
    }</main></div>
  `,
  styles:[`.layout{display:grid;grid-template-columns:280px 1fr;gap:18px}aside,main{padding:18px;border:1px solid var(--sg-border);border-radius:12px;background:var(--sg-bg-surface)}aside button{display:grid;width:100%;text-align:left;padding:12px;margin:8px 0;border:1px solid var(--sg-border);border-radius:8px;background:transparent;color:inherit}aside span,p{color:var(--sg-text-muted)}article{display:flex;justify-content:space-between;gap:12px;padding:14px 0;border-bottom:1px solid var(--sg-border)}button{padding:9px 12px;border:0;border-radius:8px;cursor:pointer;margin:3px;background:var(--sg-color-primary);color:#fff}.danger{background:#b42318}label{display:grid;gap:6px;margin-top:16px}textarea{padding:10px}.actions{display:flex;flex-wrap:wrap;margin-top:12px}.notice{padding:12px;border:1px solid #16803c;border-radius:8px;margin:12px 0}@media(max-width:760px){.layout{grid-template-columns:1fr}}`]
})
export class MerchantReviewComponent implements OnInit {
  private service = inject(MerchantOnboardingService);
  readonly queue = signal<MerchantDossier[]>([]); readonly dossier = signal<MerchantDossier|null>(null); readonly documents = signal<MerchantDocument[]>([]); readonly message = signal(''); reason='';
  ngOnInit(): void { this.reload(); }
  reload(): void { this.service.reviewQueue().subscribe(items => this.queue.set(items)); }
  select(item: MerchantDossier): void { this.dossier.set(item); this.service.reviewDocuments(item.id).subscribe(documents => this.documents.set(documents)); }
  review(document: MerchantDocument, accepted: boolean): void { const reason = accepted ? null : (this.reason.trim() || 'Piece non conforme'); this.service.reviewDocument(document.id, accepted, reason).subscribe(value => { this.documents.update(items => items.map(item => item.id === value.id ? value : item)); }); }
  openDocument(document: MerchantDocument): void { this.service.reviewDocumentContent(document.id).subscribe(blob => { const objectUrl = URL.createObjectURL(blob); window.open(objectUrl, '_blank', 'noopener,noreferrer'); setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000); }); }
  allAccepted(): boolean { const latest = new Map<string,MerchantDocument>(); for (const document of this.documents()) if (!latest.has(document.type)) latest.set(document.type,document); return ['LEGAL_EXISTENCE','REPRESENTATIVE_IDENTITY','BANK_ACCOUNT_PROOF'].every(type => latest.get(type)?.reviewStatus === 'ACCEPTED'); }
  validate(): void { if (!this.dossier()) return; this.service.validateKyc(this.dossier()!.id).subscribe(() => { this.message.set('KYC valide. Le Maker peut maintenant soumettre le dossier.'); this.clear(); }); }
  complements(): void { if (!this.dossier()) return; this.service.requestComplements(this.dossier()!.id,this.reason).subscribe(() => { this.message.set('Demande de complements envoyee.'); this.clear(); }); }
  rejectKyc(): void { if (!this.dossier()) return; this.service.rejectKyc(this.dossier()!.id,this.reason).subscribe(() => { this.message.set('KYC rejete.'); this.clear(); }); }
  private clear(): void { this.dossier.set(null); this.documents.set([]); this.reason=''; this.reload(); }
}
