import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { DmasService } from '../../core/services/dmas.service';
import { CardRequest, AuthRequest } from '../../core/models/dmas.models';

type Tab = 'network' | 'keys' | 'cards' | 'auth';
interface LogEntry { ok: boolean; label: string; detail: string; }

@Component({
  selector: 'app-dmas',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './dmas.component.html',
  styleUrl: './dmas.component.scss',
})
export class DmasComponent {
  private dmas = inject(DmasService);

  readonly tab = signal<Tab>('network');
  readonly busy = signal(false);
  readonly log = signal<LogEntry[]>([]);

  // --- Cles ---
  memberGroupId = '';
  kekClear = '';

  // --- Cartes ---
  card: CardRequest = { pan: '', pin: '', balance: 0, currency: '', expiry: '' };
  lookupPan = '';
  balanceAmount = 0;

  // --- Test 0100 ---
  auth: AuthRequest = { DE002_PAN: '', DE004_AMOUNT: 0 };
  showAdvanced = signal(false);

  setTab(t: Tab): void { this.tab.set(t); }

  private run(label: string, obs: import('rxjs').Observable<unknown>): void {
    this.busy.set(true);
    obs.subscribe({
      next: (res) => {
        this.pushLog(true, label, JSON.stringify(res, null, 2));
        this.busy.set(false);
      },
      error: (err) => {
        const detail = err.error ? JSON.stringify(err.error, null, 2) : (err.message ?? 'error');
        this.pushLog(false, label, `HTTP ${err.status} — ${detail}`);
        this.busy.set(false);
      },
    });
  }

  private pushLog(ok: boolean, label: string, detail: string): void {
    this.log.update(l => [{ ok, label, detail }, ...l].slice(0, 20));
  }

  clearLog(): void { this.log.set([]); }

  // Reseau
  signon(): void { this.run('Sign-on', this.dmas.signon()); }
  signoff(): void { this.run('Sign-off', this.dmas.signoff()); }
  netStatus(): void { this.run('Network status', this.dmas.networkStatus()); }

  // Cles
  bootstrapKek(): void { this.run('Bootstrap KEK', this.dmas.bootstrapKek(this.memberGroupId, this.kekClear)); }
  exchangePek(): void { this.run('Exchange PEK', this.dmas.exchangePek(this.memberGroupId)); }

  // Cartes
  createCard(): void { this.run('Create card', this.dmas.createCard(this.card)); }
  getCard(): void { this.run('Get card', this.dmas.getCard(this.lookupPan)); }
  setBalance(): void { this.run('Set balance', this.dmas.setBalance(this.lookupPan, this.balanceAmount)); }

  // Test 0100
  sendAuth(): void { this.run('Authorize 0100', this.dmas.authorize(this.auth)); }
}
