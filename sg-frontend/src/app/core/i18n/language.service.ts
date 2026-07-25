import { Injectable, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export interface Language { code: string; label: string; flag: string; }

const STORAGE_KEY = 'sg-lang';
const DEFAULT_LANG = 'fr';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private translate = inject(TranslateService);

  readonly available: Language[] = [
    { code: 'fr', label: 'Français', flag: '🇫🇷' },
    { code: 'en', label: 'English', flag: '🇬🇧' },
    { code: 'es', label: 'Español', flag: '🇪🇸' },
  ];

  readonly current = signal<string>(DEFAULT_LANG);

  init(): void {
    const codes = this.available.map(l => l.code);
    this.translate.addLangs(codes);
    this.translate.setFallbackLang(DEFAULT_LANG);
    const saved = localStorage.getItem(STORAGE_KEY);
    const lang = saved && codes.includes(saved) ? saved : DEFAULT_LANG;
    this.use(lang);
  }

  use(code: string): void {
    this.translate.use(code);
    this.current.set(code);
    localStorage.setItem(STORAGE_KEY, code);
    document.documentElement.setAttribute('lang', code);
  }
}

