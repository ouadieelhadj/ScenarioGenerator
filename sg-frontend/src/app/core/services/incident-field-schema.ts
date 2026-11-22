export interface IncidentField {key:string;label:string;help:string;required:boolean;maxLength:number;}
const forbidden=new Set(['password','motdepasse','pan','pin','cvv','cvc','zmk','lmk','secret','token','constructor','prototype']);
const metadataKeys=new Set(['label','help','required','maxLength']);
function keyAllowed(key:string){return /^[A-Za-z][A-Za-z0-9_]{0,47}$/.test(key)&&!forbidden.has(key.replace(/_/g,'').toLowerCase());}
export function readIncidentFields(value:unknown):IncidentField[]|null {
 try{
  const source=typeof value==='string'?JSON.parse(value):value;
  if(!source||typeof source!=='object'||Array.isArray(source)||Object.keys(source).length>12)return null;
  const result:IncidentField[]=[];
  for(const [key,raw] of Object.entries(source)){
   if(!keyAllowed(key))return null;
   if(typeof raw==='boolean'){result.push({key,label:key,help:'',required:raw,maxLength:500});continue;}
   if(!raw||typeof raw!=='object'||Array.isArray(raw)||Object.keys(raw).some(k=>!metadataKeys.has(k)))return null;
   const metadata=raw as Record<string,unknown>,label=Object.hasOwn(metadata,'label')?metadata['label']:key,help=Object.hasOwn(metadata,'help')?metadata['help']:'',required=Object.hasOwn(metadata,'required')?metadata['required']:false,maxLength=Object.hasOwn(metadata,'maxLength')?metadata['maxLength']:500;
   if(typeof label!=='string'||!label.trim()||label.length>120||typeof help!=='string'||help.length>300||typeof required!=='boolean'||typeof maxLength!=='number'||!Number.isSafeInteger(maxLength)||maxLength<1||maxLength>500)return null;
   result.push({key,label:label.trim(),help:help.trim(),required,maxLength});
  }
  return result;
 }catch{return null;}
}
export function writeIncidentFields(fields:IncidentField[]):Record<string,unknown> {
 if(fields.length>12||new Set(fields.map(f=>f.key)).size!==fields.length)throw new Error('INVALID_FIELD_SCHEMA');
 const schema=Object.fromEntries(fields.map(({key,...definition})=>[key,definition]));
 const normalized=readIncidentFields(schema);
 if(!normalized)throw new Error('INVALID_FIELD_SCHEMA');
 return Object.fromEntries(normalized.map(({key,...definition})=>[key,definition]));
}
