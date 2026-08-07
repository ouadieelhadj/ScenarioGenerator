import { spawn } from 'node:child_process';

const localPort = Number(process.env['SG_FRONTEND_TEST_PORT'] ?? 4217);
const localUrl = `http://127.0.0.1:${localPort}`;
const externalUrl = process.env['E2E_BASE_URL'];
let server;

async function waitUntilReady(url) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch { /* serveur en démarrage */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`Le frontend de test ne répond pas sur ${url}`);
}

function runPlaywright(baseUrl) {
  const cli = './node_modules/@playwright/test/cli.js';
  const args = [cli, 'test', ...process.argv.slice(2)];
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, args, {
      stdio: 'inherit',
      env: { ...process.env, E2E_BASE_URL: baseUrl },
    });
    child.once('error', reject);
    child.once('exit', code => resolve(code ?? 1));
  });
}

try {
  const baseUrl = externalUrl ?? localUrl;
  if (!externalUrl) {
    server = spawn(process.execPath, ['./tools/serve-dist.mjs'], { stdio: 'ignore' });
    await waitUntilReady(baseUrl);
  }
  process.exitCode = await runPlaywright(baseUrl);
} finally {
  if (server && !server.killed) server.kill('SIGTERM');
}
