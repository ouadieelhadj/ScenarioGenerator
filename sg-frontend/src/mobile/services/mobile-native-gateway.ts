const prefix='/api-merchant-portal';
/** Public routing configuration only: never passwords, tokens or customer data. */
export function nativeGatewayConfiguration(input:unknown):string {
  if(!input||typeof input!=='object'||Array.isArray(input))throw new Error('Configuration HTTPS mobile invalide.');
  const value=input as Record<string,unknown>;
  if(Object.keys(value).some(key=>!['version','gatewayUrl'].includes(key))||value['version']!==1
      ||typeof value['gatewayUrl']!=='string'||value['gatewayUrl'].length>512)throw new Error('Configuration HTTPS mobile invalide.');
  try{
    const url=new URL(value['gatewayUrl']);
    if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash
        ||url.pathname!==prefix||url.href!==value['gatewayUrl'])throw new Error();
    return url.href;
  }catch{throw new Error('Configuration HTTPS mobile invalide.');}
}
export function nativeGatewayRequest(requestUrl:string,gatewayUrl:string):string {
  if(!requestUrl.startsWith(prefix+'/'))return requestUrl;
  const gateway=nativeGatewayConfiguration({version:1,gatewayUrl});
  const address=new URL(requestUrl,'https://mobile.local.invalid');
  if(address.hash||!address.pathname.startsWith(prefix+'/')||address.pathname!==requestUrl.split('?')[0])
    throw new Error('Adresse API mobile invalide.');
  return gateway+address.pathname.slice(prefix.length)+address.search;
}
