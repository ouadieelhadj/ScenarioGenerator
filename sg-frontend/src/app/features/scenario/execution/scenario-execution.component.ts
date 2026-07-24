import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { ScenarioService, ExecutionResult } from '../../../core/services/scenario.service';

@Component({
  selector: 'app-scenario-execution',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatProgressBarModule,
    MatIconModule, MatButtonModule, MatChipsModule
  ],
  template: `
    <div class="exec-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Exécution du scénario</mat-card-title>
          <mat-card-subtitle>Canal : {{ channel }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>

          <mat-progress-bar *ngIf="loading" mode="indeterminate"></mat-progress-bar>

          <div *ngIf="loading" class="loading-status">
            <mat-icon>send</mat-icon>
            <span>Envoi du message 0100 en cours...</span>
          </div>

          <div *ngIf="result" class="result-panel">
            <!-- Statut global -->
            <div class="status-badge" [class.approved]="result.status === 'APPROVED'"
                 [class.declined]="result.status === 'DECLINED'">
              <mat-icon>{{ result.status === 'APPROVED' ? 'check_circle' : 'cancel' }}</mat-icon>
              {{ result.status }} — DE39 : {{ result.responseCode }}
            </div>

            <!-- Détails -->
            <div class="details-grid">
              <div class="detail-item">
                <span class="label">MTI</span>
                <span class="value mono">{{ result.mti }}</span>
              </div>
              <div class="detail-item">
                <span class="label">STAN</span>
                <span class="value mono">{{ result.stan }}</span>
              </div>
              <div class="detail-item">
                <span class="label">RRN</span>
                <span class="value mono">{{ result.rrn }}</span>
              </div>
              <div class="detail-item">
                <span class="label">Code auth</span>
                <span class="value mono">{{ result.authCode || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="label">Seed</span>
                <span class="value mono">{{ result.seed }}</span>
              </div>
            </div>

            <!-- Messages hex -->
            <div class="hex-panel">
              <h4>Message 0100 (hex)</h4>
              <pre class="hex-display">{{ result.requestHex }}</pre>
            </div>
            <div class="hex-panel">
              <h4>Réponse 0110 (hex)</h4>
              <pre class="hex-display">{{ result.responseHex }}</pre>
            </div>

            <!-- Avertissements -->
            <div *ngIf="result.warnings?.length" class="warnings">
              <mat-chip-set>
                <mat-chip *ngFor="let w of result.warnings" color="warn">
                  <mat-icon matChipAvatar>warning</mat-icon>
                  {{ w }}
                </mat-chip>
              </mat-chip-set>
            </div>
          </div>

          <div *ngIf="error" class="error-panel">
            <mat-icon>error</mat-icon>
            <span>{{ error }}</span>
          </div>

        </mat-card-content>
        <mat-card-actions *ngIf="result">
          <button mat-button (click)="goBack()">
            <mat-icon>arrow_back</mat-icon> Retour
          </button>
          <button mat-flat-button color="primary" (click)="execute()">
            <mat-icon>replay</mat-icon> Rejouer
          </button>
          <button mat-flat-button color="accent"
                  [routerLink]="['/scenarios', scenarioId, 'results']">
            <mat-icon>assessment</mat-icon> Voir résultats
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .exec-container { max-width: 800px; margin: 24px auto; padding: 0 16px; }
    .loading-status { display: flex; align-items: center; gap: 8px;
                      padding: 16px 0; color: #666; }
    .status-badge { display: flex; align-items: center; gap: 8px;
                    padding: 12px 16px; border-radius: 8px; margin: 16px 0;
                    font-weight: 500; font-size: 16px; }
    .status-badge.approved { background: #e8f5e9; color: #2e7d32; }
    .status-badge.declined { background: #ffebee; color: #c62828; }
    .details-grid { display: grid; grid-template-columns: repeat(3, 1fr);
                    gap: 12px; margin: 16px 0; }
    .detail-item { background: #f5f5f5; padding: 10px 12px; border-radius: 6px; }
    .detail-item .label { display: block; font-size: 11px; color: #888; }
    .detail-item .value { font-size: 14px; font-weight: 500; }
    .mono { font-family: monospace; }
    .hex-panel { margin: 16px 0; }
    .hex-panel h4 { margin-bottom: 6px; font-size: 13px; color: #555; }
    .hex-display { background: #1e1e1e; color: #d4d4d4; padding: 12px;
                   border-radius: 6px; font-size: 11px; overflow-x: auto;
                   white-space: pre-wrap; word-break: break-all; max-height: 120px; }
    .error-panel { display: flex; align-items: center; gap: 8px;
                   padding: 16px; background: #ffebee; color: #c62828;
                   border-radius: 8px; margin: 16px 0; }
    .warnings { margin-top: 12px; }
  `]
})
export class ScenarioExecutionComponent implements OnInit {

  scenarioId!: string;
  channel = 'CIS_VISA';
  loading = false;
  result: ExecutionResult | null = null;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private scenarioService: ScenarioService
  ) {}

  ngOnInit(): void {
    this.scenarioId = this.route.snapshot.paramMap.get('id')!;
    this.channel = this.route.snapshot.queryParamMap.get('channel') || 'CIS_VISA';
    this.execute();
  }

  execute(): void {
    this.loading = true;
    this.result = null;
    this.error = null;

    this.scenarioService.execute(this.scenarioId, this.channel).subscribe({
      next: (r) => {
        this.result = r;
        this.loading = false;
      },
      error: (e) => {
        this.error = e.error?.message || 'Erreur lors de l\'exécution';
        this.loading = false;
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/scenarios']);
  }
}
