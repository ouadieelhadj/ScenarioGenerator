/** Activation intent is kept only in memory; no token is serialized or put on a login URL. */
export class ProtectedActivationLinkMemory {
  #pending:string|null=null;
  retain(localUrl:string):boolean {
    if(typeof localUrl!=='string'||localUrl.length>2048)return false;
    const match=/^\/activation(\?[^#]*)?(#[\s\S]*)?$/.exec(localUrl);
    if(!match)return false;
    const query=new URLSearchParams((match[1]||'').slice(1));
    const fragment=new URLSearchParams((match[2]||'').slice(1));
    if(!query.has('token')&&!fragment.has('token'))return false;
    this.#pending=null;
    let queryCount=0,fragmentCount=0;query.forEach(()=>++queryCount);fragment.forEach(()=>++fragmentCount);
    if(queryCount+fragmentCount!==1||query.getAll('token').length+fragment.getAll('token').length!==1)return false;
    const candidate=fragment.get('token')??query.get('token');
    if(!candidate||!/^[A-Za-z0-9_-]{1,256}$/.test(candidate))return false;
    this.#pending=candidate;return true;
  }
  take():string {const pending=this.#pending;this.#pending=null;return pending||'';}
  clear():void {this.#pending=null;}
}
