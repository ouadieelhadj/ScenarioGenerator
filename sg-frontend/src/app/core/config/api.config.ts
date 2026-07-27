/**
 * Configuration centralisee de l'API et des valeurs par defaut.
 * Les URL de base peuvent etre surchargees a chaud via localStorage
 * (cle 'sg-ports') — utilise par l'ecran Configuration.
 */
import { environment } from '../../../environments/environment';

const PORTS_KEY = 'sg-ports';

// Mettre a false pour IGNORER le localStorage et utiliser uniquement les ports
// des fichiers (environment.ts). Utile pour repartir sur une base propre.
const USE_LOCALSTORAGE_PORTS = false;

// Ports par defaut (extraits des URL de l'environnement)
function defaultPort(u: string): number {
  const m = u.match(/:(\d+)/);
  return m ? Number(m[1]) : 80;
}
function host(u: string): string {
  return u.replace(/:\d+$/, '');
}

const DEFAULTS = {
  orchestrator: defaultPort(environment.apiOrchestrator),
  acquirer: defaultPort(environment.apiAcquirer),
  issuer: defaultPort(environment.apiIssuer),
};
const HOSTS = {
  orchestrator: host(environment.apiOrchestrator),
  acquirer: host(environment.apiAcquirer),
  issuer: host(environment.apiIssuer),
};

type ServiceKey = 'orchestrator' | 'acquirer' | 'issuer';

// Lit les ports surchargeables depuis localStorage (si active)
function readPorts(): Record<ServiceKey, number> {
  if (!USE_LOCALSTORAGE_PORTS) return { ...DEFAULTS };
  try {
    const raw = localStorage.getItem(PORTS_KEY);
    if (raw) return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch { /* ignore */ }
  return { ...DEFAULTS };
}

export function getPort(service: ServiceKey): number {
  return readPorts()[service];
}

export function setPort(service: ServiceKey, port: number): void {
  if (!USE_LOCALSTORAGE_PORTS) return; // localStorage desactive
  const ports = readPorts();
  ports[service] = port;
  localStorage.setItem(PORTS_KEY, JSON.stringify(ports));
}

export function baseUrl(service: ServiceKey): string {
  return `${HOSTS[service]}:${getPort(service)}`;
}

/** Chemins des endpoints, groupes par domaine. */
export const ENDPOINTS = {
  auth: { login: '/auth/login' },
  me: { navigation: '/api/me/navigation' },
  campaigns: {
    base: '/api/campaigns',
    byId: (id: number) => `/api/campaigns/${id}`,
    run: (id: number) => `/api/campaigns/${id}/run`,
    execution: (execId: number) => `/api/campaigns/executions/${execId}`,
    executionsByCampaign: (id: number) => `/api/campaigns/${id}/executions`,
  },
  config: {
    port: '/api/admin/config/port',
  },
  networks: {
    base: '/api/networks',
  },
  messageTypes: {
    base: '/api/admin/message-types',
  },
  users: {
    base: '/api/admin/users',
    byId: (id: number) => `/api/admin/users/${id}`,
    toggle: (id: number) => `/api/admin/users/${id}/toggle`,
  },
  dmas: {
    network: {
      signon: '/api/admin/dmas/network/signon',
      signoff: '/api/admin/dmas/network/signoff',
      echo: '/api/admin/dmas/network/echo',
      status: '/api/admin/dmas/network/status',
    },
    kek: { bootstrap: '/api/admin/dmas/kek/bootstrap' },
    keyexchange: { pek: '/api/admin/dmas/keyexchange/pek' },
    cards: {
      base: '/api/admin/dmas/cards',
      byPan: (pan: string) => `/api/admin/dmas/cards/${pan}`,
      balance: (pan: string) => `/api/admin/dmas/cards/${pan}/balance`,
    },
    authorize: '/api/mc/authorize',
    reversal: '/api/mc/reversal',
    advice: '/api/mc/advice',
  },
} as const;

/** Valeurs de test par defaut (editables). */
export const TEST_DEFAULTS = {
  memberGroupId: 'TESTGRP01',
  kekClear: '0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF',
  card: { pan: '5321962145453348', pin: '1234', balance: 1000000, currency: '840', expiry: '2812' },
  auth: {
    DE002_PAN: '5321962145453348', DE004_AMOUNT: 5000, DE003_PROCESSING_CODE: '000000',
    DE018_MCC: '5999', DE022_POS_ENTRY_MODE: '051', DE025_POS_CONDITION_CODE: '00',
    DE032_ACQUIRING_BIN: '400000', DE041_TERMINAL_ID: 'TERM0001', DE042_MERCHANT_ID: 'MERCHANT000001',
    DE043_MERCHANT_NAME: 'SG TEST SHOP', DE049_CURRENCY_CODE: '840', DE052_PIN: '',
  },
} as const;

/** Helpers URL — resolvent le port dynamiquement a chaque appel. */
export const url = {
  orchestrator: (path: string) => `${baseUrl('orchestrator')}${path}`,
  acquirer: (path: string) => `${baseUrl('acquirer')}${path}`,
  issuer: (path: string) => `${baseUrl('issuer')}${path}`,
};

