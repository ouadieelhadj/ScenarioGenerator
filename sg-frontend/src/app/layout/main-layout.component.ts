import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { LanguageService } from '../core/i18n/language.service';
import { menuItemsFor, MenuItem } from './menu';
import { NavigationService } from '../core/services/navigation.service';
import { ModuleNavigation, NavigationItem } from '../core/models/navigation.models';
import { PORTAL_PRODUCT } from '../core/product/product.config';

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
  readonly product = inject(PORTAL_PRODUCT);

  readonly user = this.auth.user;
  readonly themes = this.theme.available;
  readonly currentTheme = this.theme.current;
  readonly languages = this.lang.available;
  readonly currentLang = this.lang.current;

  private readonly configuredMenu = menuItemsFor(this.product.code);
  readonly menu = computed(() => this.filterCommonMenu(this.configuredMenu));
  readonly leadingMenu = computed(() => this.menu().filter(item => this.product.leadingMenuCodes.includes(item.code)));
  readonly trailingMenu = computed(() => this.menu().filter(item => !this.product.leadingMenuCodes.includes(item.code)));
  readonly modules = computed(() => {
    const modules = this.navigation.modules().filter(module =>
      !['CORE', 'CORE_PORTAL'].includes(module.code.toUpperCase())
    );
    if (this.product.code === 'LEGACY') return modules;
    const allowed = new Set(this.product.allowedModuleCodes.map(code => code.toUpperCase()));
    return modules.filter(module => allowed.has(module.code.toUpperCase()));
  });
  readonly selectedModuleCode = signal<string | null>(null);
  readonly selectedModule = computed(() =>
    this.modules().find(module => module.code === this.selectedModuleCode()) ?? null
  );
  readonly dynamicMenu = computed(() => this.selectedModule()?.children ?? []);
  readonly moduleGroups = computed(() => this.product.moduleGroups.map(group => {
    const accepted = new Set(group.moduleCodes.map(code => code.toUpperCase()));
    const modules = accepted.size
      ? this.modules().filter(module => accepted.has(module.code.toUpperCase()))
      : this.modules();
    return { ...group, modules };
  }).filter(group => group.modules.length));

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
      const isRestricted = Boolean(item.permissions?.length || item.roles?.length);
      const isAllowed = (item.permissions?.length && this.auth.hasAnyPermission(item.permissions))
        || (item.roles?.length && item.roles.some(role => this.auth.hasRole(role)));
      if (isRestricted && !isAllowed) return [];
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
