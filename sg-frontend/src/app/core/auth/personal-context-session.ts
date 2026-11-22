import type { CurrentUser } from '../models/auth.models';
import { decodeMobileSession } from '../../../mobile/services/mobile-session-security';

export interface PersonalContextSession { token: string; login: string; role: string; acquirerId: string; }

/** UI response binding only; server verifies JWT signatures and live memberships. */
export function validPersonalContext(current: CurrentUser | null, response: PersonalContextSession,
    requestedAcquirer: string): boolean {
  if (!current || !response || !['MERCHANT', 'COMMERCANT'].includes(current.role)) return false;
  const parsed = decodeMobileSession(response.token);
  if (!parsed || !['MERCHANT', 'COMMERCANT'].includes(parsed.user.role) || parsed.user.permissions.length) return false;
  const normalized = (login: string) => /^FP-U\d{6,}$/i.test(login) ? login.toLowerCase() : login;
  return parsed.user.role === current.role && response.role === parsed.user.role
    && normalized(parsed.user.login) === normalized(current.login) && response.login === parsed.user.login
    && parsed.user.acquirerId === requestedAcquirer && response.acquirerId === requestedAcquirer;
}
