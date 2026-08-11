import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { MERCHANT_ACTIVATION_ORIGIN, MERCHANT_ACTIVATION_PATH } from '../app/core/config/merchant-activation.config';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IonApp, IonRouterOutlet],
  template: '<ion-app><ion-router-outlet /></ion-app>',
})
export class MobileAppComponent implements OnInit {
  private readonly router = inject(Router);

  ngOnInit(): void {
    void App.addListener('appUrlOpen', event => this.openActivationLink(event.url));
    void App.getLaunchUrl().then(event => {
      if (event?.url) this.openActivationLink(event.url);
    });
  }

  private openActivationLink(rawUrl: string): void {
    try {
      const link = new URL(rawUrl);
      if (link.origin !== MERCHANT_ACTIVATION_ORIGIN
          || link.pathname !== MERCHANT_ACTIVATION_PATH) return;
      const token = link.searchParams.get('token');
      if (token) void this.router.navigate(['/activation'], { queryParams: { token } });
    } catch {
      // Ignore malformed external links and keep the current screen unchanged.
    }
  }
}
