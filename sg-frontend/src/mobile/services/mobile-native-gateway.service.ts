import {Injectable,inject} from '@angular/core';
import {HttpInterceptorFn} from '@angular/common/http';
import {Capacitor} from '@capacitor/core';
import {nativeGatewayConfiguration,nativeGatewayRequest} from './mobile-native-gateway';

@Injectable({providedIn:'root'})
export class MobileNativeGatewayService {
  private readonly native=Capacitor.isNativePlatform();
  private gateway:string|null=null;
  async initialize():Promise<void> {
    if(!this.native)return;
    try{
      const response=await fetch(new URL('/assets/merchant-mobile-runtime.json',window.location.href),
        {credentials:'omit',cache:'no-store',redirect:'error'});
      if(!response.ok)throw new Error();this.gateway=nativeGatewayConfiguration(await response.json());
    }catch{throw new Error('Configuration HTTPS mobile absente ou invalide. Contactez votre administrateur.');}
  }
  rewrite(requestUrl:string):string {
    if(!this.native)return requestUrl;
    if(!this.gateway)throw new Error('Configuration HTTPS mobile indisponible.');
    return nativeGatewayRequest(requestUrl,this.gateway);
  }
}
// Auth decides on the declared own-API route first; this last interceptor can
// forward that request only to the validated HTTPS gateway, never an arbitrary URL.
export const mobileNativeGatewayInterceptor:HttpInterceptorFn=(request,next)=>{
  const rewritten=inject(MobileNativeGatewayService).rewrite(request.url);
  return next(rewritten===request.url?request:request.clone({url:rewritten}));
};
