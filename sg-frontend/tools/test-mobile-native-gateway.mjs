import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const ts=createRequire(import.meta.url)('typescript');
const compiled=ts.transpileModule(readFileSync(new URL('../src/mobile/services/mobile-native-gateway.ts',import.meta.url),'utf8'),
 {compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}}).outputText;
const {nativeGatewayConfiguration,nativeGatewayRequest}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'));
const gateway='https://merchant.futurpayment.com/api-merchant-portal';
test('native configuration has one explicit HTTPS gateway, no localhost fallback',()=>{
 assert.equal(nativeGatewayConfiguration({version:1,gatewayUrl:gateway}),gateway);
 for(const value of [null,{},[],{version:2,gatewayUrl:gateway},{version:1,gatewayUrl:gateway,token:'never-store-secrets'}])
  assert.throws(()=>nativeGatewayConfiguration(value),{message:'Configuration HTTPS mobile invalide.'});
});
test('insecure credentialed query fragment and ambiguous gateway configurations are refused',()=>{
 for(const value of ['http://portal.futurpayment.com/api-merchant-portal','https://user@portal.futurpayment.com/api-merchant-portal',
  gateway+'?token=forbidden',gateway+'#fragment',gateway+'/',gateway+'/../api-merchant-portal',gateway+'/auth',gateway+'\n'])
  assert.throws(()=>nativeGatewayConfiguration({version:1,gatewayUrl:value}),{message:'Configuration HTTPS mobile invalide.'});
});
test('identity and onboarding requests route to the same configured remote HTTPS gateway',()=>{
 for(const path of ['/auth/login','/auth/merchant-invitations/activate','/api/onboarding/identity/me',
  '/api/merchant-onboarding/v2/dossiers/synthetic','/api/merchant-portal/v1/contexts'])
  assert.equal(nativeGatewayRequest('/api-merchant-portal'+path,gateway),gateway+path);
});
test('API filters survive without rewriting to another host',()=>{
 assert.equal(nativeGatewayRequest('/api-merchant-portal/api/merchant-onboarding/v1/commercial/dossiers?offset=0&limit=25',gateway),
  gateway+'/api/merchant-onboarding/v1/commercial/dossiers?offset=0&limit=25');
});
test('static and foreign resources are not rewritten to the authenticated gateway',()=>{
 for(const value of ['/assets/logo.png','/api-merchant-portal-foreign/auth/login','https://foreign.example.test/api-merchant-portal/auth/login'])
  assert.equal(nativeGatewayRequest(value,gateway),value);
});
test('traversal encoded path changes and protected fragments are never forwarded',()=>{
 for(const value of ['/api-merchant-portal/../auth/login','/api-merchant-portal/api/../auth/login',
  '/api-merchant-portal/%2e%2e/auth/login','/api-merchant-portal/auth/login#token=forbidden'])
  assert.throws(()=>nativeGatewayRequest(value,gateway),{message:'Adresse API mobile invalide.'});
});
test('packaged public runtime config and Capacitor both enforce the declared secure default',async()=>{
 const config=JSON.parse(readFileSync(new URL('../src/assets/merchant-mobile-runtime.json',import.meta.url),'utf8'));
 assert.equal(nativeGatewayConfiguration(config),gateway);
 const compiledConfig=ts.transpileModule(readFileSync(new URL('../capacitor.config.ts',import.meta.url),'utf8'), {compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}}).outputText;
 const capacitor=(await import('data:text/javascript;base64,'+Buffer.from(compiledConfig).toString('base64'))).default;
 assert.equal(capacitor.server.cleartext,false);assert.equal(capacitor.server.androidScheme,'https');
});
