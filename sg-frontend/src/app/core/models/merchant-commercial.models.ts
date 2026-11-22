export type CommercialPhase = 'DRAFT' | 'AWAITING_CONFIRMATION' | 'IN_REVIEW' | 'TO_DISTRIBUTE' |
  'ASSIGNMENT_EXCEPTION' | 'COMPLEMENTS_REQUIRED' | 'REEXAMINATION' | 'READY_FOR_BO' | 'TRANSMITTED_BO';
export interface CommercialCriteria { region: string; city: string; activity: string; }
export interface CommercialState {
  caseId: string; origin: 'AUTO'|'ASSISTED'; phase: CommercialPhase; version: number;
  criteria: CommercialCriteria|null; confirmedCaseVersion: number|null; reviewedCaseVersion: number|null;
  route: string|null; assignmentReason: string|null; reviewedAt: string|null; transmittedAt: string|null;
}
export interface CommercialAssignee { id: string; login: string; team: string; }
export interface CommercialWorkspace {
  state: CommercialState|null; acquirerCode: string; caseVersion: number; actions: string[];
  responsible: CommercialAssignee|null; initiator: CommercialAssignee|null; targets: CommercialAssignee[];
}
export interface CommercialReference { code: string; label: string; active: boolean; version: number; extra: string|null; }
export interface CommercialHistory { version: number; command: string; actor: string; reason: string|null; fromPhase: string|null; toPhase: CommercialPhase; at: string; }
export interface CommercialConfirmation { id: string; expiresAt: string; status: 'PREPARED'|'EXPIRED'|'REVOKED'|'CONFIRMED'; deliveryState?:'NOT_ATTEMPTED'|'SENDING'|'ACCEPTED'|'UNCERTAIN'|'BLOCKED'; }
export interface CommercialCommand {
  requestId: string; expectedWorkflowVersion: number; expectedCaseVersion: number;
  criteria?: CommercialCriteria; targetMemberId?: string; reason?: string;
}
export interface CommercialResult { caseId: string; phase: CommercialPhase; workflowVersion: number; replayed: boolean; }
export type CommercialReferenceKind='REGION'|'CITY'|'ACTIVITY';
export interface CommercialTeam { id:string;code:string;label:string;active:boolean;version:number; }
export interface CommercialTeamMember { id:string;teamId:string;userId:string;teamRole:'FIELD'|'LEAD';canReview:boolean;active:boolean;version:number; }
export interface CommercialRule { id:string;priority:number;region:string|null;city:string|null;activity:string|null;target:string;active:boolean;version:number; }
export interface CommercialConfiguration {
  teams:CommercialTeam[];members:CommercialTeamMember[];rules:CommercialRule[];
  fallback:{memberId:string|null;version:number}|null;
  users:Array<{id:string;login:string;active:boolean}>;ruleCodes:Record<string,string>;
}
export interface CommercialPreview { route:'FIELD'|'LEAD'|'FALLBACK'|'EXCEPTION';
  target:{id:string;subject:string;teamRole:string}|null;rule:{id:string;version:number}|null;reason:string; }
