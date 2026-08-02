import { Component, inject, OnDestroy, OnInit, signal, Type } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { NavigationService } from '../../core/services/navigation.service';
import { loadRegisteredScreen } from '../../core/navigation/screen-registry';

@Component({
  selector: 'app-dynamic-screen-host',
  standalone: true,
  imports: [NgComponentOutlet, TranslatePipe],
  template: `
    @if (loading()) {
      <div class="state"><i class="pi pi-spin pi-spinner"></i> {{ 'common.loading' | translate }}</div>
    } @else if (component()) {
      <ng-container *ngComponentOutlet="component()" />
    } @else {
      <section class="unavailable" role="alert">
        <i class="pi pi-lock"></i>
        <h1>{{ 'dynamicScreen.unavailableTitle' | translate }}</h1>
        <p>{{ 'dynamicScreen.unavailableDetail' | translate }}</p>
      </section>
    }
  `,
  styles: [`
    .state, .unavailable { padding:2rem; text-align:center; color:var(--sg-text-secondary); }
    .unavailable { border:1px solid var(--sg-border); border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .unavailable i { font-size:2rem; color:var(--sg-color-danger); }
    .unavailable h1 { color:var(--sg-text-primary); }
  `],
})
export class DynamicScreenHostComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private navigation = inject(NavigationService);
  private subscription?: Subscription;

  readonly component = signal<Type<unknown> | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.subscription = this.route.paramMap.subscribe(async params => {
      this.loading.set(true);
      this.component.set(null);
      const screen = this.navigation.findScreen(
        params.get('moduleCode') ?? '', params.get('screen') ?? ''
      );
      this.component.set(await loadRegisteredScreen(screen?.componentKey));
      this.loading.set(false);
    });
  }

  ngOnDestroy(): void { this.subscription?.unsubscribe(); }
}
