/** Link bearer stays in private memory, never storage/router state/JSON. */
export class PersonalAccountIntent {
  readonly requestId:string;
  readonly kind:'PASSWORD_RECOVERY'|'EMAIL_CHANGE';
  #token:string;
  constructor(id:string,kind:'PASSWORD_RECOVERY'|'EMAIL_CHANGE',token:string){this.requestId=id;this.kind=kind;this.#token=token;}
  token():string{return this.#token;}
  clear():void{this.#token='';}
  toJSON():never{throw new Error('Personal account link cannot be serialized');}
  toString():string{return 'PersonalAccountIntent[protected]';}
}
export class ProtectedPersonalAccountLinkMemory {
  #pending:PersonalAccountIntent|null=null;
  retain(localUrl:string):boolean {
    if(typeof localUrl!=='string'||localUrl.length>2048)return false;
    const match=/^\/account\/(recovery|verify-email)(\?[^#]*)?(#[\s\S]*)?$/.exec(localUrl);
    if(!match||!match[3])return false;
    this.clear();if(match[2])return false;
    const fragment=new URLSearchParams(match[3].slice(1));let count=0;fragment.forEach(()=>++count);
    if(count!==2||fragment.getAll('request').length!==1||fragment.getAll('token').length!==1)return false;
    const id=fragment.get('request')||'',token=fragment.get('token')||'';
    if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)||!/^[A-Za-z0-9_-]{43}$/.test(token))return false;
    this.#pending=new PersonalAccountIntent(id.toLowerCase(),match[1]==='recovery'?'PASSWORD_RECOVERY':'EMAIL_CHANGE',token);return true;
  }
  take(kind?:'PASSWORD_RECOVERY'|'EMAIL_CHANGE'):PersonalAccountIntent|null{if(kind&&this.#pending?.kind!==kind)return null;const value=this.#pending;this.#pending=null;return value;}
  clear():void{this.#pending?.clear();this.#pending=null;}
}
