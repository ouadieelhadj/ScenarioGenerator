import { Injectable, signal, effect } from '@angular/core';
import { Theme, ThemeTokens } from './theme.models';
import { BUILTIN_THEMES, LIGHT_THEME } from './themes';

const STORAGE_KEY = 'sg-theme';
const STORAGE_CUSTOM = 'sg-theme-custom';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  // Theme actif (signal reactif)
  readonly current = signal<Theme>(LIGHT_THEME);
  readonly available = signal<Theme[]>(BUILTIN_THEMES);

  constructor() {
    this.restore();
    // Applique automatiquement le theme des qu'il change
    effect(() => this.apply(this.current()));
  }

  // Changer de theme par id
  setTheme(id: string): void {
    const theme = this.available().find(t => t.id === id);
    if (theme) {
      this.current.set(theme);
      localStorage.setItem(STORAGE_KEY, id);
    }
  }

  // Surcharger une variable a chaud (ex. couleur primaire choisie par l'utilisateur)
  setToken(cssVar: string, value: string): void {
    const updated: Theme = {
      ...this.current(),
      tokens: { ...this.current().tokens, [cssVar]: value },
    };
    this.current.set(updated);
    this.persistCustom(updated.tokens);
  }

  // Applique les tokens au :root (surcharge des variables CSS)
  private apply(theme: Theme): void {
    const root = document.documentElement;
    Object.entries(theme.tokens).forEach(([key, value]) => {
      root.style.setProperty(`--${key}`, value);
    });
    root.setAttribute('data-theme', theme.dark ? 'dark' : 'light');
  }

  private persistCustom(tokens: ThemeTokens): void {
    localStorage.setItem(STORAGE_CUSTOM, JSON.stringify(tokens));
  }

  private restore(): void {
    const savedId = localStorage.getItem(STORAGE_KEY);
    const base = this.available().find(t => t.id === savedId) ?? LIGHT_THEME;
    const customRaw = localStorage.getItem(STORAGE_CUSTOM);
    if (customRaw) {
      try {
        const custom = JSON.parse(customRaw) as ThemeTokens;
        this.current.set({ ...base, tokens: { ...base.tokens, ...custom } });
        return;
      } catch { /* ignore */ }
    }
    this.current.set(base);
  }
}
