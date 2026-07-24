import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';

export interface LoginResponse {
  token: string;
  role: string;
  tenantCode: string | null;
}

export interface AuthState {
  token: string | null;
  role: string | null;
  tenantCode: string | null;
  username: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly TOKEN_KEY = 'psg_token';
  private readonly ROLE_KEY = 'psg_role';
  private readonly TENANT_KEY = 'psg_tenant';

  authState = signal<AuthState>({
    token: localStorage.getItem(this.TOKEN_KEY),
    role: localStorage.getItem(this.ROLE_KEY),
    tenantCode: localStorage.getItem(this.TENANT_KEY),
    username: null
  });

  constructor(private http: HttpClient, private router: Router) {}

  login(username: string, password: string) {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password })
      .pipe(
        tap(response => {
          localStorage.setItem(this.TOKEN_KEY, response.token);
          localStorage.setItem(this.ROLE_KEY, response.role);
          if (response.tenantCode) {
            localStorage.setItem(this.TENANT_KEY, response.tenantCode);
          }
          this.authState.set({
            token: response.token,
            role: response.role,
            tenantCode: response.tenantCode,
            username
          });
        })
      );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.ROLE_KEY);
    localStorage.removeItem(this.TENANT_KEY);
    this.authState.set({ token: null, role: null, tenantCode: null, username: null });
    this.router.navigate(['/auth/login']);
  }

  isAuthenticated(): boolean {
    return !!this.authState().token;
  }

  getRole(): string | null {
    return this.authState().role;
  }

  isSuperAdmin(): boolean {
    return this.getRole() === 'SUPER_ADMIN';
  }

  isAdminTenant(): boolean {
    return this.getRole() === 'ADMIN_TENANT';
  }

  getToken(): string | null {
    return this.authState().token;
  }
}
