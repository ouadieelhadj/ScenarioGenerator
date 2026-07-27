import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-clearing-workspace',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <section class="workspace">
      <header>
        <div>
          <p class="eyebrow">{{ moduleCode() }}</p>
          <h1>{{ titleKey() | translate }}</h1>
        </div>
        <span class="phase">{{ 'clearing.foundationReady' | translate }}</span>
      </header>

      <div class="notice">
        <i class="pi pi-info-circle"></i>
        <div>
          <strong>{{ 'clearing.dynamicContext' | translate }}</strong>
          <p>{{ 'clearing.dynamicContextDetail' | translate }}</p>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .workspace { display: grid; gap: 1rem; }
    header { display:flex; justify-content:space-between; align-items:center; }
    h1 { margin:.25rem 0; color:var(--sg-text-primary); }
    .eyebrow { margin:0; color:var(--sg-text-muted); font-size:.75rem; letter-spacing:.08em; }
    .phase { padding:.4rem .75rem; border-radius:999px; color:var(--sg-color-primary);
      background:color-mix(in srgb, var(--sg-color-primary) 12%, transparent); }
    .notice { display:flex; gap:.75rem; padding:1rem; border:1px solid var(--sg-border);
      border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .notice i { color:var(--sg-color-primary); margin-top:.2rem; }
    .notice p { margin:.35rem 0 0; color:var(--sg-text-secondary); }
  `],
})
export class ClearingWorkspaceComponent {
  private route = inject(ActivatedRoute);
  private params = toSignal(this.route.paramMap.pipe(map(params => ({
    moduleCode: params.get('moduleCode') ?? '',
    screen: params.get('screen') ?? '',
  }))), { initialValue: { moduleCode: '', screen: '' } });

  readonly moduleCode = computed(() => this.params().moduleCode);
  readonly titleKey = computed(() => `clearing.${this.params().screen}`);
}
