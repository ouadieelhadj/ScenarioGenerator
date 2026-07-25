import { Component } from '@angular/core';

@Component({
  selector: 'app-profile',
  standalone: true,
  template: `
    <div class="page-header">
      <h1><i class="pi pi-user"></i> Profil</h1>
    </div>
    <div class="placeholder">
      <p>Écran « Profil » — à implémenter.</p>
    </div>
  `,
  styles: [`
    .page-header h1 { font-size: 20px; color: var(--sg-text-primary); display:flex; align-items:center; gap:10px; }
    .page-header i { color: var(--sg-color-primary); }
    .placeholder {
      margin-top: var(--sg-gap-lg); padding: var(--sg-gap-xl);
      background: var(--sg-bg-surface); border: 1px dashed var(--sg-border-strong);
      border-radius: var(--sg-radius); color: var(--sg-text-muted); text-align: center;
    }
  `],
})
export class ProfileComponent {}
