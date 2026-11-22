import { Component } from '@angular/core';
import { IonButtons, IonMenuButton } from '@ionic/angular/standalone';

@Component({ selector: 'app-mobile-menu-button', standalone: true, imports: [IonButtons, IonMenuButton],
  template: `<ion-buttons><ion-menu-button menu="merchant-main-menu" [autoHide]="true" aria-label="Ouvrir le menu">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
  </ion-menu-button></ion-buttons>` })
export class MobileMenuButtonComponent {}
