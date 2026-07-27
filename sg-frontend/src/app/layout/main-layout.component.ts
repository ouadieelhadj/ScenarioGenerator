import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { LanguageService } from '../core/i18n/language.service';
import { MENU_ITEMS } from './menu';
import { NavigationService } from '../core/services/navigation.service';
import { ModuleNavigation, NavigationItem } from '../core/models/navigation.models';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
})
export class MainLayoutComponent implements OnInit {
  private auth = inject(AuthService);
  private theme = inject(ThemeService);
  private lang = inject(LanguageService);
  private router = inject(Router);
  private navigation = inject(NavigationService);

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
  readonly modules = this.navigation.modules;
  readonly selectedModuleCode = signal<string | null>(null);
  readonly selectedModule = computed(() =>
    this.modules().find(module => module.code === this.selectedModuleCode()) ?? null
  );
  readonly dynamicScreens = computed(() => this.flattenScreens(this.selectedModule()?.children ?? []));

  ngOnInit(): void {
    this.navigation.load().subscribe(() => {
      if (!this.selectedModuleCode() && this.modules().length) {
        this.selectedModuleCode.set(this.modules()[0].code);
      }
    });
  }

  selectModule(module: ModuleNavigation): void {
    this.selectedModuleCode.set(module.code);
    const first = this.flattenScreens(module.children)[0];
    if (first?.route) this.router.navigateByUrl(first.route);
  }

  private flattenScreens(items: NavigationItem[]): NavigationItem[] {
    return items.flatMap(item =>
      item.type === 'SCREEN' ? [item] : this.flattenScreens(item.children ?? [])
    );
  }

  onThemeChange(id: string): void { this.theme.setTheme(id); }
  onPrimaryColorChange(color: string): void { this.theme.setToken('sg-color-primary', color); }
  onLangChange(code: string): void { this.lang.use(code); }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
