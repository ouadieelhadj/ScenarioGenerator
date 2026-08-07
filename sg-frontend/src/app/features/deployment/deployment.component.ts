import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { DeploymentService } from '../../core/services/deployment.service';
import {
  DatabaseType, DeploymentCatalog, DeploymentClient, DeploymentEnvironment,
  DeploymentExecution, DeploymentLicense, DeploymentModule, PreflightReport, ShellType, TargetOs,
} from '../../core/models/deployment.models';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PORTAL_PRODUCT } from '../../core/product/product.config';

type Section = 'overview' | 'clients' | 'environments' | 'modules' | 'licenses' | 'executions';

@Component({
  selector: 'app-deployment',
  standalone: true,
  imports: [FormsModule, TranslatePipe, HasPermissionDirective],
  templateUrl: './deployment.component.html',
  styleUrl: './deployment.component.scss',
})
export class DeploymentComponent implements OnInit {
  private service = inject(DeploymentService);
  private route = inject(ActivatedRoute);
  private product = inject(PORTAL_PRODUCT);
  readonly isSwitchLab = this.product.code === 'SWITCHLAB';

  readonly section = this.route.snapshot.data['section'] as Section;
  readonly catalog = signal<DeploymentCatalog | null>(null);
  readonly clients = signal<DeploymentClient[]>([]);
  readonly environments = signal<DeploymentEnvironment[]>([]);
  readonly licenses = signal<DeploymentLicense[]>([]);
  readonly executions = signal<DeploymentExecution[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly report = signal<PreflightReport | null>(null);
  readonly selectedClientId = signal<number | null>(null);
  readonly selectedEnvironmentId = signal<number | null>(null);
  readonly showClientForm = signal(false);
  readonly showEnvironmentForm = signal(false);

  clientForm: DeploymentClient = { code: '', legalName: '', commercialName: '', countryCode: 'MA', currencyCode: '504' };
  environmentForm: DeploymentEnvironment = this.emptyEnvironment();
  variableReferencesText = '';
  licenseForm = { validFrom: this.dateOffset(0), validUntil: this.dateOffset(365), bundleVersion: '1.0.0-SNAPSHOT' };
  executionAction = 'VALIDATE';
  readonly executionActions = ['VALIDATE', 'PLAN', 'INSTALL', 'START', 'STATUS', 'STOP', 'UPGRADE', 'ROLLBACK', 'LOGS'];

  ngOnInit(): void {
    forkJoin({ catalog: this.service.catalog(), clients: this.service.clients(),
      licenses: this.service.licenses(), executions: this.service.executions() }).subscribe({
      next: ({ catalog, clients, licenses, executions }) => {
        this.catalog.set(catalog);
        this.clients.set(clients);
        this.licenses.set(licenses);
        this.executions.set(executions);
        this.loading.set(false);
        if (clients[0]?.id) this.selectClient(clients[0].id);
      },
      error: () => { this.error.set('deployment.errors.load'); this.loading.set(false); },
    });
  }

  selectClient(clientId: number): void {
    this.selectedClientId.set(clientId);
    this.environmentForm.clientId = clientId;
    this.service.environments(clientId).subscribe({
      next: environments => {
        this.environments.set(environments);
        const current = this.selectedEnvironmentId();
        if (!current || !environments.some(environment => environment.id === current)) {
          this.selectedEnvironmentId.set(environments[0]?.id ?? null);
        }
      },
      error: () => this.error.set('deployment.errors.environments'),
    });
  }

  createClient(): void {
    this.saving.set(true);
    this.service.createClient(this.clientForm).subscribe({
      next: client => {
        this.clients.update(items => [...items, client]);
        this.showClientForm.set(false);
        this.saving.set(false);
        this.clientForm = { code: '', legalName: '', commercialName: '', countryCode: 'MA', currencyCode: '504' };
        if (client.id) this.selectClient(client.id);
      },
      error: () => { this.error.set('deployment.errors.save'); this.saving.set(false); },
    });
  }

  createEnvironment(): void {
    const clientId = this.selectedClientId();
    if (!clientId) { this.error.set('deployment.errors.clientRequired'); return; }
    try {
      this.environmentForm.clientId = clientId;
      if (this.isSwitchLab) {
        this.environmentForm.memberModules = [];
        this.environmentForm.membersBundlePath = '';
      }
      this.environmentForm.variableReferences = this.parseVariableReferences(this.variableReferencesText);
    } catch {
      this.error.set('deployment.errors.variables');
      return;
    }
    this.saving.set(true);
    this.service.createEnvironment(this.environmentForm).subscribe({
      next: environment => {
        this.environments.update(items => [...items, environment]);
        this.showEnvironmentForm.set(false);
        this.saving.set(false);
        this.environmentForm = this.emptyEnvironment(clientId);
        this.variableReferencesText = '';
      },
      error: () => { this.error.set('deployment.errors.save'); this.saving.set(false); },
    });
  }

  validate(environment: DeploymentEnvironment): void {
    if (!environment.id) return;
    this.report.set(null);
    this.service.preflight(environment.id).subscribe({
      next: report => this.report.set(report),
      error: () => this.error.set('deployment.errors.preflight'),
    });
  }

  createLicense(): void {
    const environmentId = this.selectedEnvironmentId();
    if (!environmentId) { this.error.set('deployment.errors.environmentRequired'); return; }
    this.saving.set(true);
    this.service.createLicense({ environmentId, ...this.licenseForm }).subscribe({
      next: license => { this.licenses.update(items => [license, ...items]); this.saving.set(false); },
      error: () => { this.error.set('deployment.errors.license'); this.saving.set(false); },
    });
  }

  approveLicense(license: DeploymentLicense): void {
    this.saving.set(true);
    this.service.approveLicense(license.id).subscribe({
      next: approved => { this.licenses.update(items => items.map(item => item.id === approved.id ? approved : item)); this.saving.set(false); },
      error: () => { this.error.set('deployment.errors.approval'); this.saving.set(false); },
    });
  }

  createExecution(): void {
    const environmentId = this.selectedEnvironmentId();
    if (!environmentId) { this.error.set('deployment.errors.environmentRequired'); return; }
    this.saving.set(true);
    this.service.createExecution({ environmentId, action: this.executionAction }).subscribe({
      next: execution => { this.executions.update(items => [execution, ...items]); this.saving.set(false); },
      error: () => { this.error.set('deployment.errors.execution'); this.saving.set(false); },
    });
  }

  approveExecution(execution: DeploymentExecution): void {
    this.saving.set(true);
    this.service.approveExecution(execution.id).subscribe({
      next: approved => { this.executions.update(items => items.map(item => item.id === approved.id ? approved : item)); this.saving.set(false); },
      error: () => { this.error.set('deployment.errors.approval'); this.saving.set(false); },
    });
  }

  shellsFor(os: TargetOs): ShellType[] {
    return this.catalog()?.compatibleShells[os] ?? [];
  }

  databaseTypes(): DatabaseType[] { return this.catalog()?.databaseTypes ?? []; }
  modules(side: 'MEMBER' | 'SIMULATOR'): DeploymentModule[] {
    if (this.isSwitchLab && side === 'MEMBER') return [];
    return this.catalog()?.modules.filter(module => module.side === side) ?? [];
  }

  toggleModule(side: 'MEMBER' | 'SIMULATOR', code: string, selected: boolean): void {
    const field = side === 'MEMBER' ? 'memberModules' : 'simulatorModules';
    const current = this.environmentForm[field];
    this.environmentForm[field] = selected
      ? [...new Set([...current, code])]
      : current.filter(value => value !== code);
  }

  hasModule(side: 'MEMBER' | 'SIMULATOR', code: string): boolean {
    return (side === 'MEMBER' ? this.environmentForm.memberModules : this.environmentForm.simulatorModules).includes(code);
  }

  private parseVariableReferences(text: string): Record<string, string> {
    const result: Record<string, string> = {};
    for (const raw of text.split(/\r?\n/)) {
      const line = raw.trim();
      if (!line || line.startsWith('#')) continue;
      const separator = line.indexOf('=');
      if (separator < 1) throw new Error('invalid');
      result[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
    }
    return result;
  }

  private emptyEnvironment(clientId = 0): DeploymentEnvironment {
    return {
      clientId, code: 'LOCAL', environmentType: 'LOCAL', targetOs: 'WINDOWS', shellType: 'POWERSHELL',
      deploymentRoot: 'D:\\MoneyCore\\ScenarioGenerator\\runtime\\deployment\\local', javaExecutable: 'java',
      databaseType: 'POSTGRESQL', databaseHost: 'localhost', databasePort: 5432,
      databaseName: 'scenariogenerator', databaseSchema: 'public', databaseUser: 'scenario_user',
      databasePasswordSecretRef: '', memberModules: [], simulatorModules: [], variableReferences: {},
      membersBundlePath: '', simulatorsBundlePath: '', licensePath: '', licensePublicKeyPath: '',
    };
  }

  private dateOffset(days: number): string {
    const value = new Date();
    value.setDate(value.getDate() + days);
    return value.toISOString().slice(0, 10);
  }
}
