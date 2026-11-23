// Runs the actual Angular MobileAuthService with synthetic SDK/HTTP I/O only.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile,unlink} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const {build}=createRequire(import.meta.url)('esbuild');
const frontend=fileURLToPath(new URL('../',import.meta.url));
const target=new URL('.mobile-auth-native-check-'+process.pid+'.mjs',import.meta.url);
const sdk=`
let setup;
export function configure(value){setup={...value,calls:[],stored:[]};return setup;}
export const Capacitor={getPlatform:()=>setup.platform,isNativePlatform:()=>setup.platform!=='web'};
export const CapacitorHttp={post:async options=>{setup.calls.push(options);if(setup.reject)throw Error('synthetic-private-network-marker');return setup.reply;}};
export function registerPlugin(){return{set:async input=>setup.stored.push(input.value),get:async()=>({value:null}),clear:async()=>undefined};}
`;
const entry=`
import '@angular/compiler';
import assert from 'node:assert/strict';
import {createEnvironmentInjector} from '@angular/core';
import {HttpClient,HttpErrorResponse} from '@angular/common/http';
import {firstValueFrom,of} from 'rxjs';
import {configure} from '@capacitor/core';
import {MobileAuthService} from './src/mobile/services/mobile-auth.service';
import {MobileNativeGatewayService} from './src/mobile/services/mobile-native-gateway.service';
import {CommercialConfirmationHandoffService} from './src/app/core/auth/commercial-confirmation-handoff.service';
import {PersonalAccountHandoffService} from './src/app/core/auth/personal-account-handoff.service';
const gateway='https://merchant.example.test/api-merchant-portal',endpoint=gateway+'/auth/login';
const encode=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
const token=encode({alg:'HS256',typ:'JWT'})+'.'+encode({sub:'synthetic-user',role:'MERCHANT',exp:Date.now()/1000+300,permissions:[],acquirer_id:'SYNTHETIC'})+'.syntheticSignature';
const body={token,login:'synthetic-user',role:'MERCHANT',expiresIn:300};
const credentials={login:'synthetic-user',password:'synthetic-not-a-real-secret'};
let groups=0;
async function scenario(platform,{status=200,reject=false,url=endpoint}={},succeeds=true){
 const io=configure({platform,reply:{status,url,data:body},reject});let web=0;
 const injector=createEnvironmentInjector([MobileAuthService,{provide:HttpClient,useValue:{post:()=>{web++;return of(body)}}},
  {provide:MobileNativeGatewayService,useValue:{configuredHttpsGateway:()=>gateway}},
  {provide:CommercialConfirmationHandoffService,useValue:{clear:()=>undefined}},
  {provide:PersonalAccountHandoffService,useValue:{clear:()=>undefined}}]);
 try{
  const auth=injector.get(MobileAuthService);
  if(succeeds){await firstValueFrom(auth.login(credentials));await new Promise(resolve=>setImmediate(resolve));assert.equal(auth.user().login,'synthetic-user');assert.deepEqual(io.stored,[token]);}
  else{await assert.rejects(firstValueFrom(auth.login(credentials)),error=>error instanceof HttpErrorResponse&&error.status===(reject?0:status===200?502:status)&&error.error===null);assert.equal(auth.user(),null);assert.equal(io.stored.length,0);}
  if(platform==='ios'){assert.equal(io.calls.length,1);assert.equal(web,0);assert.equal(io.calls[0].disableRedirects,true);assert.equal(io.calls[0].headers.Authorization,undefined);}
  else{assert.equal(io.calls.length,0);assert.equal(web,1);}
  groups++;
 }finally{injector.destroy();}
}
await scenario('ios');
await scenario('ios',{status:401},false);
await scenario('ios',{status:429},false);
await scenario('ios',{reject:true},false);
await scenario('ios',{url:'https://foreign.example.invalid/auth/login'},false);
await scenario('android');await scenario('web');
console.log('MOBILE_AUTH_NATIVE_INTEGRATION=7_GROUPS_PASSED; NETWORK_REQUESTS=0; REAL_CREDENTIALS=NO');
`;
try{
 const built=await build({stdin:{contents:entry,resolveDir:frontend,sourcefile:'mobile-auth-native-integration.ts',loader:'ts'},bundle:true,platform:'node',format:'esm',packages:'external',write:false,
  tsconfig:fileURLToPath(new URL('../tsconfig.json',import.meta.url)),plugins:[{name:'synthetic-native-sdk',setup(b){b.onResolve({filter:/^@capacitor\/core$/},()=>({path:'synthetic-native-sdk',namespace:'native-test'}));b.onLoad({filter:/.*/,namespace:'native-test'},()=>({contents:sdk,loader:'js'}));}}]});
 await writeFile(target,built.outputFiles[0].contents);await import(target.href);
}finally{await unlink(target).catch(()=>{});}
