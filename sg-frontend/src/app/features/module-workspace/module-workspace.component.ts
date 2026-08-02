import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { NavigationService } from '../../core/services/navigation.service';

@Component({
  selector: 'app-module-workspace',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <section class="workspace">
      <p class="eyebrow">{{ moduleCode() }}</p>
      <h1>{{ labelKey() | translate }}</h1>
      <div class="notice">
        <i class="pi pi-info-circle"></i>
        <div>
          <strong>{{ 'moduleWorkspace.ready' | translate }}</strong>
          <p>{{ 'moduleWorkspace.detail' | translate }}</p>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .workspace { display:grid; gap:1rem; }
    h1 { margin:0; color:var(--sg-text-primary); }
    .eyebrow { margin:0; color:var(--sg-text-muted); font-size:.75rem; letter-spacing:.08em; }
    .notice { display:flex; gap:.75rem; padding:1rem; border:1px solid var(--sg-border);
      border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .notice i { color:var(--sg-color-primary); margin-top:.2rem; }
    .notice p { margin:.35rem 0 0; color:var(--sg-text-secondary); }
  `],
})
export class ModuleWorkspaceComponent {
  private route = inject(ActivatedRoute);
  private navigation = inject(NavigationService);
  private params = toSignal(this.route.paramMap.pipe(map(params => ({
    moduleCode: params.get('moduleCode') ?? '', screen: params.get('screen') ?? '',
  }))), { initialValue: { moduleCode: '', screen: '' } });

  readonly moduleCode = computed(() => this.params().moduleCode);
  readonly labelKey = computed(() => this.navigation.findScreen(
    this.params().moduleCode, this.params().screen
  )?.labelKey ?? 'dynamicScreen.unavailableTitle');
}
