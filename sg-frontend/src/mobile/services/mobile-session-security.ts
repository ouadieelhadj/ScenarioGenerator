import type { CurrentUser } from '../../app/core/models/auth.models';

export interface DecodedMobileSession { user: CurrentUser; expiresAt: number; }

/** UI session parsing only. Signature and authorization remain server checks. */
export function decodeMobileSession(token: string, nowSeconds = Date.now() / 1000): DecodedMobileSession | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3 || parts.some(part => !/^[A-Za-z0-9_-]+$/.test(part))) return null;
    const decode = (part: string): unknown => {
      const padded = part.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(part.length / 4) * 4, '=');
      const binary = atob(padded);
      return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(binary, char => char.charCodeAt(0))));
    };
    const header = decode(parts[0]) as Record<string, unknown>;
    const claims = decode(parts[1]) as Record<string, unknown>;
    if (!header || typeof header !== 'object' || Array.isArray(header)
        || typeof header['alg'] !== 'string' || header['alg'].toLowerCase() === 'none'
        || !claims || typeof claims !== 'object' || Array.isArray(claims)) return null;
    const subject = claims['sub'], role = claims['role'], expires = claims['exp'];
    if (typeof subject !== 'string' || !subject.trim() || subject !== subject.trim()
        || typeof role !== 'string' || !role.trim() || role !== role.trim()
        || typeof expires !== 'number' || !Number.isFinite(expires) || expires <= nowSeconds) return null;
    const nbf = claims['nbf'];
    if (nbf !== undefined && (typeof nbf !== 'number' || !Number.isFinite(nbf) || nbf > nowSeconds)) return null;
    const permissions = claims['permissions'] ?? [];
    if (!Array.isArray(permissions) || permissions.some(value => typeof value !== 'string' || !value.trim())) return null;
    const acquirer = claims['acquirer_id'], member = claims['member_id'];
    if (acquirer !== undefined && (typeof acquirer !== 'string' || !acquirer.trim() || acquirer !== acquirer.trim())) return null;
    if (member !== undefined && (typeof member !== 'string' || !member.trim() || member !== member.trim())) return null;
    return {
      user: { login: subject, role, permissions: [...permissions],
        ...(acquirer === undefined ? {} : { acquirerId: acquirer as string }),
        ...(member === undefined ? {} : { memberId: member as string }) },
      expiresAt: expires,
    };
  } catch { return null; }
}

/** Never disclose the bearer to Google, static resources or unrelated services. */
export function mobileApiTokenAllowed(requestUrl: string, pageOrigin: string, identityBase: string, onboardingBase: string): boolean {
  try {
    const page = new URL(pageOrigin);
    const localIOS = page.protocol === 'capacitor:' && page.hostname === 'localhost' && !page.port && !page.username && !page.password;
    const target = new URL(requestUrl, pageOrigin);
    const bundledIOS = localIOS && target.protocol === 'capacitor:' && target.hostname === 'localhost' && !target.port;
    if ((!['http:', 'https:'].includes(target.protocol) && !bundledIOS) || target.username || target.password) return false;
    for (const [baseValue, paths] of [
      [identityBase, ['/auth/', '/api/onboarding/identity/']],
      [onboardingBase, ['/api/merchant-onboarding/', '/api/merchant-portal/']],
    ] as const) {
      const base = new URL(baseValue || '/', pageOrigin);
      if (base.username || base.password || target.protocol !== base.protocol || target.hostname !== base.hostname || target.port !== base.port) continue;
      const prefix = base.pathname.replace(/\/$/, '');
      if (paths.some(path => target.pathname.startsWith(prefix + path))) return true;
    }
  } catch { /* Invalid/missing configuration never grants an origin. */ }
  return false;
}
