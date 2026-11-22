export interface MobileLoginDiagnostic {
 phase:'NETWORK'|'HTTP'|'SESSION'|'CLIENT';status:number;destination:'HTTPS'|'BUNDLED'|'OTHER'|'NONE';
}
/** Coarse simulator diagnostics only: never return an URL, body, header or credential. */
export function mobileLoginDiagnostic(error:unknown,httpError:boolean,pageUrl:string):MobileLoginDiagnostic {
 const value=error as {status?:unknown;url?:unknown;message?:unknown}|null;
 const status=httpError&&typeof value?.status==='number'&&Number.isInteger(value.status)&&value.status>=0&&value.status<=599?value.status:-1;
 let destination:MobileLoginDiagnostic['destination']='NONE';
 if(httpError&&typeof value?.url==='string'&&value.url){
  try{const protocol=new URL(value.url,pageUrl).protocol;destination=protocol==='https:'?'HTTPS':protocol==='capacitor:'?'BUNDLED':'OTHER';}catch{destination='OTHER';}
 }
 const phase=httpError?(status===0?'NETWORK':'HTTP'):
  value?.message==='Session expirée ou invalide. Reconnectez-vous.'?'SESSION':'CLIENT';
 return{phase,status,destination};
}
