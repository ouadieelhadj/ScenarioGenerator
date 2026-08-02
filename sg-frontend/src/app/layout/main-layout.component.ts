import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { LanguageService } from '../core/i18n/language.service';
import { COMMON_MENU_ITEMS, MenuItem } from './menu';
import { NavigationService } from '../core/services/navigation.service';
import { ModuleNavigation, NavigationItem } from '../core/models/navigation.models';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, NgTemplateOutlet],
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

  readonly menu = computed(() => this.filterCommonMenu(COMMON_MENU_ITEMS));
  readonly modules = computed(() => this.navigation.modules().filter(module =>
    !['CORE', 'CORE_PORTAL'].includes(module.code.toUpperCase())
  ));
  readonly selectedModuleCode = signal<string | null>(null);
  readonly selectedModule = computed(() =>
    this.modules().find(module => module.code === this.selectedModuleCode()) ?? null
  );
  readonly dynamicMenu = computed(() => this.selectedModule()?.children ?? []);

  ngOnInit(): void {
    this.navigation.load().subscribe(() => {
      if (!this.selectedModuleCode() && this.modules().length) {
        this.selectedModuleCode.set(this.modules()[0].code);
      }
    });
  }

  selectModule(module: ModuleNavigation): void {
    this.selectedModuleCode.set(module.code);
    const first = this.firstScreen(module.children);
    if (first?.route) this.router.navigateByUrl(first.route);
  }

  private firstScreen(items: NavigationItem[]): NavigationItem | null {
    for (const item of items) {
      if (item.type === 'SCREEN' && item.route) return item;
      const child = this.firstScreen(item.children ?? []);
      if (child) return child;
    }
    return null;
  }

  private filterCommonMenu(items: MenuItem[]): MenuItem[] {
    return items.flatMap(item => {
      if (item.permissions && !this.auth.hasAnyPermission(item.permissions)) return [];
      const children = this.filterCommonMenu(item.children ?? []);
      if (!item.route && item.children?.length && !children.length) return [];
      return [{ ...item, children }];
    });
  }

  onThemeChange(id: string): void { this.theme.setTheme(id); }
  onPrimaryColorChange(color: string): void { this.theme.setToken('sg-color-primary', color); }
  onLangChange(code: string): void { this.lang.use(code); }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
