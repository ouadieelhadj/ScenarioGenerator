export interface NetworkRef {
  code: string;
  name: string;
  active: boolean;
}

export interface MessageTypeRef {
  code: string;      // MTI
  name: string;
  category: string;
  network: string;
  direction: string; // ACQ_TO_ISS | ISS_TO_ACQ | BOTH
}

export interface LoadStep {
  id?: number;
  stepOrder: number;
  startSeconds: number;
  endSeconds: number;
  tpsValue: number;
  concurrency?: number;
}

export interface CampaignRequest {
  name: string;
  description?: string;
  network: string;
  initiator: string;
  category: string;
  config: string;
  expectedDe039?: string;
  active: boolean;
  slaP95MaxMs?: number;
  slaErrorRateMax?: number;
  slaApprovalMin?: number;
  stopOnErrorRate?: number;
  loadSteps: LoadStep[];
}

export interface Campaign {
  id: number;
  name: string;
  description?: string;
  network: string;
  initiator: string;
  category: string;
  config: string;
  expectedDe039?: string;
  active: boolean;
  createdAt?: string;
  createdByLogin?: string;
  slaP95MaxMs?: number;
  slaErrorRateMax?: number;
  slaApprovalMin?: number;
  stopOnErrorRate?: number;
  loadSteps: LoadStep[];
}

export interface CampaignExecution {
  campaignExecutionId?: number;
  status: string;
  txTotal: number;
  txApproved: number;
  txDeclined: number;
  verdict: string;
  verdictDetail: string;
  tpsActualAvg: number;
  responseTimeAvg: number;
}
