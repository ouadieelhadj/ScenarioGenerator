/** Contrats normalisés attendus des BFF FuturPayment. */
export type OperationalStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN';
export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorContract {
  code: string;
  message: string;
  correlationId?: string;
  details?: Record<string, string>;
}

export interface AuthorizedAction {
  code: string;
  allowed: boolean;
  reason?: string;
}

export interface ComponentHealth {
  code: string;
  status: OperationalStatus;
  checkedAt: string;
  capabilities: string[];
  actions: AuthorizedAction[];
}

export interface SwitchLabEnvironmentReference {
  id: string;
  code: string;
  label: string;
  type: string;
  active: boolean;
}

export interface SwitchLabOverview {
  schemaVersion: string;
  environment: SwitchLabEnvironmentReference;
  overallStatus: OperationalStatus;
  availableComponents: number;
  degradedComponents: number;
  unavailableComponents: number;
  components: ComponentHealth[];
  checkedAt: string;
  correlationId: string;
}

export interface SwitchLabTraceEvent {
  id: string;
  timestamp: string;
  correlationId: string;
  category: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  component: string;
  message: string;
}

export interface SwitchLabPosScenarioDefinition {
  code: string;
  label: string;
  objective: string;
  classification: string;
  requiresCertificationCard: boolean;
  expectedResults: string[];
}

export interface SwitchLabPosExecution {
  executionId: string;
  operation: string;
  status: string;
  verdict: 'PASSED' | 'FAILED';
  correlationId: string;
  startedAt: string;
  completedAt: string;
  elapsedMillis: number;
  requestSummary: Record<string, unknown>;
  response: Record<string, unknown>;
  expectedResult: string;
}

export interface SwitchLabMtipSentinelRequest {
  pan: string;
  expiry: string;
  pin: string;
  terminalId: string;
  merchantId: string;
  amount: string;
  macEnabled: boolean;
}

export interface SwitchLabTestCatalogItem {
  code: string; label: string; moduleCode: string; network: string; type: string;
  executionMode: string; executable: boolean; requiredDataReferences: string[];
}
export interface SwitchLabProfileCapability { code: string; label: string; supported: boolean; reason?: string; }
export interface SwitchLabCampaignRequest {
  name: string; description: string; testCodes: string[]; profile: string;
  minimumAvailabilityPercent: number; maximumResponseTimeMs: number;
  durationSeconds: number; targetTps: number; concurrency: number;
  dataReferences: Record<string, string>;
}
export interface SwitchLabCampaign extends SwitchLabCampaignRequest { id: string; status: string; createdAt: string; }
export interface SwitchLabCampaignTestResult { testCode: string; moduleCode: string; expected: string; actual: string; verdict: string; elapsedMillis: number; sampleCount: number; successCount: number; errorCount: number; p95ResponseTimeMs: number; }
export interface SwitchLabCampaignReport {
  executionId: string; campaignId: string; environmentId: string; status: string; verdict: string;
  actualAvailabilityPercent: number; expectedAvailabilityPercent: number; elapsedMillis: number;
  sampleCount: number; errorRatePercent: number; p95ResponseTimeMs: number;
  correlationId: string; startedAt: string; completedAt: string; results: SwitchLabCampaignTestResult[];
}
export interface SwitchLabEvidenceRequest { sourceType: string; name: string; sourceReference: string; total: number; passed: number; failed: number; }
export interface SwitchLabEvidence extends SwitchLabEvidenceRequest { id: string; verdict: string; correlationId: string; importedAt: string; }
export interface SwitchLabCertificationManifest { name: string; network: string; schemaVersion: string; sourceReference: string; tests: Array<Record<string, unknown>>; }
export interface SwitchLabOnlineNetwork { code: string; label: string; moduleCode: string; status: string; sessionsSupported: boolean; keyStatusSupported: boolean; transactionsSupported: boolean; limitations: string[]; }
export interface SwitchLabOnlineSession { networkCode: string; status: string; role: string; mode: string; interfaceCode: string | null; bankCode: string | null; connected: boolean; observedAt: string; }
export interface SwitchLabOnlineKeyStatus { networkCode: string; keyType: string; status: string; kcv: string | null; keyReference: string | null; limitation: string | null; observedAt: string; }
export interface SwitchLabOnlineScenario { code: string; networkCode: string; label: string; outcome: string; executable: boolean; limitation: string | null; }
export interface SwitchLabOnlineScenarioResult { executionId: string; scenarioCode: string; networkCode: string; status: string; responseCode: string | null; successful: boolean; correlationId: string; completedAt: string; }
export interface SwitchLabClearingNetwork { code: string; label: string; moduleCode: string; status: string; uploadSupported: boolean; eodSupported: boolean; disputesSupported: boolean; limitations: string[]; }
export interface SwitchLabClearingArtifact { id: string; networkCode: string; fileName: string; status: string; recordCount: number; amountChecksum: string | null; evidenceReference: string; correlationId: string; receivedAt: string; }
export interface SwitchLabClearingEodRequest { networkCode: string; businessDate: string; }
export interface SwitchLabClearingEodResult { executionId: string; networkCode: string; businessDate: string; status: string; recordCount: number; evidenceReference: string; correlationId: string; completedAt: string; }
export interface SwitchLabEcommerceComponent { code: string; label: string; moduleCode: string; status: string; capabilities: string[]; limitations: string[]; }
export interface SwitchLabEcommerceScenario { code: string; label: string; program: string; flow: string; executable: boolean; limitation: string | null; }
export interface SwitchLabIndustrialReadiness { code: string; label: string; status: string; evidence: string | null; limitation: string | null; }
export interface SwitchInterfaceCapability { registryAvailable: boolean; makerCheckerAvailable: boolean; activationAvailable: boolean; reason: string | null; }
export interface SwitchInterfaceDefinition { id: string; code: string; name: string; bankCode: string; network: string; protocol: string; messageFormat: string; host: string; port: number; priority: number; failoverInterfaceCode: string | null; certificateReference: string | null; keyReference: string | null; status: string; connectionStatus: string; allowedActions: string[]; updatedAt: string; }
export interface SwitchInterfaceRequest { code: string; name: string; bankCode: string; network: string; protocol: string; messageFormat: string; host: string; port: number; priority: number; failoverInterfaceCode: string; certificateReference: string; keyReference: string; }
export interface SwitchMemberServiceStatus { code: string; label: string; configured: boolean; status: OperationalStatus; capabilities: string[]; limitation: string | null; }
export interface SwitchAcquiringFeature { code: string; label: string; status: 'AVAILABLE' | 'BLOCKED' | 'UNAVAILABLE'; backendEndpointAvailable: boolean; consultationAvailable: boolean; actionAvailable: boolean; makerCheckerRequired: boolean; limitation: string | null; }
export interface SwitchAcquiringOverview { schemaVersion: string; overallStatus: OperationalStatus; services: SwitchMemberServiceStatus[]; features: SwitchAcquiringFeature[]; checkedAt: string; correlationId: string; }
export interface SwitchDomainFeature { code: string; label: string; status: 'AVAILABLE' | 'BLOCKED' | 'UNAVAILABLE'; backendEndpointAvailable: boolean; consultationAvailable: boolean; actionAvailable: boolean; makerCheckerRequired: boolean; limitation: string | null; }
export interface SwitchDomainOverview { schemaVersion: string; domain: string; overallStatus: OperationalStatus; services: SwitchMemberServiceStatus[]; features: SwitchDomainFeature[]; checkedAt: string; correlationId: string; }

export interface ExecutionResult<T = unknown> {
  executionId: string;
  status: ExecutionStatus;
  correlationId: string;
  startedAt?: string;
  completedAt?: string;
  result?: T;
  error?: ApiErrorContract;
}
