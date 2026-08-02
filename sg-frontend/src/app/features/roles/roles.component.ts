import { Component, inject, OnInit, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RoleService } from '../../core/services/role.service';
import { RoleSummary } from '../../core/models/role.models';

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="page-header">
      <div>
        <h1><i class="pi pi-shield"></i> {{ 'roles.title' | translate }}</h1>
        <p>{{ 'roles.subtitle' | translate }}</p>
      </div>
    </div>

    @if (error()) {
      <div class="alert-error"><i class="pi pi-exclamation-circle"></i> {{ 'roles.loadError' | translate }}</div>
    }

    <div class="card">
      @if (loading()) {
        <div class="empty"><i class="pi pi-spin pi-spinner"></i> {{ 'common.loading' | translate }}</div>
      } @else if (!roles().length) {
        <div class="empty">{{ 'roles.none' | translate }}</div>
      } @else {
        <table class="data-table">
          <thead><tr><th>{{ 'roles.code' | translate }}</th><th>{{ 'roles.label' | translate }}</th><th>{{ 'roles.permissions' | translate }}</th></tr></thead>
          <tbody>
            @for (role of roles(); track role.id) {
              <tr>
                <td><strong>{{ role.code }}</strong></td>
                <td>{{ role.label }}</td>
                <td class="permissions">
                  @for (permission of role.permissions; track permission.id) {
                    <span>{{ permission.code }}</span>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; margin-bottom:1rem; }
    h1 { margin:0; color:var(--sg-text-primary); } .page-header p { color:var(--sg-text-secondary); }
    .card { padding:1rem; border:1px solid var(--sg-border); border-radius:var(--sg-radius-md); background:var(--sg-bg-surface); }
    .data-table { width:100%; border-collapse:collapse; }
    th, td { padding:.75rem; text-align:left; border-bottom:1px solid var(--sg-border); vertical-align:top; }
    .permissions { display:flex; flex-wrap:wrap; gap:.35rem; }
    .permissions span { padding:.2rem .5rem; border-radius:999px; color:var(--sg-color-primary);
      background:color-mix(in srgb, var(--sg-color-primary) 10%, transparent); font-size:.75rem; }
    .empty { padding:2rem; text-align:center; color:var(--sg-text-secondary); }
    .alert-error { margin-bottom:1rem; padding:.75rem; color:var(--sg-color-danger); }
  `],
})
export class RolesComponent implements OnInit {
  private service = inject(RoleService);
  readonly roles = signal<RoleSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  ngOnInit(): void {
    this.service.findAll().subscribe({
      next: roles => { this.roles.set(roles); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }
}
