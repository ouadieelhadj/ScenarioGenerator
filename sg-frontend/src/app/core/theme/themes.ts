import { Theme } from './theme.models';

// Theme clair (valeurs par defaut de _tokens.scss, rappelees ici pour le reset)
export const LIGHT_THEME: Theme = {
  id: 'light',
  label: 'Clair',
  dark: false,
  tokens: {
    'sg-color-primary': '#2563eb',
    'sg-color-primary-hover': '#1d4ed8',
    'sg-bg-page': '#f5f6f8',
    'sg-bg-surface': '#ffffff',
    'sg-bg-elevated': '#ffffff',
    'sg-bg-sidebar': '#1e293b',
    'sg-bg-hover': '#f1f5f9',
    'sg-text-primary': '#0f172a',
    'sg-text-secondary': '#475569',
    'sg-text-muted': '#94a3b8',
    'sg-text-on-sidebar': '#e2e8f0',
    'sg-border': '#e2e8f0',
    'sg-border-strong': '#cbd5e1',
  },
};

// Theme sombre
export const DARK_THEME: Theme = {
  id: 'dark',
  label: 'Sombre',
  dark: true,
  tokens: {
    'sg-color-primary': '#3b82f6',
    'sg-color-primary-hover': '#60a5fa',
    'sg-bg-page': '#0f172a',
    'sg-bg-surface': '#1e293b',
    'sg-bg-elevated': '#243449',
    'sg-bg-sidebar': '#0b1220',
    'sg-bg-hover': '#334155',
    'sg-text-primary': '#f1f5f9',
    'sg-text-secondary': '#cbd5e1',
    'sg-text-muted': '#64748b',
    'sg-text-on-sidebar': '#cbd5e1',
    'sg-border': '#334155',
    'sg-border-strong': '#475569',
  },
};

export const BUILTIN_THEMES: Theme[] = [LIGHT_THEME, DARK_THEME];
