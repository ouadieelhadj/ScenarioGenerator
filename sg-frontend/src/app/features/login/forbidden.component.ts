import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div style="min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:var(--sg-text-secondary)">
      <i class="pi pi-lock" style="font-size:40px;color:var(--sg-color-danger)"></i>
      <h2 style="color:var(--sg-text-primary);margin:0">Accès refusé</h2>
      <p>Vous n'avez pas la permission d'accéder à cet écran.</p>
      <a routerLink="/dashboard" style="color:var(--sg-color-primary)">Retour au tableau de bord</a>
    </div>
  `,
})
export class ForbiddenComponent {}
