import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { PortConfigService } from '../../core/services/port-config.service';

type ServiceKey = 'orchestrator' | 'acquirer' | 'issuer';

interface ServiceRow {
  key: ServiceKey;
  label: string;
  frontPort: number;      // port connu du front
  serverPort: number | null; // port reel lu du back (null si injoignable)
  newPort: number;
  status: 'idle' | 'loading' | 'restarting' | 'error';
  message: string | null;
}

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './config.component.html',
  styleUrl: './config.component.scss',
})
export class ConfigComponent implements OnInit {
  private portSvc = inject(PortConfigService);

  readonly rows = signal<ServiceRow[]>([]);

  ngOnInit(): void {
    const defs: { key: ServiceKey; label: string }[] = [
      { key: 'orchestrator', label: 'Orchestrateur' },
      { key: 'acquirer', label: 'Acquéreur (DMAS)' },
      { key: 'issuer', label: 'Émetteur (DMAS)' },
    ];
    this.rows.set(defs.map(d => ({
      key: d.key, label: d.label,
      frontPort: this.portSvc.frontPort(d.key),
      serverPort: null,
      newPort: this.portSvc.frontPort(d.key),
      status: 'idle', message: null,
    })));
    this.rows().forEach(r => this.loadServerPort(r.key));
  }

  private update(key: ServiceKey, patch: Partial<ServiceRow>): void {
    this.rows.update(rows => rows.map(r => r.key === key ? { ...r, ...patch } : r));
  }

  loadServerPort(key: ServiceKey): void {
    this.update(key, { status: 'loading', message: null });
    this.portSvc.getServerPort(key).subscribe({
      next: (info) => this.update(key, { serverPort: info.currentPort, newPort: info.currentPort, status: 'idle' }),
      error: () => this.update(key, { serverPort: null, status: 'error', message: 'portConfig.unreachable' }),
    });
  }

  apply(row: ServiceRow): void {
    const port = Number(row.newPort);
    if (!port || port < 1024 || port > 65535) {
      this.update(row.key, { status: 'error', message: 'portConfig.invalidPort' });
      return;
    }
    this.update(row.key, { status: 'restarting', message: 'portConfig.restarting' });
    this.portSvc.changePort(row.key, port).subscribe({
      next: (res) => {
        if (res.restarting) {
          // Met a jour la config front pour suivre le back
          this.portSvc.updateFrontPort(row.key, port);
          // Attendre le redemarrage puis re-tester
          setTimeout(() => this.verifyReconnect(row.key, port), 8000);
        } else {
          this.update(row.key, { status: 'idle', message: null, serverPort: port });
        }
      },
      error: () => this.update(row.key, { status: 'error', message: 'portConfig.changeError' }),
    });
  }

  private verifyReconnect(key: ServiceKey, port: number): void {
    this.portSvc.getServerPort(key).subscribe({
      next: (info) => this.update(key, {
        serverPort: info.currentPort, frontPort: port, newPort: info.currentPort,
        status: 'idle', message: 'portConfig.reconnected',
      }),
      error: () => this.update(key, { status: 'error', message: 'portConfig.unreachable' }),
    });
  }
}

