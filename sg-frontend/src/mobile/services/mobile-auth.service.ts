import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { registerPlugin } from '@capacitor/core';
import { ENDPOINTS, url } from '../../app/core/config/api.config';
import { CurrentUser, JwtClaims, LoginRequest, LoginResponse } from '../../app/core/models/auth.models';

@Injectable({ providedIn: 'root' })
export class MobileAuthService {
  private readonly http = inject(HttpClient);
  private readonly accessToken = signal<string | null>(null);
  readonly user = signal<CurrentUser | null>(null);
  readonly authenticated = computed(() => this.user() !== null);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(url.orchestrator(ENDPOINTS.auth.login), request)
      .pipe(tap(response => { this.openSession(response.token); void secureSession.set({ value: response.token }).catch(() => undefined); }));
  }

  token(): string | null {
    return this.accessToken();
  }

  logout(): void {
    this.accessToken.set(null);
    this.user.set(null);
    void secureSession.clear().catch(() => undefined);
  }

  async restore(): Promise<boolean> {
    if (this.authenticated()) return true;
    try {
      const result = await secureSession.get();
      if (!result.value) return false;
      this.openSession(result.value);
      return this.authenticated();
    } catch { return false; }
  }

  private openSession(token: string): void {
    const claims = this.decodeClaims(token);
    if (!claims) throw new Error('Jeton identite invalide');
    this.accessToken.set(token);
    this.user.set({ login: claims.sub, role: claims.role, permissions: claims.permissions ?? [] });
  }

  private decodeClaims(token: string): JwtClaims | null {
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as JwtClaims;
    } catch {
      return null;
    }
  }
}

interface SecureSessionPlugin {
  set(options: { value: string }): Promise<void>;
  get(): Promise<{ value: string | null }>;
  clear(): Promise<void>;
}

const secureSession = registerPlugin<SecureSessionPlugin>('SecureSession');
