import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatStepperModule } from '@angular/material/stepper';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ScenarioService } from '../../../core/services/scenario.service';

@Component({
  selector: 'app-scenario-wizard',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatStepperModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatChipsModule,
    MatTabsModule, MatIconModule, MatCardModule, MatSnackBarModule
  ],
  template: `
    <div class="wizard-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Nouveau scénario de test</mat-card-title>
          <mat-card-subtitle>Configurez votre scénario ISO 8583 en 5 étapes</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <mat-stepper [linear]="true" #stepper>

            <!-- Étape 1 : Réseau -->
            <mat-step [stepControl]="step1" label="Réseau">
              <form [formGroup]="step1">
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Scheme / Réseau</mat-label>
                  <mat-select formControlName="scheme" required>
                    <mat-option value="VISA">Visa</mat-option>
                    <mat-option value="MASTERCARD">Mastercard</mat-option>
                    <mat-option value="CB">CB (domestique FR)</mat-option>
                    <mat-option value="CB_VISA">CB / Visa (cobadge)</mat-option>
                    <mat-option value="CB_MC">CB / Mastercard (cobadge)</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Mode de traitement</mat-label>
                  <mat-select formControlName="processingMode" required>
                    <mat-option value="ONLINE">Online (temps réel)</mat-option>
                    <mat-option value="CLEARING">Clearing (fichiers)</mat-option>
                    <mat-option value="BOTH">Les deux</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Interface cible</mat-label>
                  <mat-select formControlName="targetChannel" required>
                    <mat-option value="CIS_VISA">CIS — Visa authorization</mat-option>
                    <mat-option value="BASE1_VISA">BASE I — Visa real-time auth</mat-option>
                    <mat-option value="MAS_MC">MAS — Mastercard auth</mat-option>
                    <mat-option value="CB_DOMESTIC">CB — Réseau domestique</mat-option>
                    <mat-option value="SIMULATED">Simulé (loopback interne)</mat-option>
                  </mat-select>
                </mat-form-field>
                <div class="step-actions">
                  <button mat-flat-button color="primary" matStepperNext>
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- Étape 2 : Canal -->
            <mat-step [stepControl]="step2" label="Canal">
              <form [formGroup]="step2">
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Canal d'acceptation</mat-label>
                  <mat-select formControlName="channel" required>
                    <mat-option value="POS">TPE / POS</mat-option>
                    <mat-option value="ECOMMERCE">E-commerce</mat-option>
                    <mat-option value="ATM">ATM / GAB</mat-option>
                    <mat-option value="MOTO">MOTO</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Mode de saisie (DE22)</mat-label>
                  <mat-select formControlName="posEntryMode" required>
                    <mat-option value="05">Puce contact (05)</mat-option>
                    <mat-option value="07">Sans contact (07)</mat-option>
                    <mat-option value="02">Piste magnétique (02)</mat-option>
                    <mat-option value="01">Saisie manuelle (01)</mat-option>
                    <mat-option value="81">E-commerce (81)</mat-option>
                  </mat-select>
                </mat-form-field>
                <div class="step-actions">
                  <button mat-button matStepperPrevious>Précédent</button>
                  <button mat-flat-button color="primary" matStepperNext>
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- Étape 3 : Scénario -->
            <mat-step [stepControl]="step3" label="Scénario">
              <form [formGroup]="step3">
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Nom du scénario</mat-label>
                  <input matInput formControlName="name" required
                         placeholder="Ex: Achat nominal Visa TPE puce">
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Famille</mat-label>
                  <mat-select formControlName="family" required>
                    <mat-option value="NOMINAL">Nominal</mat-option>
                    <mat-option value="ANOMALY">Anomalie de traitement</mat-option>
                    <mat-option value="FRAUD">Fraude</mat-option>
                    <mat-option value="CHARGEBACK">Chargeback</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Type d'opération</mat-label>
                  <mat-select formControlName="operationType" required>
                    <mat-option value="AUTHORIZATION">Autorisation (0100)</mat-option>
                    <mat-option value="REVERSAL">Reversal (0400)</mat-option>
                    <mat-option value="REFUND">Remboursement</mat-option>
                    <mat-option value="PRE_AUTH">Pré-autorisation</mat-option>
                    <mat-option value="CASH_WITHDRAWAL">Retrait espèces</mat-option>
                    <mat-option value="FULL_LIFECYCLE">Cycle de vie complet</mat-option>
                  </mat-select>
                </mat-form-field>
                <div class="step-actions">
                  <button mat-button matStepperPrevious>Précédent</button>
                  <button mat-flat-button color="primary" matStepperNext>
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- Étape 4 : Paramètres -->
            <mat-step [stepControl]="step4" label="Paramètres">
              <form [formGroup]="step4">
                <mat-tab-group>
                  <mat-tab label="Online — 0100/0110">
                    <div class="tab-content">
                      <mat-form-field appearance="outline">
                        <mat-label>Montant (centimes)</mat-label>
                        <input matInput type="number" formControlName="amount" value="5000">
                        <mat-hint>5000 = 50,00 EUR</mat-hint>
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Devise (ISO 4217)</mat-label>
                        <mat-select formControlName="currency">
                          <mat-option value="978">978 — EUR</mat-option>
                          <mat-option value="840">840 — USD</mat-option>
                          <mat-option value="826">826 — GBP</mat-option>
                        </mat-select>
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>MCC (ISO 18245)</mat-label>
                        <input matInput formControlName="mcc" placeholder="5411">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Seed (reproductibilité)</mat-label>
                        <input matInput type="number" formControlName="seed"
                               placeholder="Auto-généré si vide">
                      </mat-form-field>
                    </div>
                  </mat-tab>
                  <mat-tab label="Clearing — IPM/BASE II">
                    <div class="tab-content">
                      <mat-form-field appearance="outline" class="full-width">
                        <mat-label>Format fichier</mat-label>
                        <mat-select formControlName="clearingFormat">
                          <mat-option value="IPM">IPM (Mastercard)</mat-option>
                          <mat-option value="BASE_II">BASE II (Visa)</mat-option>
                          <mat-option value="CSV">CSV générique</mat-option>
                        </mat-select>
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Nombre de records</mat-label>
                        <input matInput type="number" formControlName="recordCount" value="100">
                      </mat-form-field>
                    </div>
                  </mat-tab>
                </mat-tab-group>
                <div class="step-actions">
                  <button mat-button matStepperPrevious>Précédent</button>
                  <button mat-flat-button color="primary" matStepperNext>
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- Étape 5 : Validation -->
            <mat-step label="Validation">
              <div class="summary">
                <h3>Résumé du scénario</h3>
                <p><strong>Réseau :</strong> {{ step1.get('scheme')?.value }}</p>
                <p><strong>Canal :</strong> {{ step2.get('channel')?.value }}</p>
                <p><strong>Nom :</strong> {{ step3.get('name')?.value }}</p>
                <p><strong>Type :</strong> {{ step3.get('operationType')?.value }}</p>
                <p><strong>Famille :</strong> {{ step3.get('family')?.value }}</p>
              </div>
              <div class="step-actions">
                <button mat-button matStepperPrevious>Précédent</button>
                <button mat-flat-button color="accent"
                        (click)="saveAndExecute()" [disabled]="saving">
                  <mat-icon>play_arrow</mat-icon>
                  Sauvegarder et lancer
                </button>
                <button mat-flat-button color="primary"
                        (click)="save()" [disabled]="saving">
                  <mat-icon>save</mat-icon>
                  Sauvegarder seulement
                </button>
              </div>
            </mat-step>

          </mat-stepper>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .wizard-container { max-width: 720px; margin: 24px auto; padding: 0 16px; }
    .full-width { width: 100%; margin-bottom: 12px; }
    .step-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
    .tab-content { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 16px 0; }
    .summary p { margin: 8px 0; font-size: 14px; }
    .summary h3 { margin-bottom: 12px; }
  `]
})
export class ScenarioWizardComponent implements OnInit {

  step1!: FormGroup;
  step2!: FormGroup;
  step3!: FormGroup;
  step4!: FormGroup;
  saving = false;

  constructor(
    private fb: FormBuilder,
    private scenarioService: ScenarioService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.step1 = this.fb.group({
      scheme: ['VISA', Validators.required],
      processingMode: ['ONLINE', Validators.required],
      targetChannel: ['CIS_VISA', Validators.required]
    });
    this.step2 = this.fb.group({
      channel: ['POS', Validators.required],
      posEntryMode: ['05', Validators.required]
    });
    this.step3 = this.fb.group({
      name: ['', Validators.required],
      family: ['NOMINAL', Validators.required],
      operationType: ['AUTHORIZATION', Validators.required]
    });
    this.step4 = this.fb.group({
      amount: [5000],
      currency: ['978'],
      mcc: ['5411'],
      seed: [null],
      clearingFormat: ['IPM'],
      recordCount: [100]
    });
  }

  buildScenario() {
    return {
      ...this.step1.value,
      ...this.step2.value,
      ...this.step3.value,
      parameters: {
        amount: this.step4.get('amount')?.value,
        currency: this.step4.get('currency')?.value,
        mcc: this.step4.get('mcc')?.value,
        posEntryMode: this.step2.get('posEntryMode')?.value,
        clearingFormat: this.step4.get('clearingFormat')?.value,
        recordCount: this.step4.get('recordCount')?.value
      },
      seed: this.step4.get('seed')?.value || null
    };
  }

  save(): void {
    this.saving = true;
    this.scenarioService.create(this.buildScenario()).subscribe({
      next: (s) => {
        this.snackBar.open('Scénario sauvegardé', 'OK', { duration: 3000 });
        this.router.navigate(['/scenarios']);
      },
      error: () => {
        this.snackBar.open('Erreur lors de la sauvegarde', 'Fermer', { duration: 3000 });
        this.saving = false;
      }
    });
  }

  saveAndExecute(): void {
    this.saving = true;
    this.scenarioService.create(this.buildScenario()).subscribe({
      next: (s) => {
        this.router.navigate(['/scenarios', s.id, 'execute'],
          { queryParams: { channel: this.step1.get('targetChannel')?.value } });
      },
      error: () => {
        this.snackBar.open('Erreur', 'Fermer', { duration: 3000 });
        this.saving = false;
      }
    });
  }
}
