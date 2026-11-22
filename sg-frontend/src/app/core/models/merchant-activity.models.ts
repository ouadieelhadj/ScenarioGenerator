export type PortalRecordKind = 'AUTHORIZATION' | 'TRANSACTION' | 'FEE' | 'SETTLEMENT';
export type PortalNodeType = 'MERCHANT' | 'CHAIN_GROUP' | 'CHAIN' | 'OUTLET' | 'PDV' | 'TERMINAL';
export interface PortalPage<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
export interface PortalNode {
  id: string; merchantId: string; parentNodeId: string | null; nodeType: PortalNodeType;
  sourceSystem: string; sourceObjectId: string; label: string; mid: string | null; tid: string | null;
  active: boolean; sourceUpdatedAt: string; lastSyncedAt: string;
}
export interface PortalFinancialData {
  hierarchyNodeId: string; kind: PortalRecordKind; sourceSystem: string; sourceObjectId: string;
  sourceUpdatedAt: string; mappingVersion: string; reference: string; relatedTransactionId: string | null;
  originalTransactionId: string | null; occurredAt: string; operationType: string;
  channel: string | null; network: string | null; amount: string; currency: string; status: string;
  sourceStatus: string; rrn: string | null; stan: string | null; authorizationCode: string | null;
  responseCode: string | null; responseLabel: string | null; feeType: string | null;
  calculationBase: string | null; feeRate: string | null; fixedAmount: string | null;
  taxAmount: string | null; estimated: boolean | null; grossAmount: string | null;
  feesAmount: string | null; adjustmentsAmount: string | null; periodStart: string | null;
  periodEnd: string | null; plannedAt: string | null; executedAt: string | null; bankConfirmedAt: string | null;
}
export interface PortalFinancialView { id: string; receivedAt: string; lastSyncedAt: string; data: PortalFinancialData }
export interface PortalFinancialRevision { id: string; sourceUpdatedAt: string; observedAt: string; captureReason: 'INITIAL_IMPORT'|'SOURCE_UPDATE'|'EXISTING_BASELINE'; data: PortalFinancialData }
export interface PortalFinancialTotal { kind: PortalRecordKind; currency: string; status: string; count: number; amount: string; latestSourceUpdate: string }
export interface PortalFinancialDaily { date: string; kind: PortalRecordKind; currency: string; status: string; count: number; amount: string }
export interface PortalFinancialSummary { calculatedAt: string; totals: PortalFinancialTotal[]; daily: PortalFinancialDaily[] }
export interface PortalFinancialFilter { from: string; to: string; hierarchyNodeId?: string; kind?: PortalRecordKind; currency?: string; status?: string }
export interface PortalSettlementComposition {
  status: 'NOT_RECEIVED' | 'PARTIAL' | 'COMPLETE' | 'STALE'; sourceUpdatedAt: string | null; receivedAt: string | null;
  expectedLines: number; receivedLines: number; currency: string | null;
  sourceGross: string | null; sourceFees: string | null; sourceAdjustments: string | null; sourceNet: string | null;
  importedGross: string | null; importedFees: string | null; importedAdjustments: string | null; importedNet: string | null;
  lines: PortalPage<{id: string; sourceLineId: string; role: 'GROSS' | 'FEE' | 'ADJUSTMENT'; financialRecordId: string | null; amount: string}>;
}

/** No conversion to IEEE-754: preserve the exact amount returned by the server. */
export function portalMoney(value: string | null | undefined): string {
  if (value == null) return 'Non fourni';
  if (!/^-?\d+(\.\d{1,4})?$/.test(value)) return 'Montant invalide';
  const [whole, decimals = ''] = value.split('.');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '\u202f') + (decimals ? ',' + decimals : '');
}
