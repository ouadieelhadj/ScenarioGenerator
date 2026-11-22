import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { ProtectedPersonalAccountLinkMemory } from './protected-personal-account-link';
@Injectable({providedIn:'root'})
export class PersonalAccountHandoffService extends ProtectedPersonalAccountLinkMemory {
 readonly changes=new Subject<void>();
 override retain(url:string):boolean{const accepted=super.retain(url);if(accepted)this.changes.next();return accepted;}
 capture(url:string):boolean|null{
  if(!/^\/account\/(recovery|verify-email)(?:[?#]|$)/.test(url)||!/[?#]/.test(url))return null;
  const accepted=this.retain(url);
  window.history.replaceState(window.history.state,'',window.location.pathname);
  return accepted;
 }
}
