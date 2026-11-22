import { Injectable } from '@angular/core';
import { ProtectedCommercialLinkMemory } from './protected-commercial-link';

@Injectable({providedIn:'root'})
export class CommercialConfirmationHandoffService extends ProtectedCommercialLinkMemory {
  capture(url:string):void {
    const fragment=url.slice(url.indexOf('#')+1);
    const values=new URLSearchParams(fragment);
    if(url.includes('#')&&(values.has('commercialToken')||values.has('commercialConfirmation'))){
      this.retain(url);
      // Scrub before authentication or any backend request. Never put the secret on the login URL.
      window.history.replaceState(window.history.state,'',window.location.pathname+window.location.search);
    }
  }
}
