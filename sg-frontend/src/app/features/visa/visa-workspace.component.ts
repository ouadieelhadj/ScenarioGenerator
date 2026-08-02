import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { NavigationService } from '../../core/services/navigation.service';

@Component({
  selector: 'app-visa-workspace',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <section class="workspace">
      <header>
        <div>
          <p class="eyebrow">{{ moduleCode() }}</p>
          <h1>{{ labelKey() | translate }}</h1>
        </div>
        <span class="status"><i class="pi pi-shield"></i> {{ 'visaWorkspace.sandbox' | translate }}</span>
      </header>

      <div class="flow" aria-label="Visa processing flow">
        @for (step of flowSteps(); track step) {
          <div class="step">{{ step | translate }}</div>
          @if (!$last) { <i class="pi pi-arrow-right"></i> }
        }
      </div>

      <div class="cards">
        <article>
          <i class="pi pi-list-check"></i>
          <h2>{{ 'visaWorkspace.scope' | translate }}</h2>
          <p>{{ scopeKey() | translate }}</p>
        </article>
        <article>
          <i class="pi pi-lock"></i>
          <h2>{{ 'visaWorkspace.safety' | translate }}</h2>
          <p>{{ 'visaWorkspace.safetyDetail' | translate }}</p>
        </article>
      </div>
    </section>
  `,
  styles: [`
    .workspace { display:grid; gap:1.25rem; }
    header { display:flex; align-items:center; justify-content:space-between; gap:1rem; }
    h1, h2 { margin:0; color:var(--sg-text-primary); }
    h2 { font-size:1rem; }
    .eyebrow { margin:0 0 .25rem; color:var(--sg-text-muted); font-size:.75rem; letter-spacing:.08em; }
    .status { padding:.45rem .7rem; border-radius:999px; background:var(--sg-bg-surface);
      border:1px solid var(--sg-border); color:var(--sg-text-secondary); white-space:nowrap; }
    .flow { display:flex; align-items:center; gap:.65rem; padding:1rem; overflow:auto;
      border:1px solid var(--sg-border); border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .step { padding:.55rem .75rem; border-radius:var(--sg-radius-sm); background:var(--sg-color-primary);
      color:white; font-weight:600; white-space:nowrap; }
    .flow > i { color:var(--sg-text-muted); }
    .cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:1rem; }
    article { padding:1rem; border:1px solid var(--sg-border); border-radius:var(--sg-radius-md);
      background:var(--sg-bg-surface); }
    article > i { color:var(--sg-color-primary); font-size:1.25rem; }
    article h2 { margin-top:.65rem; }
    article p { margin:.5rem 0 0; color:var(--sg-text-secondary); line-height:1.5; }
  `],
})
export class VisaWorkspaceComponent {
  private route = inject(ActivatedRoute);
  private navigation = inject(NavigationService);
  private params = toSignal(this.route.paramMap.pipe(map(params => ({
    moduleCode: params.get('moduleCode') ?? '', screen: params.get('screen') ?? '',
  }))), { initialValue: { moduleCode: '', screen: '' } });

  readonly moduleCode = computed(() => this.params().moduleCode);
  readonly labelKey = computed(() => this.navigation.findScreen(
    this.params().moduleCode, this.params().screen
  )?.labelKey ?? 'dynamicScreen.unavailableTitle');
  readonly isBase2 = computed(() => this.moduleCode().includes('BASE2')
    || this.params().screen.includes('base2'));
  readonly scopeKey = computed(() => this.isBase2()
    ? 'visaWorkspace.base2Scope' : 'visaWorkspace.onlineScope');
  readonly flowSteps = computed(() => this.isBase2()
    ? ['visaWorkspace.flow.authorization', 'visaWorkspace.flow.presentment',
       'visaWorkspace.flow.base2', 'visaWorkspace.flow.ack']
    : ['visaWorkspace.flow.acquiring', 'visaWorkspace.flow.gateway',
       'visaWorkspace.flow.visanet', 'visaWorkspace.flow.response']);
}
