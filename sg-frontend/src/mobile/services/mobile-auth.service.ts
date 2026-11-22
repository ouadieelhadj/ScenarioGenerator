import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { registerPlugin } from '@capacitor/core';
import { ENDPOINTS, url } from '../../app/core/config/api.config';
import { CurrentUser, LoginRequest, LoginResponse } from '../../app/core/models/auth.models';
import { decodeMobileSession } from './mobile-session-security';
import { PersonalContextSession, validPersonalContext } from '../../app/core/auth/personal-context-session';
import { CommercialConfirmationHandoffService } from '../../app/core/auth/commercial-confirmation-handoff.service';
import { PersonalAccountHandoffService } from '../../app/core/auth/personal-account-handoff.service';

@Injectable({ providedIn: 'root' })
export class MobileAuthService {
  private readonly http = inject(HttpClient);
  private readonly confirmationHandoff=inject(CommercialConfirmationHandoffService);
  private readonly personalAccountHandoff=inject(PersonalAccountHandoffService);
  private readonly accessToken = signal<string | null>(null);
  private expiresAt = 0;
  private storageQueue: Promise<void> = Promise.resolve();
  private sessionVersion = 0;
  readonly user = signal<CurrentUser | null>(null);
  readonly authenticated = computed(() => this.user() !== null);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(url.orchestrator(ENDPOINTS.auth.login), request)
      .pipe(tap(response => { this.openSession(response.token); this.store(() => secureSession.set({ value: response.token })); }));
  }

  token(): string | null {
    if (this.accessToken() && this.expiresAt <= Date.now() / 1000) this.clearSession();
    return this.accessToken();
  }

  logout(): void {
    this.confirmationHandoff.clear();this.clearSession();
  }
  private clearSession():void {
    this.personalAccountHandoff.clear();
    ++this.sessionVersion;
    this.expiresAt = 0;
    this.accessToken.set(null);
    this.user.set(null);
    this.store(() => secureSession.clear());
  }

  acceptPersonalContext(response: PersonalContextSession, requested: string): boolean {
    if (!this.token() || !validPersonalContext(this.user(), response, requested)) return false;
    this.openSession(response.token); this.store(() => secureSession.set({ value: response.token })); return true;
  }

  async restore(): Promise<boolean> {
    if (this.token()) return true;
    const version = this.sessionVersion;
    try {
      await this.storageQueue;
      const result = await secureSession.get();
      if (version !== this.sessionVersion) return this.token() !== null;
      if (!result.value) return false;
      this.openSession(result.value);
      return this.authenticated();
    } catch { this.clearSession(); return false; }
  }

  private openSession(token: string): void {
    const session = decodeMobileSession(token);
    if (!session) { this.clearSession(); throw new Error('Session expirée ou invalide. Reconnectez-vous.'); }
    ++this.sessionVersion;
    this.accessToken.set(token);
    this.expiresAt = session.expiresAt;
    this.user.set(session.user);
  }
  private store(operation: () => Promise<void>): void {
    // Serialize native writes so an old asynchronous set cannot undo a logout.
    this.storageQueue = this.storageQueue.then(operation).catch(() => undefined);
  }
}

interface SecureSessionPlugin {
  set(options: { value: string }): Promise<void>;
  get(): Promise<{ value: string | null }>;
  clear(): Promise<void>;
}

const secureSession = registerPlugin<SecureSessionPlugin>('SecureSession');
