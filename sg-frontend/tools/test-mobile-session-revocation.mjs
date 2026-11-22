import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const ts = require('typescript');
const source = readFileSync(new URL('../src/mobile/services/mobile-session-revocation.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 } }).outputText;
const { shouldEndMobileSession } = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
test('server 401 revokes only the currently used authenticated session', () => {
  assert.equal(shouldEndMobileSession(401, true, 'synthetic-one', 'synthetic-one'), true);
});
test('a delayed 401 for an old session cannot log out a new identity', () => {
  assert.equal(shouldEndMobileSession(401, true, 'synthetic-old', 'synthetic-new'), false);
});
test('third-party errors cannot revoke the FuturPayment session', () => {
  assert.equal(shouldEndMobileSession(401, false, 'synthetic-one', 'synthetic-one'), false);
});
test('authorization refusal is not interpreted as session revocation', () => {
  for (const status of [400, 403, 404, 409, 500, 0]) assert.equal(shouldEndMobileSession(status, true, 'synthetic-one', 'synthetic-one'), false);
});
test('a failed unauthenticated login or an already closed session has no revocation effect', () => {
  assert.equal(shouldEndMobileSession(401, true, null, null), false);
  assert.equal(shouldEndMobileSession(401, true, 'synthetic-one', null), false);
});
