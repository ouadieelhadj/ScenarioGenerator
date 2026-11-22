import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const ts = require('typescript');
const source = readFileSync(new URL('../src/mobile/services/mobile-session-security.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 } }).outputText;
const { decodeMobileSession, mobileApiTokenAllowed } = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
const now = 1791100000;
const claims = { sub: 'FP-U000123', role: 'COMMERCIAL', exp: now + 300, acquirer_id: 'TEST_ACQ', permissions: ['ONBOARDING_PROSPECT_CREATE'] };
const encoded = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url');
const token = (payload, header = { alg: 'HS256', typ: 'JWT' }) => encoded(header) + '.' + encoded(payload) + '.syntheticSignature';
test('same personal identifier, permissions and acquirer are preserved', () => {
  const result = decodeMobileSession(token(claims), now);
  assert.equal(result.user.login, 'FP-U000123');
  assert.equal(result.user.acquirerId, 'TEST_ACQ');
  assert.deepEqual(result.user.permissions, ['ONBOARDING_PROSPECT_CREATE']);
});
test('UTF-8 identifiers are decoded rather than corrupted', () => {
  assert.equal(decodeMobileSession(token({ ...claims, sub: 'utilisateur-é' }), now).user.login, 'utilisateur-é');
});
test('token expiry is enforced including its exact boundary', () => {
  assert.equal(decodeMobileSession(token({ ...claims, exp: now }), now), null);
  assert.equal(decodeMobileSession(token({ ...claims, exp: now - 1 }), now), null);
});
test('missing and nonnumeric expiry are rejected', () => {
  for (const exp of [undefined, null, 'later']) assert.equal(decodeMobileSession(token({ ...claims, exp }), now), null);
});
test('a future not-before claim cannot open a session', () => {
  assert.equal(decodeMobileSession(token({ ...claims, nbf: now + 1 }), now), null);
});
test('unsigned alg none cannot open a session', () => {
  assert.equal(decodeMobileSession(token(claims, { alg: 'none' }), now), null);
});
test('malformed JWT and JSON structures are refused', () => {
  for (const value of ['', 'invalid', 'a.b.c', token(null), token([])]) assert.equal(decodeMobileSession(value, now), null);
});
test('subject and role are required, not derived from email', () => {
  for (const invalid of [{ sub: '' }, { sub: null }, { role: '' }, { role: 123 }, { sub: ' spaced ' }])
    assert.equal(decodeMobileSession(token({ ...claims, ...invalid }), now), null);
});
test('invalid authorization claims cannot reach the mobile user model', () => {
  for (const invalid of [{ permissions: 'ADMIN' }, { permissions: [123] }, { acquirer_id: '' }, { member_id: 123 }])
    assert.equal(decodeMobileSession(token({ ...claims, ...invalid }), now), null);
});
test('legacy identities without tenant claims do not invent an acquirer', () => {
  const result = decodeMobileSession(token({ sub: 'legacy', role: 'MERCHANT', exp: now + 1 }), now);
  assert.equal(Object.hasOwn(result.user, 'acquirerId'), false);
  assert.deepEqual(result.user.permissions, []);
});
const allowed = target => mobileApiTokenAllowed(target, 'https://mobile.example.test', 'https://identity.example.test', 'https://onboarding.example.test');
test('identity login and verified identity endpoints may receive their bearer', () => {
  assert.equal(allowed('https://identity.example.test/auth/login'), true);
  assert.equal(allowed('https://identity.example.test/api/onboarding/identity/me'), true);
});
test('onboarding and portal endpoints on the configured origin are allowed', () => {
  assert.equal(allowed('https://onboarding.example.test/api/merchant-onboarding/v1/dossiers/mine'), true);
  assert.equal(allowed('https://onboarding.example.test/api/merchant-portal/v1/acquirers/TEST_ACQ/activity'), true);
});
test('Google and unrelated hosts never receive the bearer', () => {
  assert.equal(allowed('https://maps.googleapis.com/maps/api/place/autocomplete/json'), false);
  assert.equal(allowed('https://onboarding.example.test.evil.test/api/merchant-onboarding/v1/dossiers'), false);
});
test('same-origin static or unrelated application routes are refused', () => {
  assert.equal(allowed('https://onboarding.example.test/images/photo.jpg'), false);
  assert.equal(allowed('https://identity.example.test/api/another-service'), false);
});
test('scheme, port, credentials and path confusion do not bypass the allowlist', () => {
  for (const target of ['http://onboarding.example.test/api/merchant-onboarding/v1/dossiers',
    'https://onboarding.example.test:8443/api/merchant-onboarding/v1/dossiers',
    'https://user@onboarding.example.test/api/merchant-onboarding/v1/dossiers',
    'https://onboarding.example.test/api/merchant-onboarding-fake/v1',
    'https://onboarding.example.test/api/merchant-onboarding/../../private'])
    assert.equal(allowed(target), false);
});
test('relative trusted service bases retain their configured prefix', () => {
  assert.equal(mobileApiTokenAllowed('/onboarding/api/merchant-onboarding/v1/dossiers', 'https://mobile.example.test', '/identity', '/onboarding'), true);
  assert.equal(mobileApiTokenAllowed('/api/merchant-onboarding/v1/dossiers', 'https://mobile.example.test', '/identity', '/onboarding'), false);
});

const ios = target => mobileApiTokenAllowed(target, 'capacitor://localhost/login', '/api-merchant-portal', '/api-merchant-portal');
test('iPhone local gateway receives authentication before its HTTPS rewrite', () => {
  for (const path of ['/auth/login','/api/onboarding/identity/me','/api/merchant-onboarding/v1/dossiers/mine','/api/merchant-portal/v1/acquirers/TEST_ACQ/incidents'])
    assert.equal(ios('/api-merchant-portal'+path),true);
});
test('iPhone null-origin custom schemes cannot leak the bearer to another authority', () => {
  for (const value of ['capacitor://elsewhere/api-merchant-portal/auth/me', 'other://localhost/api-merchant-portal/auth/me',
    'capacitor://localhost:443/api-merchant-portal/auth/me','capacitor://user@localhost/api-merchant-portal/auth/me',
    'https://foreign.invalid/api-merchant-portal/auth/me','//foreign.invalid/api-merchant-portal/auth/me',
    '/assets/merchant-mobile-runtime.json','/api-merchant-portal/api/unrelated','/api-merchant-portal/api/merchant-portal/../../private'])
    assert.equal(ios(value),false,value);
});
test('only the exact iPhone bundled scheme and hostname opt into native local authentication', () => {
  for (const page of ['capacitor://elsewhere/login','other://localhost/login','null','file:///tmp/index.html'])
    assert.equal(mobileApiTokenAllowed('/api-merchant-portal/auth/me',page,'/api-merchant-portal','/api-merchant-portal'),false,page);
});
