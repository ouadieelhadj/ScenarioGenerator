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

const BASE_URLS = {
  orchestrator: environment.apiOrchestrator.replace(/\/$/, ''),
  onboarding: environment.apiOnboarding.replace(/\/$/, ''),
  acquirer: environment.apiAcquirer.replace(/\/$/, ''),
  issuer: environment.apiIssuer.replace(/\/$/, ''),
};

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
  onboarding: defaultPort(environment.apiOnboarding),
  acquirer: defaultPort(environment.apiAcquirer),
  issuer: defaultPort(environment.apiIssuer),
};
const HOSTS = {
  orchestrator: host(environment.apiOrchestrator),
  onboarding: host(environment.apiOnboarding),
  acquirer: host(environment.apiAcquirer),
  issuer: host(environment.apiIssuer),
};

type ServiceKey = 'orchestrator' | 'onboarding' | 'acquirer' | 'issuer';

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
  if (!USE_LOCALSTORAGE_PORTS || !/^https?:\/\//i.test(BASE_URLS[service])) {
    return BASE_URLS[service];
  }
  return `${HOSTS[service]}:${getPort(service)}`;
}

/** Chemins des endpoints, groupes par domaine. */
export const ENDPOINTS = {
  switch: {
    interfaces: '/api/switch/v1/interfaces',
    interfaceCapabilities: '/api/switch/v1/interfaces/capabilities',
    acquiringOverview: '/api/switch/v1/acquiring/overview',
    domainOverview: (domain: string) => `/api/switch/v1/domains/${domain}`,
    fraudOverview: '/api/switch/v1/fraud/overview',
  },
  auth: { login: '/auth/login' },
  merchantPortal: {
    activate: '/auth/merchant-invitations/activate',
    prospects: '/api/merchant-onboarding/v1/prospects',
    dossier: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}`,
    dossierV2: (id: string) => `/api/merchant-onboarding/v2/dossiers/${id}`,
    referencesV2: (category: string) => `/api/merchant-onboarding/v2/references/${category}`,
    myDossier: '/api/merchant-onboarding/v1/dossiers/mine',
    documents: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/documents`,
    documentFiles: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/document-files`,
    documentContent: (id: string, documentId: string) => `/api/merchant-onboarding/v1/dossiers/${id}/documents/${documentId}/content`,
    submitKyc: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/kyc/submit`,
    submit: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/submit`,
    reviewQueue: '/api/merchant-onboarding/v1/review/dossiers',
    reviewDossier: (id: string) => `/api/merchant-onboarding/v1/review/dossiers/${id}`,
    reviewDocuments: (id: string) => `/api/merchant-onboarding/v1/review/dossiers/${id}/documents`,
    reviewDocument: (documentId: string) => `/api/merchant-onboarding/v1/documents/${documentId}/review`,
    reviewDocumentContent: (documentId: string) => `/api/merchant-onboarding/v1/review/documents/${documentId}/content`,
    validateKyc: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/kyc/validate`,
    kycComplements: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/kyc/complements`,
    rejectKyc: (id: string) => `/api/merchant-onboarding/v1/dossiers/${id}/kyc/reject`,
    provision: (id: string, mode: 'IMMEDIATE' | 'BATCH') => `/api/merchant-onboarding/v1/dossiers/${id}/provision?mode=${mode}`,
    pendingBatch: '/api/merchant-onboarding/v1/batches/pending',
    runBatch: '/api/merchant-onboarding/v1/batches/run',
    way4Candidates: '/api/merchant-onboarding/v2/operations/way4/candidates',
    way4Batches: '/api/merchant-onboarding/v2/operations/way4/batches',
    futurPaymentCandidates: '/api/merchant-onboarding/v2/operations/futurpayment/candidates',
    futurPaymentResend: (id: string) => `/api/merchant-onboarding/v2/operations/futurpayment/${id}/resend`,
  },
  switchLab: {
    fraudOverview: '/api/switchlab/v1/fraud/overview',
    environments: '/api/switchlab/v1/environments',
    overview: '/api/switchlab/v1/overview',
    traces: '/api/switchlab/v1/traces',
    pos: {
      catalog: '/api/switchlab/v1/pos/catalog',
      history: '/api/switchlab/v1/pos/history',
      transactions: '/api/switchlab/v1/pos/transactions',
      fieldMap: '/api/switchlab/v1/pos/field-map',
      repeat: '/api/switchlab/v1/pos/repeat',
      rki: '/api/switchlab/v1/pos/rki',
      rkiConfirm: '/api/switchlab/v1/pos/rki/confirm',
      sentinel: '/api/switchlab/v1/pos/sentinel',
    },
    testCenter: {
      catalog: '/api/switchlab/v1/test-center/catalog',
      profiles: '/api/switchlab/v1/test-center/profiles',
      campaigns: '/api/switchlab/v1/test-center/campaigns',
      runCampaign: (id: string) => `/api/switchlab/v1/test-center/campaigns/${id}/run`,
      reports: '/api/switchlab/v1/test-center/reports',
      exportReport: (id: string) => `/api/switchlab/v1/test-center/reports/${id}/export`,
      evidence: '/api/switchlab/v1/test-center/evidence',
      analyzeCertification: '/api/switchlab/v1/test-center/certification/analyze',
    },
    online: {
      networks: '/api/switchlab/v1/online/networks',
      session: (code: string) => `/api/switchlab/v1/online/networks/${code}/session`,
      keys: (code: string) => `/api/switchlab/v1/online/networks/${code}/keys`,
      scenarios: '/api/switchlab/v1/online/scenarios',
      runScenario: (code: string) => `/api/switchlab/v1/online/scenarios/${code}/run`,
    },
    clearing: {
      networks: '/api/switchlab/v1/clearing/networks',
      artifacts: '/api/switchlab/v1/clearing/artifacts',
      upload: (code: string) => `/api/switchlab/v1/clearing/networks/${code}/files`,
      eod: '/api/switchlab/v1/clearing/eod',
    },
    ecommerce: {
      components: '/api/switchlab/v1/ecommerce/components',
      scenarios: '/api/switchlab/v1/ecommerce/scenarios',
    },
    industrialization: {
      readiness: '/api/switchlab/v1/industrialization/readiness',
      backup: '/api/switchlab/v1/industrialization/backup',
    },
  },
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
  roles: {
    base: '/api/admin/roles',
    permissions: '/api/admin/roles/permissions',
  },
  workflow: {
    myOperations: '/api/workflow/requests/mine',
    myApprovals: '/api/workflow/approvals/mine',
  },
  deployments: {
    catalog: '/api/admin/deployments/catalog',
    clients: '/api/admin/deployments/clients',
    environments: '/api/admin/deployments/environments',
    preflight: (id: number) => `/api/admin/deployments/environments/${id}/preflight`,
    licenses: '/api/admin/deployments/licenses',
    approveLicense: (id: string) => `/api/admin/deployments/licenses/${id}/approve`,
    executions: '/api/admin/deployments/executions',
    approveExecution: (id: string) => `/api/admin/deployments/executions/${id}/approve`,
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
/** Helpers URL — resolvent le port dynamiquement a chaque appel. */
export const url = {
  orchestrator: (path: string) => `${baseUrl('orchestrator')}${path}`,
  onboarding: (path: string) => `${baseUrl('onboarding')}${path}`,
  acquirer: (path: string) => `${baseUrl('acquirer')}${path}`,
  issuer: (path: string) => `${baseUrl('issuer')}${path}`,
};

