/** Scope only automatic bearer injection. Server authentication remains authoritative. */
export function apiTokenDestinationAllowed(requestUrl:string,pageUrl:string,configuredBases:readonly string[]):boolean {
  if(!requestUrl||requestUrl.startsWith('//')||requestUrl.includes('\\'))return false;
  try {
    const target=new URL(requestUrl,pageUrl);
    if(!['http:','https:'].includes(target.protocol)||target.username||target.password||target.hash)return false;
    for(const value of configuredBases){
      if(!value)continue;
      try {
        const base=new URL(value,pageUrl);
        if(!['http:','https:'].includes(base.protocol)||base.username||base.password||base.search||base.hash||base.origin!==target.origin)continue;
        const prefix=base.pathname.replace(/\/+$/,'');
        if(prefix){if(target.pathname===prefix||target.pathname.startsWith(prefix+'/'))return true;}
        else if(target.pathname==='/api'||target.pathname.startsWith('/api/')||target.pathname==='/auth'||target.pathname.startsWith('/auth/'))return true;
      } catch { /* A broken API setting never widens another API's scope. */ }
    }
  } catch { /* Unknown destinations do not receive the application's bearer. */ }
  return false;
}
