import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ScenarioService, Scenario } from '../../../core/services/scenario.service';

@Component({
  selector: 'app-scenario-list',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatTableModule, MatButtonModule,
    MatIconModule, MatChipsModule, MatCardModule, MatPaginatorModule,
    MatProgressSpinnerModule, MatTooltipModule
  ],
  template: `
    <div class="list-container">
      <div class="list-header">
        <h2>Scénarios de test</h2>
        <button mat-flat-button color="primary" routerLink="/scenarios/new">
          <mat-icon>add</mat-icon> Nouveau scénario
        </button>
      </div>

      <mat-card>
        <mat-progress-spinner *ngIf="loading" mode="indeterminate"
                              diameter="40" class="spinner"></mat-progress-spinner>

        <table mat-table [dataSource]="scenarios" *ngIf="!loading" class="full-width">

          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Nom</th>
            <td mat-cell *matCellDef="let s">
              <strong>{{ s.name }}</strong>
            </td>
          </ng-container>

          <ng-container matColumnDef="scheme">
            <th mat-header-cell *matHeaderCellDef>Réseau</th>
            <td mat-cell *matCellDef="let s">
              <mat-chip [class]="'chip-' + s.scheme.toLowerCase()">
                {{ s.scheme }}
              </mat-chip>
            </td>
          </ng-container>

          <ng-container matColumnDef="channel">
            <th mat-header-cell *matHeaderCellDef>Canal</th>
            <td mat-cell *matCellDef="let s">{{ s.channel }}</td>
          </ng-container>

          <ng-container matColumnDef="family">
            <th mat-header-cell *matHeaderCellDef>Famille</th>
            <td mat-cell *matCellDef="let s">
              <mat-chip [class]="'chip-family-' + s.family.toLowerCase()">
                {{ s.family }}
              </mat-chip>
            </td>
          </ng-container>

          <ng-container matColumnDef="mode">
            <th mat-header-cell *matHeaderCellDef>Mode</th>
            <td mat-cell *matCellDef="let s">{{ s.processingMode }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let s">
              <button mat-icon-button color="primary"
                      [routerLink]="['/scenarios', s.id, 'execute']"
                      matTooltip="Lancer">
                <mat-icon>play_arrow</mat-icon>
              </button>
              <button mat-icon-button
                      [routerLink]="['/scenarios', s.id, 'edit']"
                      matTooltip="Modifier">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button
                      [routerLink]="['/scenarios', s.id, 'results']"
                      matTooltip="Résultats">
                <mat-icon>assessment</mat-icon>
              </button>
              <button mat-icon-button color="warn"
                      (click)="delete(s)" matTooltip="Supprimer">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        <mat-paginator [length]="totalElements" [pageSize]="pageSize"
                       [pageSizeOptions]="[10, 20, 50]"
                       (page)="onPage($event)">
        </mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .list-container { max-width: 1100px; margin: 24px auto; padding: 0 16px; }
    .list-header { display: flex; justify-content: space-between;
                   align-items: center; margin-bottom: 16px; }
    .list-header h2 { margin: 0; }
    .full-width { width: 100%; }
    .spinner { margin: 40px auto; display: block; }
    .chip-visa { background: #1a1f71 !important; color: #fff !important; }
    .chip-mastercard { background: #eb001b !important; color: #fff !important; }
    .chip-cb { background: #0066cc !important; color: #fff !important; }
    .chip-family-nominal { background: #e8f5e9 !important; color: #2e7d32 !important; }
    .chip-family-anomaly { background: #fff3e0 !important; color: #e65100 !important; }
    .chip-family-fraud { background: #ffebee !important; color: #c62828 !important; }
    .chip-family-chargeback { background: #f3e5f5 !important; color: #6a1b9a !important; }
  `]
})
export class ScenarioListComponent implements OnInit {

  scenarios: Scenario[] = [];
  displayedColumns = ['name', 'scheme', 'channel', 'family', 'mode', 'actions'];
  loading = false;
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;

  constructor(private scenarioService: ScenarioService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.scenarioService.list(this.currentPage, this.pageSize).subscribe({
      next: (page) => {
        this.scenarios = page.content;
        this.totalElements = page.totalElements;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  onPage(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  delete(scenario: Scenario): void {
    if (!confirm(`Supprimer le scénario "${scenario.name}" ?`)) return;
    this.scenarioService.delete(scenario.id!).subscribe({
      next: () => this.load()
    });
  }
}
