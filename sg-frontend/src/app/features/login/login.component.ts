import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PORTAL_PRODUCT } from '../../core/product/product.config';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
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
        this.router.navigate(['/dashboard']);
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
