import {Capacitor,registerPlugin} from '@capacitor/core';
import {exportIncidentBlob,IncidentExportFile} from './incident-file-export';

const nativeCopy=registerPlugin<{save:(input:{base64:string;contentType:string;filename:string})=>Promise<{saved:boolean}>}>('IncidentFileDownload');
export function exportIncidentAttachment(blob:Blob,file:IncidentExportFile,isCurrent:()=>boolean){
 return exportIncidentBlob(blob,file,{
  native:Capacitor.isNativePlatform(),
  readBase64:value=>new Promise<string>((resolve,reject)=>{
   const reader=new FileReader();const prefix='data:'+value.type+';base64,';
   reader.onerror=reader.onabort=()=>reject(new Error('INCIDENT_EXPORT_ENCODING'));
   reader.onload=()=>typeof reader.result==='string'&&reader.result.startsWith(prefix)?resolve(reader.result.slice(prefix.length)):reject(new Error('INCIDENT_EXPORT_ENCODING'));
   reader.readAsDataURL(value);
  }),
  saveNative:input=>nativeCopy.save(input),
  saveWeb:(value,filename)=>{
   const address=URL.createObjectURL(value),link=document.createElement('a');
   try{link.href=address;link.download=filename;link.rel='noopener';document.body.appendChild(link);link.click();}
   finally{link.remove();setTimeout(()=>URL.revokeObjectURL(address),1000);}
  },
 },isCurrent);
}
