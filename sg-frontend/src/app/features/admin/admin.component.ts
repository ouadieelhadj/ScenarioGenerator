import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { UserService } from '../../core/services/user.service';
import { User, CreateUserRequest, ROLES } from '../../core/models/admin.models';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [FormsModule, TranslatePipe, HasPermissionDirective],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent implements OnInit {
  private service = inject(UserService);

  readonly users = signal<User[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly roles = ROLES;

  readonly showForm = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);

  form: CreateUserRequest = this.emptyForm();

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.findAll().subscribe({
      next: (data) => { this.users.set(data); this.loading.set(false); },
      error: () => { this.error.set('admin.loadError'); this.loading.set(false); },
    });
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form = this.emptyForm();
    this.showForm.set(true);
  }

  openEdit(u: User): void {
    this.editingId.set(u.id!);
    this.form = { login: u.login, email: u.email, role: u.role, password: '' };
    this.showForm.set(true);
  }

  closeForm(): void { this.showForm.set(false); }

  save(): void {
    if (!this.form.login || !this.form.email || !this.form.role) {
      this.error.set('admin.saveError');
      return;
    }
    this.saving.set(true);
    const id = this.editingId();
    // A l'edition, si password vide on ne l'envoie pas
    const payload: CreateUserRequest = { ...this.form };
    if (id && !payload.password) delete payload.password;

    const op = id ? this.service.update(id, payload) : this.service.create(payload);
    op.subscribe({
      next: () => { this.saving.set(false); this.showForm.set(false); this.load(); },
      error: () => { this.error.set('admin.saveError'); this.saving.set(false); },
    });
  }

  toggle(u: User): void {
    this.service.toggle(u.id!).subscribe({
      next: () => this.load(),
      error: () => this.error.set('admin.saveError'),
    });
  }

  private emptyForm(): CreateUserRequest {
    return { login: '', password: '', email: '', role: 'OBSERVATEUR' };
  }
}

