import type {LoginRequest,LoginResponse} from '../../app/core/models/auth.models';
import {nativeGatewayConfiguration} from './mobile-native-gateway';

export interface NativeLoginOptions {
 url:string;headers:Record<string,string>;data:LoginRequest;
 responseType:'json';disableRedirects:true;connectTimeout:number;readTimeout:number;
}
export interface NativeLoginReply {status:number;url:string;data:unknown}
export type NativeLoginPost=(options:NativeLoginOptions)=>Promise<NativeLoginReply>;
export class NativeLoginFailure extends Error {
 constructor(readonly status:number){super('Connexion native non confirmée.');this.name='NativeLoginFailure';}
}

/** Only the packaged, validated gateway can select the endpoint. No redirect,
 * fallback request, retry, old Bearer, response body or credential is logged.
 * With Capacitor iOS 8.5.1, disableRedirects uses a dedicated URLSession delegate.
 * System TLS validation remains unchanged; a network error is still an error. */
export async function nativeLoginRequest(gateway:string,credentials:LoginRequest,post:NativeLoginPost):Promise<LoginResponse> {
 let endpoint:string;
 try{endpoint=nativeGatewayConfiguration({version:1,gatewayUrl:gateway})+'/auth/login';}
 catch{throw new NativeLoginFailure(0);}
 const options:NativeLoginOptions={url:endpoint,headers:{'Content-Type':'application/json','Accept':'application/json','Cache-Control':'no-store'},
  data:{login:credentials.login,password:credentials.password},responseType:'json',disableRedirects:true,connectTimeout:30000,readTimeout:30000};
 let response:NativeLoginReply;
 try{response=await post(options);}catch{throw new NativeLoginFailure(0);}
 if(!response||!Number.isInteger(response.status)||response.status<100||response.status>599)throw new NativeLoginFailure(502);
 if(response.status!==200)throw new NativeLoginFailure(response.status);
 if(response.url!==endpoint)throw new NativeLoginFailure(502);
 let data:unknown=response.data;
 if(typeof data==='string'){try{data=JSON.parse(data);}catch{throw new NativeLoginFailure(502);}}
 if(!data||typeof data!=='object'||Array.isArray(data))throw new NativeLoginFailure(502);
 const value=data as Record<string,unknown>;
 if(typeof value['token']!=='string'||!value['token']||typeof value['login']!=='string'||typeof value['role']!=='string'
  ||typeof value['expiresIn']!=='number'||!Number.isFinite(value['expiresIn'])||value['expiresIn']<=0)throw new NativeLoginFailure(502);
 return {token:value['token'],login:value['login'],role:value['role'],expiresIn:value['expiresIn']};
}
