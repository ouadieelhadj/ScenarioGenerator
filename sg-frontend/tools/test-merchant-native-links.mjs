import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {randomBytes} from 'node:crypto';
const ts=createRequire(import.meta.url)('typescript');
async function load(relative){const source=ts.transpileModule(readFileSync(new URL(relative,import.meta.url),'utf8'),
 {compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}}).outputText;
 return import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));}
const {ProtectedActivationLinkMemory}=await load('../src/app/core/auth/protected-activation-link.ts');
const {ProtectedCommercialLinkMemory}=await load('../src/app/core/auth/protected-commercial-link.ts');
const {ProtectedPersonalAccountLinkMemory}=await load('../src/app/core/auth/protected-personal-account-link.ts');
const {dispatchMerchantNativeLink}=await load('../src/mobile/services/merchant-native-links.ts');
const origin='https://portal.futurpayment.com',id='98000000-0000-4000-8000-000000000001',receipt='98000000-0000-4000-8000-000000000002';
const opaque=()=>randomBytes(32).toString('base64url');
function ports(){const activation=new ProtectedActivationLinkMemory(),confirmation=new ProtectedCommercialLinkMemory(),paths=[];
 return {activation,confirmation,paths,dispatch:raw=>dispatchMerchantNativeLink(raw,origin,activation,confirmation,path=>paths.push(path))};}
test('activation fragment opens only the fixed clean route and is consumed once',()=>{
 const p=ports(),value=opaque();assert.equal(p.dispatch(origin+'/activation#token='+value),true);
 assert.deepEqual(p.paths,['/activation']);assert.equal(p.activation.take()===value,true);assert.equal(p.activation.take(),'');
});
test('legacy query is accepted without propagating it to native navigation',()=>{
 const p=ports(),value=opaque();assert.equal(p.dispatch(origin+'/activation?token='+value),true);
 assert.deepEqual(p.paths,['/activation']);assert.equal(p.activation.take()===value,true);
});
test('ambiguous duplicate and unexpected activation fields are refused',()=>{
 const value=opaque();for(const suffix of ['?token='+value+'#token='+value,'#token='+value+'&token='+value,
  '#token='+value+'&returnUrl=https://evil.example.test','?other=field#token='+value,'#token=invalid%0Avalue']){
  const p=ports();assert.equal(p.dispatch(origin+'/activation'+suffix),false);assert.deepEqual(p.paths,[]);assert.equal(p.activation.take(),'');}
});
test('confirmation app-link retains secret in existing protected memory and opens a fixed local dossier',()=>{
 const p=ports(),value=opaque();assert.equal(p.dispatch(origin+'/merchant/dossier/'+id+'#commercialToken='+value+'&commercialConfirmation='+receipt),true);
 assert.deepEqual(p.paths,['/merchant/dossier/'+id]);assert.equal(p.confirmation.resumePath('MERCHANT'),'/merchant/dossier/'+id);
 assert.equal(p.confirmation.take(id)?.token===value,true);assert.equal(p.confirmation.take(id),null);
});
test('foreign insecure credentialed origins and different ports never navigate',()=>{
 const suffix='/activation#token='+opaque();for(const raw of ['https://evil.example.test'+suffix,'http://portal.futurpayment.com'+suffix,
  'https://portal.futurpayment.com.evil.example.test'+suffix,'https://portal.futurpayment.com:444'+suffix,
  'https://user@portal.futurpayment.com'+suffix,'file:///activation#token='+opaque(),'not-a-url']){
  const p=ports();assert.equal(p.dispatch(raw),false);assert.deepEqual(p.paths,[]);assert.equal(p.activation.take(),'');}
});
test('unsupported routes and malformed confirmation content are rejected before authentication',()=>{
 for(const path of ['/admin#token='+opaque(),'/merchant/dossier/not-a-uuid#commercialToken='+opaque()+'&commercialConfirmation='+receipt,
  '/merchant/dossier/'+id+'?returnUrl=external#commercialToken='+opaque()+'&commercialConfirmation='+receipt,
  '/merchant/dossier/'+id+'#commercialToken=invalid&commercialConfirmation='+receipt]){
  const p=ports();assert.equal(p.dispatch(origin+path),false);assert.deepEqual(p.paths,[]);assert.equal(p.confirmation.take(id),null);}
});
test('cold and hot activation arrivals replace memory without retaining an earlier token',()=>{
 const p=ports(),first=opaque(),second=opaque();p.dispatch(origin+'/activation#token='+first);p.dispatch(origin+'/activation#token='+second);
 assert.equal(p.activation.take()===second,true);assert.equal(p.activation.take(),'');assert.deepEqual(p.paths,['/activation','/activation']);
});
test('activation secret is never enumerable and clear revokes pending local intent',()=>{
 const p=ports(),value=opaque();p.dispatch(origin+'/activation#token='+value);assert.equal(JSON.stringify(p.activation),'{}');
 assert.equal(Object.keys(p.activation).length,0);p.activation.clear();assert.equal(p.activation.take(),'');
});
test('malformed confirmed link cannot grant a commercial actor a personal confirmation',()=>{
 const p=ports();p.dispatch(origin+'/merchant/dossier/'+id+'#commercialToken='+opaque()+'&commercialConfirmation='+receipt);
 assert.equal(p.confirmation.resumePath('COMMERCIAL'),null);assert.equal(p.confirmation.take(id),null);
});
test('Android declares both HTTPS app-link paths and excludes protected state from backups',()=>{
 const manifest=readFileSync(new URL('../android/app/src/main/AndroidManifest.xml',import.meta.url),'utf8');
 assert.equal(manifest.includes('android:allowBackup="false"'),true);
 assert.equal(manifest.includes('android:scheme="https"'),true);
 assert.equal(manifest.includes('android:pathPrefix="/activation"'),true);
 assert.equal(manifest.includes('android:pathPrefix="/merchant/dossier/"'),true);
});
test('personal recovery and email links use clean routes with protected native memory',()=>{
 for(const name of ['recovery','verify-email']){const p=ports(),account=new ProtectedPersonalAccountLinkMemory(),token=opaque();
  assert.equal(dispatchMerchantNativeLink(origin+'/account/'+name+'#request='+id+'&token='+token,origin,p.activation,p.confirmation,path=>p.paths.push(path),account),true);
  assert.deepEqual(p.paths,['/account/'+name]);assert.equal(account.take()?.token()===token,true);assert.equal(account.take(),null);}
});
test('personal link with another origin or query cannot enter native account memory',()=>{
 for(const raw of ['https://evil.example.test/account/recovery#request='+id+'&token='+opaque(),origin+'/account/recovery?request='+id+'&token='+opaque()]){const p=ports(),account=new ProtectedPersonalAccountLinkMemory();
  assert.equal(dispatchMerchantNativeLink(raw,origin,p.activation,p.confirmation,path=>p.paths.push(path),account),false);assert.deepEqual(p.paths,[]);assert.equal(account.take(),null);}
});
