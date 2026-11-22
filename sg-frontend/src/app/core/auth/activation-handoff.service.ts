import {Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {ProtectedActivationLinkMemory} from './protected-activation-link';

@Injectable({providedIn:'root'})
export class ActivationHandoffService extends ProtectedActivationLinkMemory {
  private readonly updated=new Subject<void>();
  readonly changes=this.updated.asObservable();
  override retain(localUrl:string):boolean {
    let supplied=false;
    if(typeof localUrl==='string'&&/^\/activation(?:[?#]|$)/.test(localUrl)){
      const address=new URL(localUrl,'https://local.invalid');supplied=address.searchParams.has('token')||new URLSearchParams(address.hash.slice(1)).has('token');
    }
    if(supplied)this.clear();
    const retained=super.retain(localUrl);
    // Invalid replacements must notify the visible page too, so it cannot keep
    // the preceding token. A clean address is not a replacement or a reset.
    if(supplied)this.updated.next();return retained;
  }
  capture(localUrl:string):void {
    if(!/^\/activation(?:[?#]|$)/.test(localUrl))return;
    this.retain(localUrl);
    const address=new URL(window.location.href),fragment=new URLSearchParams(address.hash.slice(1));
    if(address.searchParams.has('token')||fragment.has('token')){
      address.searchParams.delete('token');fragment.delete('token');address.hash=fragment.toString();
      window.history.replaceState(window.history.state,'',address.pathname+address.search+address.hash);
    }
  }
}
