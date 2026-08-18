export type SoftPosDeviceStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'COMPROMISED';
export type SoftPosRouteMode = 'REST_JSON' | 'ISO8583_PERSISTENT';
export interface SoftPosDevice { deviceId:string; merchantId:string; outletId:string; terminalId:string; status:SoftPosDeviceStatus; applicationVersion:string; }
export interface SoftPosRoute { memberId:string; environment:string; primaryMode:SoftPosRouteMode; endpoint:string; connectTimeoutMillis:number; responseTimeoutMillis:number; active:boolean; }
export interface SoftPosTransaction { clientTransactionId:string; status:string; responseCode?:string; authorizationCode?:string; updatedAt:string; }
export interface SoftPosActivation { activationCode:string; expiresAt:string; }
