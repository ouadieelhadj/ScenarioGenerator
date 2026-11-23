import { OperationCapabilities,RequestAssignment } from './merchant-operations.models';
export interface ChargebackData { hierarchyNodeId:string;transactionId:string;sourceSystem:string;sourceCaseId:string;reference:string;amount:string;currency:string;
  reasonCode:string;reasonLabel:string;sourceStatus:string;canonicalStatus:'OPEN'|'CLOSED'|'CANCELLED'|'UNKNOWN';openedAt:string;deadlineAt:string;
  sourceUpdatedAt:string;officialOutcome:string|null;mappingVersion:string }
export interface ChargebackDocument {id:string;contentType:string;contentSize:number;uploadedAt:string}
export interface ChargebackResponse {id:string;response:'ACCEPT'|'CONTEST';comment:string;state:string;sourceCurrent:boolean;createdBy:string;createdAt:string;version:number;
  merchantChecker:string|null;merchantCheckedAt:string|null;merchantReason:string|null;backofficeChecker:string|null;backofficeCheckedAt:string|null;backofficeReason:string|null;
  invalidatedBy:string|null;invalidatedAt:string|null;invalidationReason:string|null;documents:ChargebackDocument[];assignment?:RequestAssignment}
export interface PortalChargeback {id:string;data:ChargebackData;version:number;responses:ChargebackResponse[];capabilities:OperationCapabilities;
  notifications:{kind:string;dueAt:string;status:string;blockReason?:string|null}[];networkNotice:string}
export interface ChargebackDeliveryAttempt {id:string;state:string;startedAt:string;finishedAt:string|null;failureCode:string|null;retryAuthorizedBy:string|null;retryAuthorizedAt:string|null}
export interface ChargebackDelivery {id:string;kind:string;state:string;acceptedAt:string|null;version:number;attempts:ChargebackDeliveryAttempt[]}
