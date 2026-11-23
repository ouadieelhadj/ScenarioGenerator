import { PortalFinancialFilter, PortalPage } from './merchant-activity.models';
export interface PortalCredit {
  id: string; data: { hierarchyNodeId: string; sourceSystem: string; sourceCreditId: string;
    paymentReference: string | null; bookedAt: string; amount: string; currency: string };
  reconciliationStatus: string;
  revisionId: string | null;
}
export interface PortalCreditCorrection {
  id: string; creditId: string; previousRevisionId: string | null; revisionNumber: number;
  data: PortalCredit['data']; status: 'PROPOSED' | 'APPROVED' | 'REJECTED'; proposedBy: string;
  proposedAt: string; proposalReason: string; decidedBy: string | null; decidedAt: string | null;
  decisionReason: string | null; version: number;
}
export interface PortalReconciliationMatch {
  id: string; settlementId: string; creditId: string; status: string; sourceCurrent: boolean; difference: string;
  proposedBy: string; proposedAt: string; proposalReason: string; decidedBy: string | null;
  decidedAt: string | null; decisionReason: string | null; version: number;
}
export interface PortalSettlementReconciliation {
  id: string; reference: string; hierarchyNodeId: string; amount: string; currency: string; executedAt: string | null;
  reconciliationStatus: string; candidates: PortalCredit[]; match: PortalReconciliationMatch | null;
  grossAmount: string; feesAmount: string; adjustmentsAmount: string;
}
export interface ReconciliationPermissions { maker: boolean; checker: boolean }
export type ReconciliationFilter = Omit<PortalFinancialFilter, 'kind' | 'status'>;
export type ReconciliationPage<T> = PortalPage<T>;
