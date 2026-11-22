/** Transport-independent copy policy; server permission and CLEAN are rechecked by every download GET. */
export interface IncidentExportFile {id:string;contentType:string;scanState:string}
export interface IncidentExportPorts {
 native:boolean;
 readBase64:(blob:Blob)=>Promise<string>;
 saveNative:(input:{base64:string;contentType:string;filename:string})=>Promise<{saved:boolean}>;
 saveWeb:(blob:Blob,filename:string)=>void;
}
export type IncidentExportResult='SAVED'|'WEB_STARTED'|'CANCELLED'|'STALE';
const extensions:Record<string,string>={'application/pdf':'.pdf','image/png':'.png','image/jpeg':'.jpg'};
export function incidentExportName(blob:Blob,file:IncidentExportFile):string {
 if(!blob||!file||file.scanState!=='CLEAN'||!Object.prototype.hasOwnProperty.call(extensions,file.contentType)||blob.type!==file.contentType||
    !Number.isInteger(blob.size)||blob.size<1||blob.size>10_000_000||!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(file.id))throw new Error('INCIDENT_EXPORT_REFUSED');
 return 'incident-'+file.id.toLowerCase()+extensions[file.contentType];
}
export async function exportIncidentBlob(blob:Blob,file:IncidentExportFile,ports:IncidentExportPorts,isCurrent:()=>boolean):Promise<IncidentExportResult> {
 const filename=incidentExportName(blob,file);
 if(!isCurrent())return 'STALE';
 if(!ports.native){ports.saveWeb(blob,filename);return 'WEB_STARTED';}
 const base64=await ports.readBase64(blob);
 if(!isCurrent())return 'STALE';
 if(base64.length!==4*Math.ceil(blob.size/3)||!/^[A-Za-z0-9+/]+={0,2}$/.test(base64))throw new Error('INCIDENT_EXPORT_ENCODING');
 const result=await ports.saveNative({base64,contentType:file.contentType,filename});
 if(!isCurrent())return 'STALE';
 if(typeof result?.saved!=='boolean')throw new Error('INCIDENT_EXPORT_UNCONFIRMED');
 return result.saved?'SAVED':'CANCELLED';
}
