export const incidentReferenceKinds=[
 {code:'TERMINAL',label:'TPE'},{code:'POINT_OF_SALE',label:'Point de vente'},
 {code:'AUTHORIZATION',label:'Autorisation'},{code:'TRANSACTION',label:'Opération'},
 {code:'SETTLEMENT',label:'Règlement ou remise'},{code:'FEE',label:'Commission ou frais'},{code:'EQUIPMENT_REQUEST',label:'Demande TPE'},
 {code:'BANK_DETAILS_REQUEST',label:'Demande RIB'},{code:'CHARGEBACK',label:'Contestation'},
] as const;
export type IncidentReferenceKind=typeof incidentReferenceKinds[number]['code'];
export interface IncidentReference {kind:IncidentReferenceKind;id:string;}
export interface IncidentReferenceOption extends IncidentReference {label:string;}
const validId=(id:unknown):id is string=>typeof id==='string'&&/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id)&&id!=='00000000-0000-0000-0000-000000000000';
export function readReferenceOptions(value:unknown,kind?:IncidentReferenceKind):IncidentReferenceOption[]|null{
 if(!Array.isArray(value)||value.length>50)return null;
 const seen=new Set<string>(),result:IncidentReferenceOption[]=[];
 for(const input of value){
  if(!input||typeof input!=='object'||!validId(input.id)||!incidentReferenceKinds.some(k=>k.code===input.kind)||kind&&input.kind!==kind||typeof input.label!=='string'||!input.label.trim()||input.label.length>300)return null;
  const key=input.kind+':'+input.id.toLowerCase();if(seen.has(key))return null;seen.add(key);
  result.push({kind:input.kind,id:input.id,label:input.label});
 }
 return result;
}
export function referencePayload(value:IncidentReferenceOption[]):IncidentReference[]{
 const safe=readReferenceOptions(value);if(!safe||safe.length>5)throw new Error('INVALID_BUSINESS_REFERENCES');
 return safe.map(({kind,id})=>({kind,id}));
}
