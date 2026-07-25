import { Directive, Input, TemplateRef, ViewContainerRef, inject, effect } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

// Usage : <button *hasPermission="'CAMPAIGN_CREATE'">Creer</button>
//         <div *hasPermission="['TPS_RUN','CAMPAIGN_REPLAY']">...</div>
@Directive({
  selector: '[hasPermission]',
  standalone: true,
})
export class HasPermissionDirective {
  private tpl = inject(TemplateRef<unknown>);
  private vcr = inject(ViewContainerRef);
  private auth = inject(AuthService);

  private required: string[] = [];
  private rendered = false;

  constructor() {
    // Re-evalue quand le user change (login/logout)
    effect(() => {
      this.auth.user();
      this.update();
    });
  }

  @Input() set hasPermission(value: string | string[]) {
    this.required = Array.isArray(value) ? value : [value];
    this.update();
  }

  private update(): void {
    const allowed = this.required.length === 0 || this.auth.hasAnyPermission(this.required);
    if (allowed && !this.rendered) {
      this.vcr.createEmbeddedView(this.tpl);
      this.rendered = true;
    } else if (!allowed && this.rendered) {
      this.vcr.clear();
      this.rendered = false;
    }
  }
}
