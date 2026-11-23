import { Component, ViewChild, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { PersonalMerchantAssociationsView } from './personal-merchant-associations.view';
import type { PersonalContextSession } from '../../core/auth/personal-context-session';

@Component({ standalone: true, imports: [PersonalMerchantAssociationsView], template: `
  <app-personal-merchant-associations [currentLogin]="auth.user()?.login || ''"
    [currentAcquirer]="auth.user()?.acquirerId || ''" (contextSelected)="select($event)" />` })
export class PersonalMerchantAssociationsComponent {
  readonly auth=inject(AuthService); @ViewChild(PersonalMerchantAssociationsView) view?: PersonalMerchantAssociationsView;
  select(event: { session: PersonalContextSession; requested: string }): void {
    if (!this.auth.acceptPersonalContext(event.session,event.requested)) this.view?.rejectContext();
  }
}
