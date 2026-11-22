import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { MERCHANT_ACTIVATION_ORIGIN } from '../app/core/config/merchant-activation.config';
import { ActivationHandoffService } from '../app/core/auth/activation-handoff.service';
import { CommercialConfirmationHandoffService } from '../app/core/auth/commercial-confirmation-handoff.service';
import { dispatchMerchantNativeLink } from './services/merchant-native-links';
import { PersonalAccountHandoffService } from '../app/core/auth/personal-account-handoff.service';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { MobileNavComponent } from './components/mobile-nav.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IonApp, IonRouterOutlet, MobileNavComponent],
  template: '<ion-app><app-mobile-nav /><ion-router-outlet id="merchant-main-content" /></ion-app>',
})
export class MobileAppComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly activation=inject(ActivationHandoffService);
  private readonly confirmation=inject(CommercialConfirmationHandoffService);
  private readonly account=inject(PersonalAccountHandoffService);

  ngOnInit(): void {
    void App.addListener('appUrlOpen', event => this.openTrustedLink(event.url));
    void App.getLaunchUrl().then(event => {
      if (event?.url) this.openTrustedLink(event.url);
    });
  }

  private openTrustedLink(rawUrl: string): void {
    dispatchMerchantNativeLink(rawUrl,MERCHANT_ACTIVATION_ORIGIN,this.activation,this.confirmation,
      path=>{void this.router.navigateByUrl(path);},this.account);
  }
}
