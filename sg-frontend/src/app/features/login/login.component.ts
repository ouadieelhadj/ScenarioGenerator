import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router,RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PORTAL_PRODUCT } from '../../core/product/product.config';
import { CommercialConfirmationHandoffService } from '../../core/auth/commercial-confirmation-handoff.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, TranslatePipe,RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private confirmationHandoff=inject(CommercialConfirmationHandoffService);
  readonly product = inject(PORTAL_PRODUCT);

  login = '';
  password = '';
  readonly loading = signal(false);
  readonly errorKey = signal<string | null>(null);

  submit(): void {
    if (!this.login || !this.password) return;
    this.loading.set(true);
    this.errorKey.set(null);
    this.auth.login({ login: this.login, password: this.password }).subscribe({
      next: () => {
        this.loading.set(false);
        // Fixed route data, never an arbitrary return URL supplied by a caller.
        const onboarding=this.product.code==='MERCHANT_PORTAL' && this.route.snapshot.data['onboardingLogin']===true;
        const protectedDossier=this.product.code==='MERCHANT_PORTAL'?this.confirmationHandoff.resumePath(this.auth.user()?.role):null;
        this.password='';
        this.router.navigateByUrl(protectedDossier||(onboarding?'/onboarding/dossier':this.product.homeRoute));
      },
      error: (err) => {
        this.loading.set(false);
        this.errorKey.set(
          err.status === 401 ? 'login.errorInvalid' :
          err.status === 403 ? 'login.errorDisabled' :
          err.status === 0   ? 'login.errorNoServer' :
                               'login.errorGeneric'
        );
      },
    });
  }
}
