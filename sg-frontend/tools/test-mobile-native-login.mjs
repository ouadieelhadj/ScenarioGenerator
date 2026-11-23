import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {build}=createRequire(import.meta.url)('esbuild');
const built=await build({entryPoints:[new URL('../src/mobile/services/mobile-native-login.ts',import.meta.url).pathname.replace(/^\/(\w:)/,'$1')],bundle:true,platform:'node',format:'esm',write:false});
const {nativeLoginRequest:login,NativeLoginFailure}=await import('data:text/javascript;base64,'+Buffer.from(built.outputFiles[0].contents).toString('base64'));
const gateway='https://merchant.example.test/api-merchant-portal',endpoint=gateway+'/auth/login';
const credentials={login:'synthetic-user',password:'synthetic-not-a-real-secret'};
const accepted={token:'synthetic-token',login:'synthetic-user',role:'MERCHANT',expiresIn:300};
const ok=data=>({status:200,url:endpoint,data});
test('native login sends one JSON request to the configured gateway with no redirect or old bearer',async()=>{
 let calls=0;const result=await login(gateway,{...credentials,url:'https://foreign.example.invalid'},async options=>{
  calls++;assert.equal(options.url,endpoint);assert.equal(options.disableRedirects,true);assert.equal(options.responseType,'json');
  assert.deepEqual(options.data,credentials);assert.equal(options.headers.Authorization,undefined);assert.equal(options.headers['Content-Type'],'application/json');assert.equal(options.headers['Cache-Control'],'no-store');
  assert.equal(options.connectTimeout,30000);assert.equal(options.readTimeout,30000);return ok({...accepted,ignored:'not relayed'});
 });assert.equal(calls,1);assert.deepEqual(result,accepted);
});
test('serialized JSON is decoded but no extra response fields are relayed',async()=>{
 assert.deepEqual(await login(gateway,credentials,async()=>ok(JSON.stringify({...accepted,privateField:'synthetic-only'}))),accepted);
});
test('bad credentials, denied access, throttling and server errors stay failures without retries',async()=>{
 for(const status of [400,401,403,429,500]){let calls=0;await assert.rejects(login(gateway,credentials,async()=>{calls++;return{status,url:endpoint,data:{password:'never-include-in-an-error'}}}),e=>e instanceof NativeLoginFailure&&e.status===status&&!String(e).includes('never-include'));assert.equal(calls,1);}
});
test('every redirect status remains a failure and is never followed by another request',async()=>{
 for(const status of [301,302,303,307,308]){let calls=0;await assert.rejects(login(gateway,credentials,async options=>{calls++;assert.equal(options.disableRedirects,true);return{status,url:endpoint,data:accepted}}),e=>e.status===status);assert.equal(calls,1);}
});
test('a success reported from a different response URL cannot open a session',async()=>{
 for(const url of ['https://foreign.example.invalid/auth/login',endpoint+'?token=synthetic',endpoint+'/'])await assert.rejects(login(gateway,credentials,async()=>({status:200,url,data:accepted})),e=>e.status===502);
});
test('malformed success bodies do not become authenticated responses',async()=>{
 for(const data of [null,[],1,'not JSON',{}, {...accepted,token:''},{...accepted,role:7},{...accepted,expiresIn:0},{...accepted,expiresIn:'300'}])await assert.rejects(login(gateway,credentials,async()=>ok(data)),e=>e.status===502);
});
test('a native network error stays an error, is not retried and leaks no raw diagnostic body',async()=>{
 let calls=0;await assert.rejects(login(gateway,credentials,async()=>{calls++;throw Error('synthetic-private-diagnostic '+credentials.password)}),e=>e.status===0&&!String(e).includes(credentials.password)&&!String(e).includes('private-diagnostic'));assert.equal(calls,1);
});
test('an insecure or ambiguous packaged gateway is refused before credentials are sent',async()=>{
 for(const base of ['http://merchant.example.test/api-merchant-portal',gateway+'/',gateway+'?x=1',gateway+'#x','https://user:pass@merchant.example.test/api-merchant-portal',gateway+'/../api-merchant-portal']){let calls=0;await assert.rejects(login(base,credentials,async()=>{calls++;return ok(accepted)}),e=>e.status===0);assert.equal(calls,0);}
});
test('invalid native response shapes cannot report success',async()=>{
 for(const response of [null,{}, {status:0},{status:NaN},{status:600}])await assert.rejects(login(gateway,credentials,async()=>response),e=>e.status===502);
});
