/** This is a declared client channel, never device attestation or an authorization claim. */
export function incidentCreationSuffix(mobile:boolean,platform:string):''|'/android'|'/ios' {
    if(!mobile)return '';
    // Preserve the existing Android/mobile-web endpoint while separating native iPhone.
    return platform==='ios'?'/ios':'/android';
}
