import { Component, computed, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { LanguageService } from '../core/i18n/language.service';
import { MENU_ITEMS } from './menu';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
})
export class MainLayoutComponent {
  private auth = inject(AuthService);
  private theme = inject(ThemeService);
  private lang = inject(LanguageService);
  private router = inject(Router);

  readonly user = this.auth.user;
  readonly themes = this.theme.available;
  readonly currentTheme = this.theme.current;
  readonly languages = this.lang.available;
  readonly currentLang = this.lang.current;

  readonly menu = computed(() =>
    MENU_ITEMS.filter(item =>
      !item.permissions || this.auth.hasAnyPermission(item.permissions)
    )
  );

  onThemeChange(id: string): void { this.theme.setTheme(id); }
  onPrimaryColorChange(color: string): void { this.theme.setToken('sg-color-primary', color); }
  onLangChange(code: string): void { this.lang.use(code); }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}

