import { Component,inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PersonalAccountFormComponent } from './personal-account-forms.component';
@Component({standalone:true,imports:[PersonalAccountFormComponent],template:`<app-personal-account-form [flow]="flow" loginPath="/onboarding/login" (confirmed)="resetSession()" />`})
export class PersonalAccountPageComponent {
 private readonly route=inject(ActivatedRoute);private readonly auth=inject(AuthService);
 readonly flow:'PASSWORD_RECOVERY'|'EMAIL_CHANGE'=this.route.snapshot.data['flow']==='EMAIL_CHANGE'?'EMAIL_CHANGE':'PASSWORD_RECOVERY';
 resetSession():void{this.auth.logout();}
}
