export interface ProtectedCommercialLink { caseId:string|null; confirmationId:string; token:string; }
const uuid='[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const route=new RegExp('^/merchant/dossier(?:/('+uuid+'))?$','i');
const identifier=new RegExp('^'+uuid+'$','i');

/** No storage, arbitrary return URL or enumerable secret. Authority remains server-side. */
export class ProtectedCommercialLinkMemory {
  #pending:ProtectedCommercialLink|null=null;
  retain(url:string):boolean {
    if(typeof url!=='string'||url.length>2048)return false;
    const hash=url.indexOf('#');if(hash<0)return false;
    const values=new URLSearchParams(url.slice(hash+1));
    if(!values.has('commercialToken')&&!values.has('commercialConfirmation'))return false;
    this.#pending=null;
    const path=url.slice(0,hash),match=route.exec(path);
    let keyCount=0;values.forEach(()=>++keyCount);
    const token=values.get('commercialToken'),confirmationId=values.get('commercialConfirmation');
    if(!match||keyCount!==2||values.getAll('commercialToken').length!==1||values.getAll('commercialConfirmation').length!==1
      ||!token||!/^[A-Za-z0-9_-]{43}$/.test(token)||!confirmationId||!identifier.test(confirmationId))return false;
    this.#pending={caseId:match[1]?.toLowerCase()||null,confirmationId:confirmationId.toLowerCase(),token};return true;
  }
  resumePath(role:string|undefined):string|null {
    if(!['MERCHANT','COMMERCANT'].includes(role||'')){this.clear();return null;}
    return this.#pending?'/merchant/dossier'+(this.#pending.caseId?'/'+this.#pending.caseId:''):null;
  }
  take(caseId:string):ProtectedCommercialLink|null {
    const value=this.#pending;this.clear();return identifier.test(caseId)&&value&&(value.caseId===null||value.caseId===caseId.toLowerCase())?{...value,caseId:caseId.toLowerCase()}:null;
  }
  clearFor(caseId:string):void { if(this.#pending&&(this.#pending.caseId===null||this.#pending.caseId===caseId.toLowerCase()))this.clear(); }
  clear():void { this.#pending=null; }
}
