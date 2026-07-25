import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { ENDPOINTS, url } from '../config/api.config';
import { LoginRequest, LoginResponse, JwtClaims, CurrentUser } from '../models/auth.models';

const TOKEN_KEY = 'sg-token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  readonly user = signal<CurrentUser | null>(null);
  readonly isAuthenticated = computed(() => this.user() !== null);

  constructor() {
    this.restoreSession();
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    const loginUrl = url.orchestrator(ENDPOINTS.auth.login);
    return this.http
      .post<LoginResponse>(loginUrl, credentials)
      .pipe(tap(res => this.handleLogin(res.token)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.user.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  hasPermission(permission: string): boolean {
    return this.user()?.permissions.includes(permission) ?? false;
  }

  hasAnyPermission(permissions: string[]): boolean {
    const perms = this.user()?.permissions ?? [];
    return permissions.some(p => perms.includes(p));
  }

  hasRole(role: string): boolean {
    return this.user()?.role === role;
  }

  private handleLogin(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.user.set(this.decodeUser(token));
  }

  private restoreSession(): void {
    const token = this.getToken();
    if (token && !this.isExpired(token)) {
      this.user.set(this.decodeUser(token));
    } else if (token) {
      this.logout();
    }
  }

  private decodeClaims(token: string): JwtClaims | null {
    try {
      const payload = token.split('.')[1];
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json) as JwtClaims;
    } catch {
      return null;
    }
  }

  private decodeUser(token: string): CurrentUser | null {
    const claims = this.decodeClaims(token);
    if (!claims) return null;
    return {
      login: claims.sub,
      role: claims.role,
      permissions: claims.permissions ?? [],
    };
  }

  private isExpired(token: string): boolean {
    const claims = this.decodeClaims(token);
    if (!claims?.exp) return true;
    return Date.now() >= claims.exp * 1000;
  }
}
