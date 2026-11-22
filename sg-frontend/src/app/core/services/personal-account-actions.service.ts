import { inject, Injectable } from '@angular/core';
import { HttpClient,HttpContext,HttpContextToken } from '@angular/common/http';
import { Observable } from 'rxjs';
import { url } from '../config/api.config';
export const PERSONAL_ACCOUNT_PUBLIC_REQUEST=new HttpContextToken<boolean>(()=>false);
@Injectable({providedIn:'root'})
export class PersonalAccountActionsService {
 private readonly http=inject(HttpClient);
 private publicOptions(){return {context:new HttpContext().set(PERSONAL_ACCOUNT_PUBLIC_REQUEST,true)};}
 recovery(id:string,body:{acquirerCode:string;personalId:string;email:string}):Observable<{status:string}>{return this.http.post<{status:string}>(url.onboarding('/api/merchant-onboarding/public/v1/account-recoveries'),body,{...this.publicOptions(),headers:{'Idempotency-Key':id}});}
 changeEmail(id:string,email:string,currentPassword:string):Observable<{status:string}>{return this.http.post<{status:string}>(url.onboarding('/api/merchant-onboarding/v1/personal-account/email-change-requests'),{email,currentPassword},{headers:{'Idempotency-Key':id}});}
 complete(id:string,token:string,newPassword?:string):Observable<{status:string;personalId:string}>{return this.http.post<{status:string;personalId:string}>(url.onboarding('/api/merchant-onboarding/public/v1/account-actions/'+encodeURIComponent(id)+'/complete'),newPassword===undefined?{token}:{token,newPassword},this.publicOptions());}
 assist(id:string,code:string,body:{personalId:string;email:string;verificationReference:string;identityVerified:boolean}){return this.http.post<{status:string}>(url.onboarding('/api/merchant-onboarding/v1/acquirers/'+encodeURIComponent(code)+'/personal-email-assistance'),body,{headers:{'Idempotency-Key':id}});}
 assistanceStatus(id:string,code:string){return this.http.get<EmailAssistanceStatus>(url.onboarding('/api/merchant-onboarding/v1/acquirers/'+encodeURIComponent(code)+'/personal-email-assistance/'+encodeURIComponent(id)));}
}
export interface EmailAssistanceStatus {id:string;state:string;mailState:string;personalId:string;verificationReference:string;expiresAt:string|null;}
