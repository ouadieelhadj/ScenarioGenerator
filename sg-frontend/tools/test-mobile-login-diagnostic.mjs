import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const ts=createRequire(import.meta.url)('typescript');
const source=ts.transpileModule(readFileSync(new URL('../src/mobile/services/mobile-login-diagnostic.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022}}).outputText;
const {mobileLoginDiagnostic:diagnose}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
test('remote transport failure is distinguished from a local bundled request',()=>{
 assert.deepEqual(diagnose({status:0,url:'https://merchant.futurpayment.com/auth/login'},true,'capacitor://localhost/login'),{phase:'NETWORK',status:0,destination:'HTTPS'});
 assert.deepEqual(diagnose({status:0,url:'/auth/login'},true,'capacitor://localhost/login'),{phase:'NETWORK',status:0,destination:'BUNDLED'});
});
test('HTTP refusal and malformed successful payload retain their actual status',()=>{
 for(const status of [200,400,401,403,429,500])assert.equal(diagnose({status,url:'https://merchant.futurpayment.com/auth/login'},true,'capacitor://localhost').status,status);
});
test('client and session parsing failures are distinct without exporting their message',()=>{
 assert.equal(diagnose(new Error('Session expirée ou invalide. Reconnectez-vous.'),false,'capacitor://localhost').phase,'SESSION');
 const result=diagnose(new Error('private-synthetic-marker'),false,'capacitor://localhost');
 assert.deepEqual(result,{phase:'CLIENT',status:-1,destination:'NONE'});assert.equal(JSON.stringify(result).includes('private-synthetic-marker'),false);
});
test('untrusted response fields never become diagnostic contents',()=>{
 const result=diagnose({status:Infinity,url:'https://merchant.futurpayment.com/auth/login?secret=private-synthetic-marker',headers:{Authorization:'private-synthetic-marker'},error:{password:'private-synthetic-marker'}},true,'capacitor://localhost');
 assert.equal(result.status,-1);assert.deepEqual(Object.keys(result).sort(),['destination','phase','status']);assert.equal(JSON.stringify(result).includes('private-synthetic-marker'),false);
});
