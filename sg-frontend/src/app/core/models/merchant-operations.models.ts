export type OperationKind = 'ADD_TERMINAL' | 'CHANGE_RIB';
export type OperationState = 'DRAFT' | 'MERCHANT_REVIEW' | 'BACKOFFICE_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXECUTING' | 'UNCERTAIN' | 'SOURCE_PENDING' | 'CONFIRMED' | 'SOURCE_REJECTED' | 'COMPLEMENTS_REQUIRED' | 'SUPERSEDED';
export interface OperationCapabilities { maker: boolean; checker: boolean; backoffice: boolean; subject: string }
export interface OperationDocument { id: string; contentType: string; contentSize: number; status: 'PENDING' | 'CHECKED' | 'REJECTED';
  uploadedBy: string; uploadedAt: string; reviewedBy: string | null; reviewReason: string | null; version: number }
export interface OperationDetails { label: string | null; terminalModel: string | null; connectionType: string | null; currency: string | null;
  location: string | null; settlementContractReference: string | null; accountHolder: string | null }
export interface PortalOperation { id: string; hierarchyNodeId: string; kind: OperationKind; state: OperationState; summary: string; createdBy: string;
  createdAt: string; merchantApprovalRequired: boolean; merchantChecker: string | null; merchantCheckedAt: string | null;
  backofficeChecker: string | null; backofficeCheckedAt: string | null; decisionReason: string | null; sourceReference: string | null;
  sourceResultAt: string | null; version: number; documents: OperationDocument[]; capabilities: OperationCapabilities; sourceNotice: string; details: OperationDetails | null;
  previousOperationId: string | null; nextOperationId: string | null; complementRequestedBy: string | null; complementRequestedAt: string | null; complementReason: string | null; assignment?:RequestAssignment }
export interface RequestAssignment {responsibleSubject:string;version:number;canReassign:boolean;independent:boolean;supervision?:RequestSupervision}
export interface RequestSupervision {checkerSubject:string|null;version:number;canTake:boolean;canPropose:boolean;canRespond:boolean;pendingId:string|null;pendingTarget:string|null;pendingReason:string|null}
export interface RequestCheckerEvent {id:string;action:'TAKE'|'PROPOSE'|'ACCEPT'|'REFUSE';fromChecker:string|null;toChecker:string;actor:string;reason:string;at:string;proposalId:string|null}
export interface RequestAssignmentTarget {identitySubject:string;label:string}
export interface RequestAssignmentEvent {id:string;fromSubject:string;toSubject:string;actor:string;reason:string;at:string;businessVersion:number;previousAssignmentVersion:number}
export interface OperationPayload { label?: string; terminalModel?: string; connectionType?: string; currency?: string; location?: string;
  settlementContractReference?: string; accountHolder?: string; bankCoordinates?: string }
