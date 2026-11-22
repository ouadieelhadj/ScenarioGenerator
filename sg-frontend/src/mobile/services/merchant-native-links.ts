import {ProtectedActivationLinkMemory} from '../../app/core/auth/protected-activation-link';
import {ProtectedCommercialLinkMemory} from '../../app/core/auth/protected-commercial-link';
import type {ProtectedPersonalAccountLinkMemory} from '../../app/core/auth/protected-personal-account-link';

/** Handles Capacitor cold and hot app-links. Secrets never reach router/history/navigation metadata. */
export function dispatchMerchantNativeLink(rawUrl:string,expectedOrigin:string,
    activation:ProtectedActivationLinkMemory,confirmation:ProtectedCommercialLinkMemory,
    navigate:(cleanPath:string)=>void,account?:ProtectedPersonalAccountLinkMemory):boolean {
  if(typeof rawUrl!=='string'||rawUrl.length>2048)return false;
  try{
    const link=new URL(rawUrl),authority=new URL(expectedOrigin);
    if(authority.protocol!=='https:'||authority.origin!==expectedOrigin||link.origin!==expectedOrigin
      ||link.username||link.password)return false;
    const local=link.pathname+link.search+link.hash;
    if(link.pathname==='/account/recovery'||link.pathname==='/account/verify-email'){
      if(!account?.retain(local))return false;navigate(link.pathname);return true;
    }
    if(link.pathname==='/activation'){
      if(!activation.retain(local))return false;navigate('/activation');return true;
    }
    if(!/^\/merchant\/dossier\/[0-9a-f-]{36}$/i.test(link.pathname)||!confirmation.retain(local))return false;
    navigate(link.pathname.toLowerCase());return true;
  }catch{return false;}
}
