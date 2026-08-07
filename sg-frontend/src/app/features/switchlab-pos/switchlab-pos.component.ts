import { DatePipe, JsonPipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, Observable } from 'rxjs';
import { SwitchLabMtipSentinelRequest, SwitchLabPosExecution, SwitchLabPosScenarioDefinition } from '../../core/models/product-contracts.models';
import { SwitchLabOperationsService } from '../../core/services/switchlab-operations.service';

type Tab = 'transaction' | 'field-map' | 'repeat' | 'rki' | 'mtip' | 'history';

@Component({
  selector: 'app-switchlab-pos',
  standalone: true,
  imports: [FormsModule, DatePipe, JsonPipe, NgTemplateOutlet],
  template: `
    <div class="page-header"><div><p class="eyebrow">SwitchLab · Lot 2</p><h1><i class="pi pi-desktop"></i> TPE & POS</h1><p>Transactions, field-map, repeat, RKI et campagne MTIP sentinelle.</p></div></div>
    <nav class="tabs">
      @for (item of tabs; track item.code) { <button type="button" [class.active]="tab() === item.code" (click)="tab.set(item.code)"><i [class]="item.icon"></i>{{ item.label }}</button> }
    </nav>
    @if (error()) { <div class="notice error"><i class="pi pi-exclamation-triangle"></i>{{ error() }}</div> }

    @switch (tab()) {
      @case ('transaction') {
        <form class="card form-grid" (ngSubmit)="sendTransaction()">
          <h2>Nouvelle transaction</h2>
          <label>MTI<input name="mti" [(ngModel)]="transaction.mti" required /></label>
          <label>Processing code<input name="processingCode" [(ngModel)]="transaction.processingCode" required /></label>
          <label>PAN<input name="pan" type="password" autocomplete="off" [(ngModel)]="transaction.pan" required /></label>
          <label>Expiration YYMM<input name="expiry" [(ngModel)]="transaction.expiry" required /></label>
          <label>Montant ISO (12 chiffres)<input name="amount" [(ngModel)]="transaction.amount" required /></label>
          <label>Entry mode<input name="entryMode" [(ngModel)]="transaction.entryMode" /></label>
          <label>Terminal<input name="terminalId" [(ngModel)]="transaction.terminalId" required /></label>
          <label>Marchand<input name="merchantId" [(ngModel)]="transaction.merchantId" required /></label>
          <label class="check"><input type="checkbox" name="mac" [(ngModel)]="transaction.macEnabled" /> MAC activé</label>
          <button class="primary" [disabled]="loading()"><i class="pi pi-send"></i> Envoyer</button>
        </form>
      }
      @case ('field-map') {
        <form class="card form-grid" (ngSubmit)="sendFieldMap()">
          <h2>Transaction field-map</h2>
          <label>MTI<input name="fieldMti" [(ngModel)]="fieldMap.mti" required /></label>
          <label class="full">Champs texte JSON<textarea name="fields" [(ngModel)]="fieldMap.fields" rows="8" required></textarea></label>
          <label class="full">Champs binaires JSON<textarea name="binaryFields" [(ngModel)]="fieldMap.binaryFields" rows="5"></textarea></label>
          <label>Champs à supprimer<input name="unset" [(ngModel)]="fieldMap.unsetFields" placeholder="2, 35, 55" /></label>
          <label>PIN clair temporaire<input name="fieldPin" type="password" autocomplete="off" [(ngModel)]="fieldMap.pin" /></label>
          <label class="check"><input type="checkbox" name="fieldMac" [(ngModel)]="fieldMap.macEnabled" /> MAC activé</label>
          <label class="check"><input type="checkbox" name="validate" [(ngModel)]="fieldMap.validate" /> Validation stricte</label>
          <button class="primary" [disabled]="loading()"><i class="pi pi-send"></i> Envoyer le field-map</button>
        </form>
      }
      @case ('repeat') {
        <form class="card compact" (ngSubmit)="repeat()"><h2>Repeat de la dernière transaction</h2><label>Terminal<input name="repeatTerminal" [(ngModel)]="repeatForm.terminalId" required /></label><label class="check"><input type="checkbox" name="repeatMac" [(ngModel)]="repeatForm.macEnabled" /> MAC activé</label><button class="primary" [disabled]="loading()"><i class="pi pi-replay"></i> Répéter</button></form>
      }
      @case ('rki') {
        <section class="card"><h2>Assistant RKI</h2><p>Les clés claires ne sont ni saisies ni affichées par le portail. Elles restent dans le simulateur autorisé.</p><div class="button-row"><button class="primary" type="button" (click)="startRki(false)" [disabled]="loading()">1. Demander le changement</button><button type="button" (click)="confirmRki()" [disabled]="loading()">2. Confirmer les statuts</button><button type="button" (click)="startRki(true)" [disabled]="loading()">Exécuter avec confirmation</button></div></section>
      }
      @case ('mtip') {
        <section class="card catalog">@for (scenario of catalog(); track scenario.code) { <div><span class="badge">{{ scenario.classification }}</span><h2>{{ scenario.code }}</h2><strong>{{ scenario.label }}</strong><p>{{ scenario.objective }}</p><ul>@for (expected of scenario.expectedResults; track expected) { <li>{{ expected }}</li> }</ul></div> }</section>
        <form class="card form-grid" (ngSubmit)="runSentinel()">
          <h2>Paramètres de certification</h2><div class="notice full"><i class="pi pi-shield"></i>Le PAN et le PIN ne sont pas conservés. L'historique contient uniquement un PAN masqué et aucun PIN.</div>
          <label>PAN de la carte MCD01<input name="sentinelPan" type="password" autocomplete="off" [(ngModel)]="sentinel.pan" required /></label>
          <label>Expiration YYMM<input name="sentinelExpiry" [(ngModel)]="sentinel.expiry" required /></label>
          <label>PIN<input name="sentinelPin" type="password" autocomplete="off" [(ngModel)]="sentinel.pin" required /></label>
          <label>Montant ISO<input name="sentinelAmount" [(ngModel)]="sentinel.amount" required /></label>
          <label>Terminal<input name="sentinelTerminal" [(ngModel)]="sentinel.terminalId" required /></label>
          <label>Marchand<input name="sentinelMerchant" [(ngModel)]="sentinel.merchantId" required /></label>
          <label class="check"><input type="checkbox" name="sentinelMac" [(ngModel)]="sentinel.macEnabled" /> MAC activé</label>
          <button class="primary" [disabled]="loading()"><i class="pi pi-play"></i> Exécuter MCD01.Test.01.Scenario.01</button>
        </form>
      }
      @case ('history') { <ng-container *ngTemplateOutlet="historyView" /> }
    }

    @if (current(); as execution) {
      <section class="card result" [class.failed]="execution.verdict === 'FAILED'"><header><div><p class="eyebrow">Dernière exécution</p><h2>{{ execution.operation }}</h2></div><span class="verdict">{{ execution.verdict }}</span></header><dl><div><dt>Corrélation</dt><dd><code>{{ execution.correlationId }}</code></dd></div><div><dt>Durée</dt><dd>{{ execution.elapsedMillis }} ms</dd></div><div><dt>Attendu</dt><dd>{{ execution.expectedResult }}</dd></div></dl><h3>Réponse obtenue</h3><pre>{{ execution.response | json }}</pre></section>
    }

    <ng-template #historyView><section class="card"><header><h2>Historique POS</h2><button type="button" (click)="reloadHistory()"><i class="pi pi-refresh"></i> Actualiser</button></header>@if (!history().length) { <div class="empty">Aucune exécution enregistrée dans cette instance BFF.</div> } @else { <div class="history">@for (execution of history(); track execution.executionId) { <button type="button" (click)="current.set(execution)"><time>{{ execution.startedAt | date:'medium' }}</time><strong>{{ execution.operation }}</strong><span [class.failed-text]="execution.verdict === 'FAILED'">{{ execution.verdict }}</span><code>{{ execution.correlationId }}</code></button> }</div> }</section></ng-template>
  `,
  styles: [`
    .page-header h1 { display:flex; gap:10px; align-items:center; margin:0; font-size:21px; } .page-header i { color:var(--sg-color-primary); } .page-header p { color:var(--sg-text-muted); }
    .eyebrow { margin:0 0 5px; text-transform:uppercase; letter-spacing:.08em; font-size:11px; color:var(--sg-text-muted); }
    .tabs { display:flex; flex-wrap:wrap; gap:7px; margin:18px 0; } button { border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:9px 13px; background:var(--sg-bg-surface); color:var(--sg-text-primary); cursor:pointer; } button.active, button.primary { color:white; background:var(--sg-color-primary); border-color:var(--sg-color-primary); } button i { margin-right:6px; }
    .card, .notice { padding:18px; background:var(--sg-bg-surface); border:1px solid var(--sg-border); border-radius:var(--sg-radius); margin-top:14px; } .card h2 { margin:0 0 14px; font-size:17px; }
    .form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:13px; } .form-grid h2, .form-grid .full { grid-column:1 / -1; } label { display:flex; flex-direction:column; gap:5px; color:var(--sg-text-muted); font-size:12px; } input, textarea { border:1px solid var(--sg-border-strong); border-radius:var(--sg-radius); padding:9px; background:var(--sg-bg-surface); color:var(--sg-text-primary); font-family:inherit; } textarea { font-family:monospace; } label.check { flex-direction:row; align-items:center; } .compact { display:flex; align-items:end; gap:14px; flex-wrap:wrap; } .compact h2 { width:100%; }
    .button-row { display:flex; gap:9px; flex-wrap:wrap; } .notice { color:var(--sg-text-muted); } .notice.error, .failed-text { color:#b42318; } .notice i { margin-right:7px; }
    .catalog .badge { display:inline-block; padding:4px 8px; border-radius:999px; background:var(--sg-bg-muted); font-size:11px; } .catalog li { margin:5px 0; }
    .result header, .card > header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; } .verdict { color:#16803c; font-weight:700; } .result.failed .verdict { color:#b42318; } dl { display:grid; gap:7px; } dl div { display:grid; grid-template-columns:110px 1fr; } dt { color:var(--sg-text-muted); } pre { overflow:auto; max-height:340px; background:var(--sg-bg-muted); padding:12px; border-radius:var(--sg-radius); }
    .history { display:grid; gap:7px; margin-top:12px; } .history button { display:grid; grid-template-columns:180px 1fr 80px 300px; text-align:left; gap:9px; } .history code { overflow:hidden; text-overflow:ellipsis; } .empty { padding:25px; text-align:center; color:var(--sg-text-muted); }
    @media (max-width:800px) { .form-grid { grid-template-columns:1fr; } .form-grid h2, .form-grid .full { grid-column:auto; } .history button { grid-template-columns:1fr 80px; } .history code { grid-column:1/-1; } }
  `],
})
export class SwitchLabPosComponent implements OnInit {
  private readonly service = inject(SwitchLabOperationsService);
  readonly tab = signal<Tab>('transaction');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly catalog = signal<SwitchLabPosScenarioDefinition[]>([]);
  readonly history = signal<SwitchLabPosExecution[]>([]);
  readonly current = signal<SwitchLabPosExecution | null>(null);
  readonly tabs = [
    { code: 'transaction' as Tab, label: 'Transaction', icon: 'pi pi-send' }, { code: 'field-map' as Tab, label: 'Field-map', icon: 'pi pi-list' },
    { code: 'repeat' as Tab, label: 'Repeat', icon: 'pi pi-replay' }, { code: 'rki' as Tab, label: 'RKI', icon: 'pi pi-key' },
    { code: 'mtip' as Tab, label: 'MTIP sentinelle', icon: 'pi pi-verified' }, { code: 'history' as Tab, label: 'Historique', icon: 'pi pi-history' },
  ];
  transaction = { mti: '0200', processingCode: '000000', pan: '', expiry: '', amount: '000000001000', entryMode: '051', conditionCode: '00', terminalId: 'TERM0001', merchantId: 'MERCHANT0000001', macEnabled: true };
  fieldMap = { mti: '0200', fields: '{\n  "3": "000000",\n  "4": "000000001000"\n}', binaryFields: '{}', unsetFields: '', pin: '', macEnabled: true, validate: true };
  repeatForm = { terminalId: 'TERM0001', macEnabled: true };
  sentinel: SwitchLabMtipSentinelRequest = { pan: '', expiry: '', pin: '', amount: '000000008000', terminalId: 'TERM0001', merchantId: 'MERCHANT0000001', macEnabled: true };

  ngOnInit(): void { forkJoin({ catalog: this.service.posCatalog(), history: this.service.posHistory() }).subscribe({ next: data => { this.catalog.set(data.catalog); this.history.set(data.history); }, error: () => this.error.set('Le catalogue POS n’est pas disponible.') }); }
  sendTransaction(): void { const request = { ...this.transaction }; this.run(this.service.sendPosTransaction(request), () => { this.transaction.pan = ''; }); }
  sendFieldMap(): void { try { const request = { mti: this.fieldMap.mti, fields: JSON.parse(this.fieldMap.fields), binaryFields: JSON.parse(this.fieldMap.binaryFields || '{}'), unsetFields: this.fieldMap.unsetFields.split(',').map(v => Number(v.trim())).filter(Number.isFinite), pin: this.fieldMap.pin || null, macEnabled: this.fieldMap.macEnabled, validate: this.fieldMap.validate }; this.run(this.service.sendPosFieldMap(request), () => { this.fieldMap.pin = ''; }); } catch { this.error.set('Le JSON du field-map est invalide.'); } }
  repeat(): void { this.run(this.service.repeatPos(this.repeatForm.terminalId, this.repeatForm.macEnabled)); }
  startRki(confirm: boolean): void { this.run(this.service.startPosRki(confirm)); }
  confirmRki(): void { this.run(this.service.confirmPosRki()); }
  runSentinel(): void { const request = { ...this.sentinel }; this.run(this.service.runMtipSentinel(request), () => { this.sentinel.pan = ''; this.sentinel.pin = ''; }); }
  reloadHistory(): void { this.service.posHistory().subscribe({ next: items => this.history.set(items), error: () => this.error.set('Historique indisponible.') }); }
  private run(operation: Observable<SwitchLabPosExecution>, clear: () => void = () => undefined): void { this.loading.set(true); this.error.set(null); operation.pipe(finalize(() => { this.loading.set(false); clear(); })).subscribe({ next: result => { this.current.set(result); this.history.update(items => [result, ...items.filter(item => item.executionId !== result.executionId)]); }, error: () => this.error.set('L’opération POS a échoué.') }); }
}
