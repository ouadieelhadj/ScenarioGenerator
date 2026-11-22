/** Presentation/early validation only; the server rechecks live ownership, counts, content and policy. */
export interface IncidentAttachmentPolicy {maxFiles:number;maxBytes:number;contentTypes:string[];}
export type AttachmentRefusal='POLICY_UNAVAILABLE'|'COUNT_UNAVAILABLE'|'DISABLED'|'LIMIT_REACHED'|'INVALID_TYPE'|'EMPTY'|'TOO_LARGE';
const supported=new Set(['application/pdf','image/jpeg','image/png']);
export function readAttachmentPolicy(value:unknown):IncidentAttachmentPolicy|null {
 if(!value||typeof value!=='object'||Array.isArray(value))return null;
 const row=value as Record<string,unknown>,count=row['maxFiles'],bytes=row['maxBytes'],types=row['contentTypes'];
 if(typeof count!=='number'||!Number.isSafeInteger(count)||count<0||count>20||typeof bytes!=='number'||!Number.isSafeInteger(bytes)||bytes<1||bytes>10_000_000||
   !Array.isArray(types)||types.length<1||types.length>supported.size||new Set(types).size!==types.length||types.some(type=>typeof type!=='string'||!supported.has(type)))return null;
 return {maxFiles:count,maxBytes:bytes,contentTypes:[...types]};
}
export function attachmentRefusal(policy:IncidentAttachmentPolicy|null,count:number|null,file:{size:number;type:string}):AttachmentRefusal|null {
 if(!policy)return 'POLICY_UNAVAILABLE';
 if(count===null||!Number.isSafeInteger(count)||count<0)return 'COUNT_UNAVAILABLE';
 if(policy.maxFiles===0)return 'DISABLED';
 if(count>=policy.maxFiles)return 'LIMIT_REACHED';
 if(!supported.has(file.type)||!policy.contentTypes.includes(file.type))return 'INVALID_TYPE';
 if(!Number.isSafeInteger(file.size)||file.size<=0)return 'EMPTY';
 return file.size>policy.maxBytes?'TOO_LARGE':null;
}
/** Native image capture returns a JPEG; the selected file is still revalidated before upload. */
export function canCaptureIncidentPhoto(policy:IncidentAttachmentPolicy|null,count:number|null):boolean {
 return attachmentRefusal(policy,count,{size:1,type:'image/jpeg'})===null;
}
export function attachmentSizeLabel(bytes:number):string {
 const format=new Intl.NumberFormat('fr-FR',{maximumFractionDigits:3});
 return bytes>=1_000_000?format.format(bytes/1_000_000)+' Mo ('+format.format(bytes)+' octets)':bytes>=1000?format.format(bytes/1000)+' ko ('+format.format(bytes)+' octets)':format.format(bytes)+' octets';
}
