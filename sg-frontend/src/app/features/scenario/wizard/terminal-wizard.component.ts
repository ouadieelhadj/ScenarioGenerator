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
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-terminal-wizard',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatStepperModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatChipsModule, MatTabsModule,
    MatIconModule, MatCardModule, MatSlideToggleModule,
    MatSnackBarModule
  ],
  template: `
    <div class="wizard-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Simulation de terminal</mat-card-title>
          <mat-card-subtitle>TPE / GAB / E-commerce — même flux vers l'émetteur simulé</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <mat-stepper [linear]="true" #stepper>

            <!-- ══ ÉTAPE 1 : Type de terminal ══ -->
            <mat-step [stepControl]="step1" label="Terminal">
              <form [formGroup]="step1">
                <div class="field">
                  <label>Type de terminal</label>
                  <div class="chips">
                    <span class="chip" [class.sel]="selectedTerminal === 'POS'"
                          (click)="selectTerminal('POS')">
                      🏪 TPE / POS
                    </span>
                    <span class="chip" [class.sel]="selectedTerminal === 'ATM'"
                          (click)="selectTerminal('ATM')">
                      🏧 GAB / ATM
                    </span>
                    <span class="chip" [class.sel]="selectedTerminal === 'ECOMMERCE'"
                          (click)="selectTerminal('ECOMMERCE')">
                      🛒 E-commerce
                    </span>
                  </div>
                </div>

                <!-- TPE options -->
                <div *ngIf="selectedTerminal === 'POS'">
                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Mode de saisie (DE22)</mat-label>
                    <mat-select formControlName="posEntryMode">
                      <mat-option value="05">Puce contact (05)</mat-option>
                      <mat-option value="07">Sans contact NFC (07)</mat-option>
                      <mat-option value="02">Piste magnétique (02)</mat-option>
                      <mat-option value="01">Saisie manuelle (01)</mat-option>
                    </mat-select>
                  </mat-form-field>
                  <mat-slide-toggle formControlName="withPin">
                    Avec PIN (DE52)
                  </mat-slide-toggle>
                </div>

                <!-- GAB options -->
                <div *ngIf="selectedTerminal === 'ATM'">
                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Opération GAB</mat-label>
                    <mat-select formControlName="atmOperation">
                      <mat-option value="WITHDRAWAL">Retrait espèces (DE3=010000)</mat-option>
                      <mat-option value="BALANCE_INQUIRY">Consultation solde (DE3=300000)</mat-option>
                      <mat-option value="PIN_CHANGE">Changement PIN (DE3=900000)</mat-option>
                      <mat-option value="MINI_STATEMENT">Mini-relevé</mat-option>
                    </mat-select>
                  </mat-form-field>
                  <div class="info-box">
                    🔐 PIN obligatoire sur GAB — calculé via HSM Thales payShield
                  </div>
                  <div class="info-box ndc">
                    📡 Protocole NDC (jndc) : Sign-on → Transaction → Sign-off → Conversion ISO 8583
                  </div>
                </div>

                <!-- E-commerce options -->
                <div *ngIf="selectedTerminal === 'ECOMMERCE'">
                  <mat-slide-toggle formControlName="with3ds" class="mb-16">
                    Activer 3-D Secure 2.x
                  </mat-slide-toggle>
                  <mat-form-field *ngIf="step1.get('with3ds')?.value"
                                  appearance="outline" class="full-width">
                    <mat-label>Mode 3DS</mat-label>
                    <mat-select formControlName="threeDsMode">
                      <mat-option value="FRICTIONLESS">
                        Frictionless (transStatus=Y, ECI=05)
                      </mat-option>
                      <mat-option value="CHALLENGE">
                        Challenge (OTP simulé, ECI=05)
                      </mat-option>
                      <mat-option value="ATTEMPT">
                        Tentative (transStatus=A, ECI=06)
                      </mat-option>
                      <mat-option value="NO_3DS">
                        Sans 3DS (ECI=07)
                      </mat-option>
                    </mat-select>
                  </mat-form-field>
                  <div class="info-box ecom">
                    🌐 DE22=81 · Pas de DE55 · ECI/CAVV injectés dans DE48
                  </div>
                </div>

                <div class="step-actions">
                  <button mat-flat-button color="primary" matStepperNext
                          [disabled]="!selectedTerminal">
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- ══ ÉTAPE 2 : Réseau & Canal ══ -->
            <mat-step [stepControl]="step2" label="Réseau">
              <form [formGroup]="step2">
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
                  <mat-label>Interface cible (émetteur simulé)</mat-label>
                  <mat-select formControlName="targetChannel" required>
                    <mat-option value="CIS_VISA">
                      CIS Visa → ISOServer port 8200 (Visa ém.)
                    </mat-option>
                    <mat-option value="BASE1_VISA">
                      BASE I Visa → ISOServer port 8200 (Visa ém.)
                    </mat-option>
                    <mat-option value="MAS_MC">
                      MAS Mastercard → ISOServer port 8200 (MC ém.)
                    </mat-option>
                    <mat-option value="CB_DOMESTIC">
                      CB Domestique → ISOServer port 8200 (CB ém.)
                    </mat-option>
                  </mat-select>
                </mat-form-field>

                <div class="info-box green">
                  ✦ Notre ISOServer port 8200 simule la banque émettrice —
                  BIN (DE2) → {{ step2.get('scheme')?.value || 'réseau' }}
                </div>

                <div class="step-actions">
                  <button mat-button matStepperPrevious>Précédent</button>
                  <button mat-flat-button color="primary" matStepperNext>
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- ══ ÉTAPE 3 : Paramètres ISO ══ -->
            <mat-step [stepControl]="step3" label="Paramètres">
              <form [formGroup]="step3">
                <div class="row2">
                  <mat-form-field appearance="outline">
                    <mat-label>Montant (centimes)</mat-label>
                    <input matInput type="number" formControlName="amount">
                    <mat-hint>5000 = 50,00 EUR</mat-hint>
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Devise (ISO 4217)</mat-label>
                    <mat-select formControlName="currencyCode">
                      <mat-option value="978">978 — EUR</mat-option>
                      <mat-option value="840">840 — USD</mat-option>
                      <mat-option value="826">826 — GBP</mat-option>
                    </mat-select>
                  </mat-form-field>
                </div>
                <div class="row2">
                  <mat-form-field appearance="outline">
                    <mat-label>BIN (préfixe carte)</mat-label>
                    <mat-select formControlName="binPrefix">
                      <mat-option value="411111">411111 — Visa test</mat-option>
                      <mat-option value="555555">555555 — MC test</mat-option>
                      <mat-option value="497010">497010 — CB test</mat-option>
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>MCC (ISO 18245)</mat-label>
                    <input matInput formControlName="mcc">
                  </mat-form-field>
                </div>
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Seed (reproductibilité)</mat-label>
                  <input matInput type="number" formControlName="seed"
                         placeholder="Auto-généré si vide">
                </mat-form-field>

                <div class="step-actions">
                  <button mat-button matStepperPrevious>Précédent</button>
                  <button mat-flat-button color="primary" matStepperNext>
                    Suivant <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- ══ ÉTAPE 4 : Lancement ══ -->
            <mat-step label="Lancement">
              <div class="summary">
                <h3>Résumé de la simulation</h3>
                <p><strong>Terminal :</strong> {{ selectedTerminal }}</p>
                <p><strong>Réseau :</strong> {{ step2.get('scheme')?.value }}</p>
                <p><strong>Canal :</strong> {{ step2.get('targetChannel')?.value }}</p>
                <p><strong>Montant :</strong>
                  {{ step3.get('amount')?.value / 100 | number:'1.2-2' }}
                  {{ step3.get('currencyCode')?.value }}
                </p>
                <p *ngIf="selectedTerminal === 'ATM'">
                  <strong>Opération GAB :</strong>
                  {{ step1.get('atmOperation')?.value }}
                </p>
                <p *ngIf="selectedTerminal === 'ECOMMERCE'">
                  <strong>Mode 3DS :</strong>
                  {{ step1.get('threeDsMode')?.value || 'Sans 3DS' }}
                </p>
                <div class="info-box green">
                  Le 0100 sera envoyé → {{ step2.get('targetChannel')?.value }}
                  → ISOServer port 8200 → réponse 0110
                </div>
              </div>
              <div class="step-actions">
                <button mat-button matStepperPrevious>Précédent</button>
                <button mat-flat-button color="primary"
                        (click)="launch()" [disabled]="loading">
                  <mat-icon>play_arrow</mat-icon>
                  {{ loading ? 'Simulation en cours...' : 'Lancer la simulation' }}
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
    .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
    .step-actions { display: flex; gap: 12px; margin-top: 20px; justify-content: flex-end; }
    .field { margin-bottom: 16px; }
    .field label { display: block; font-size: 13px; color: var(--color-text-secondary); margin-bottom: 6px; }
    .chips { display: flex; gap: 10px; flex-wrap: wrap; }
    .chip { padding: 10px 18px; border-radius: 20px; border: 1px solid var(--color-border-secondary); font-size: 13px; cursor: pointer; transition: all .15s; }
    .chip:hover { border-color: #185FA5; color: #185FA5; }
    .chip.sel { background: #185FA5; color: #fff; border-color: #185FA5; font-weight: 500; }
    .info-box { padding: 10px 14px; border-radius: 8px; font-size: 12px; margin: 10px 0; background: var(--color-background-secondary); border-left: 3px solid var(--color-border-secondary); }
    .info-box.ndc { border-left-color: #0F6E56; background: #E8F7F1; color: #085041; }
    .info-box.ecom { border-left-color: #534193; background: #EEEDFE; color: #3C3489; }
    .info-box.green { border-left-color: #1D9E75; background: #E8F7F1; color: #0F6E56; }
    .summary p { margin: 8px 0; font-size: 14px; }
    .summary h3 { margin-bottom: 12px; }
    .mb-16 { margin-bottom: 16px; display: block; }
  `]
})
export class TerminalWizardComponent implements OnInit {

  step1!: FormGroup;
  step2!: FormGroup;
  step3!: FormGroup;
  selectedTerminal: 'POS' | 'ATM' | 'ECOMMERCE' | null = null;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.step1 = this.fb.group({
      posEntryMode: ['05'],
      withPin: [false],
      atmOperation: ['WITHDRAWAL'],
      with3ds: [true],
      threeDsMode: ['FRICTIONLESS'],
    });
    this.step2 = this.fb.group({
      scheme: ['VISA', Validators.required],
      targetChannel: ['CIS_VISA', Validators.required],
    });
    this.step3 = this.fb.group({
      amount: [5000],
      currencyCode: ['978'],
      binPrefix: ['411111'],
      mcc: ['5411'],
      seed: [null],
    });
  }

  selectTerminal(type: 'POS' | 'ATM' | 'ECOMMERCE'): void {
    this.selectedTerminal = type;
    // Auto-sélection BIN selon terminal
    if (type === 'ATM') {
      this.step3.patchValue({ mcc: '6011' });
    } else if (type === 'ECOMMERCE') {
      this.step3.patchValue({ mcc: '5999' });
    }
  }

  launch(): void {
    if (!this.selectedTerminal) return;
    this.loading = true;

    const endpoint = `/api/terminals/${this.selectedTerminal.toLowerCase()}/simulate`;
    const body = this.buildBody();

    this.http.post(endpoint, body).subscribe({
      next: (result: any) => {
        this.loading = false;
        this.snackBar.open(
          `DE39=${result.responseCode} — ${result.status}`,
          'OK', { duration: 5000 }
        );
        this.router.navigate(['/terminals/results'],
          { state: { result } });
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(
          `Erreur : ${err.error?.message || 'Simulation échouée'}`,
          'Fermer', { duration: 5000 }
        );
      }
    });
  }

  private buildBody(): object {
    const common = {
      ...this.step2.value,
      ...this.step3.value,
      seed: this.step3.get('seed')?.value || Date.now(),
    };

    switch (this.selectedTerminal) {
      case 'POS':
        return {
          ...common,
          posEntryMode: this.step1.get('posEntryMode')?.value,
          withPin: this.step1.get('withPin')?.value,
        };
      case 'ATM':
        return {
          ...common,
          atmOperation: this.step1.get('atmOperation')?.value,
        };
      case 'ECOMMERCE':
        return {
          ...common,
          with3ds: this.step1.get('with3ds')?.value,
          threeDsMode: this.step1.get('threeDsMode')?.value,
        };
      default:
        return common;
    }
  }
}
