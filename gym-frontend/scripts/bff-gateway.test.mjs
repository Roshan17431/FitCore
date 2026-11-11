import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import https from 'node:https';
import { createHash, createPublicKey, generateKeyPairSync } from 'node:crypto';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { once } from 'node:events';

const module = await import('../bff/gateway.mjs').catch((error) => {
  if (error.code === 'ERR_MODULE_NOT_FOUND') return {};
  throw error;
});
const createBff = module.createBff;
let tempDir, tls, signingKey, publicKey;
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');
const session = '__Host-fitcore_session=' + 'a'.repeat(43) + '; Path=/; Secure; HttpOnly; SameSite=Lax';
const preauth = '__Host-fitcore_preauth=' + 'b'.repeat(43) + '; Path=/; Secure; HttpOnly; SameSite=Lax';
const expired = '__Host-fitcore_session=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax';
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

before(async () => {
  tempDir = await mkdtemp(join(tmpdir(), 'fitcore-bff-contract-'));
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=FitCore synthetic TLS', '-addext', 'subjectAltName=IP:127.0.0.1,DNS:localhost', '-keyout', join(tempDir, 'tls.key'), '-out', join(tempDir, 'tls.crt')], { stdio: 'ignore' });
  tls = { key: await readFile(join(tempDir, 'tls.key')), cert: await readFile(join(tempDir, 'tls.crt')) };
  const pair = generateKeyPairSync('rsa', { modulusLength: 2048 });
  signingKey = pair.privateKey.export({ type: 'pkcs8', format: 'pem' });
  publicKey = createPublicKey(signingKey);
});
after(async () => { await rm(tempDir, { recursive: true, force: true }); });

function api() {
  assert.equal(typeof createBff, 'function', 'createBff must implement the local API gateway contract');
  return createBff;
}
async function listen(server) {
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  return `https://127.0.0.1:${server.address().port}`;
}
async function fixture(t, { respond, options = {} } = {}) {
  api();
  const requests = [];
  const upstream = https.createServer(tls, async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    requests.push({ url: req.url, method: req.method, headers: req.headers, body: Buffer.concat(chunks) });
    if (respond) await respond(req, res, requests.length);
    else res.end('{"ok":true}');
  });
  const upstreamOrigin = await listen(upstream);
  let handler;
  const server = https.createServer(tls, (req, res) => handler(req, res));
  const origin = await listen(server);
  const config = { upstream: upstreamOrigin, publicOrigin: origin, privateKeyPem: signingKey, caPem: tls.cert, ...options };
  handler = createBff(config);
  t.after(async () => {
    server.closeAllConnections(); upstream.closeAllConnections();
    await Promise.all([new Promise((r) => server.close(r)), new Promise((r) => upstream.close(r))]);
  });
  return { requests, origin, config, server };
}
function request(f, path = '/api/auth/me', { method = 'GET', headers = {}, body = '', chunks } = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request(f.origin, { path, method, ca: tls.cert, servername: 'localhost', headers: { 'sec-fetch-site': 'same-origin', origin: f.origin, ...(!chunks && body.length ? { 'content-length': Buffer.byteLength(body) } : {}), ...headers }, agent: false }, (res) => {
      const parts = [];
      res.on('data', (part) => parts.push(part));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(parts).toString() }));
      res.on('error', reject);
    });
    req.on('error', reject);
    if (chunks) for (const chunk of chunks) req.write(chunk);
    req.end(body);
  });
}

test('fails startup on missing, unsafe or malformed configuration without exposing secrets', () => {
  const make = api();
  const good = { upstream: 'https://127.0.0.1:9443', publicOrigin: 'https://127.0.0.1:9444', privateKeyPem: signingKey, caPem: tls.cert };
  for (const changes of [null, {}, { upstream: 'http://127.0.0.1:9443' }, { upstream: 'https://localhost:9443' }, { upstream: 'https://127.0.0.2:9443' }, { upstream: 'https://127.0.0.1:9443/api' }, { upstream: 'https://x:y@127.0.0.1:9443' }, { upstream: 'https://127.0.0.1:9443/?x=1' }, { publicOrigin: 'https://example.com' }, { publicOrigin: 'http://127.0.0.1:9444' }, { publicOrigin: 'https://127.0.0.1:9444/' }, { caPem: 'SECRET-CA-INVALID' }, { privateKeyPem: 'SECRET-KEY-INVALID' }, { privateKeyPem: publicKey.export({ type: 'spki', format: 'pem' }) }, { maxBodyBytes: 1048577 }, { maxBodyBytes: 0 }, { timeoutMs: 20001 }, { timeoutMs: 0 }]) {
    const input = changes === null ? undefined : Object.keys(changes).length ? { ...good, ...changes } : {};
    assert.throws(() => make(input), (error) => !String(error).includes('SECRET-') && !String(error).includes('BEGIN PRIVATE KEY'));
  }
  const weak = generateKeyPairSync('rsa', { modulusLength: 1024 }).privateKey.export({ type: 'pkcs8', format: 'pem' });
  assert.throws(() => make({ ...good, privateKeyPem: weak }));
});

test('forwards raw bytes and raw target with a verified request-bound RS256 proof, stripping untrusted headers', async (t) => {
  const f = await fixture(t);
  const raw = Buffer.from('{ "email": "synthetic@example.test", "password": "test", "gymId": 4 }\n');
  const cookie = '__Host-fitcore_preauth=' + 'z'.repeat(43) + '; sentinel=raw%20value';
  const target = '/api/browser/login?gymId=4&q=a%20b&q=c';
  const response = await request(f, target, { method: 'POST', body: raw, headers: { 'content-type': 'application/json', cookie, 'x-csrf-token': 'synthetic-csrf', authorization: 'Bearer client-jwt', 'x-fitcore-gateway': 'forged', forwarded: 'for=203.0.113.1', 'x-forwarded-for': '203.0.113.2', 'x-forwarded-host': 'evil.test', 'x-real-ip': '203.0.113.3', 'x-internal-tenant': '4', 'sec-fetch-mode': 'cors', 'sec-fetch-dest': 'empty' } });
  assert.equal(response.status, 200);
  const actual = f.requests[0];
  assert.equal(actual.url, target); assert.deepEqual(actual.body, raw);
  assert.equal(actual.headers.cookie, cookie); assert.equal(actual.headers['x-csrf-token'], 'synthetic-csrf');
  assert.equal(actual.headers.origin, f.origin); assert.equal(actual.headers['sec-fetch-mode'], 'cors');
  for (const header of ['authorization', 'forwarded', 'x-forwarded-for', 'x-forwarded-host', 'x-real-ip', 'x-internal-tenant']) assert.equal(actual.headers[header], undefined, header);
  const { jwtVerify } = await import('jose');
  const { payload, protectedHeader } = await jwtVerify(actual.headers['x-fitcore-gateway'], publicKey, { algorithms: ['RS256'], issuer: 'fitcore-local-bff', audience: 'fitcore-browser-spike' });
  assert.equal(protectedHeader.alg, 'RS256'); assert.equal(payload.method, 'POST'); assert.equal(payload.target, target);
  assert.equal(payload.bodySha256, digest(raw)); assert.equal(payload.cookieSha256, digest(cookie));
  assert.equal(payload.clientIp, '127.0.0.1'); assert.match(payload.jti, /^[0-9a-f-]{36}$/);
  assert.ok(payload.exp > payload.iat && payload.exp - payload.iat <= 30);
  assert.ok(Math.abs(payload.iat - Math.floor(Date.now() / 1000)) <= 5);
  assert.equal(payload.gymId, undefined); assert.equal(payload.role, undefined);
  assert.equal(response.headers['cache-control'], 'no-store');
  assert.match(response.headers['content-security-policy'], /default-src 'none'/);
  assert.equal(response.headers['x-fitcore-gateway'], undefined);
  const second = await request(f);
  assert.equal(second.status, 200);
  const next = await jwtVerify(f.requests[1].headers['x-fitcore-gateway'], publicKey);
  assert.notEqual(next.payload.jti, payload.jti);
  assert.equal(next.payload.cookieSha256, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
});

// Each literal is derived from existing controller annotations; broad prefix routing would fail the rejection test below.
const routes = [
  ['POST', '/api/browser/bootstrap'], ['GET', '/api/browser/csrf'], ['POST', '/api/browser/login'], ['POST', '/api/browser/logout'], ['POST', '/api/browser/ack'], ['GET', '/api/browser/state'],
  ['GET', '/api/auth/me'], ['GET', '/api/health'], ['GET', '/api/health/ready'],
  ['POST', '/api/browser/registration/owner'], ['POST', '/api/browser/registration/invitation'], ['POST', '/api/browser/verification/request'], ['POST', '/api/browser/verify-email'], ['POST', '/api/browser/reset-password'], ['POST', '/api/browser/forgot-password'],
  ['GET', '/api/users/11111111-2222-4333-8444-555555555555'], ['GET', '/api/dashboard/11111111-2222-4333-8444-555555555555'], ['GET', '/api/notifications/11111111-2222-4333-8444-555555555555'],
  ['GET', '/api/gym'], ['PATCH', '/api/gym'], ['GET', '/api/gym/invitations'], ['POST', '/api/gym/invitations'], ['DELETE', '/api/gym/invitations/11111111-2222-4333-8444-555555555555'],
  ['GET', '/api/trainers'], ['POST', '/api/trainers/book/11111111-2222-4333-8444-555555555555/2'], ['GET', '/api/trainers/sessions/11111111-2222-4333-8444-555555555555'],
  ['GET', '/api/memberships/plans'], ['POST', '/api/memberships/subscribe/11111111-2222-4333-8444-555555555555/2'], ['GET', '/api/memberships/user/11111111-2222-4333-8444-555555555555'], ['GET', '/api/memberships/user/11111111-2222-4333-8444-555555555555/active'], ['POST', '/api/memberships/renew/11111111-2222-4333-8444-555555555555/2'], ['POST', '/api/memberships/upgrade/11111111-2222-4333-8444-555555555555/2'],
  ['GET', '/api/nutrition/dashboard/11111111-2222-4333-8444-555555555555'], ['GET', '/api/nutrition/plans'], ['POST', '/api/nutrition/goal/11111111-2222-4333-8444-555555555555'], ['GET', '/api/nutrition/goal/11111111-2222-4333-8444-555555555555'], ['POST', '/api/nutrition/log/11111111-2222-4333-8444-555555555555'], ['GET', '/api/nutrition/logs/11111111-2222-4333-8444-555555555555'],
  ['POST', '/api/tracking/progress/11111111-2222-4333-8444-555555555555'], ['GET', '/api/tracking/progress/11111111-2222-4333-8444-555555555555'], ['POST', '/api/tracking/attendance/check-in/11111111-2222-4333-8444-555555555555'], ['PUT', '/api/tracking/attendance/check-out/1'], ['GET', '/api/tracking/attendance/11111111-2222-4333-8444-555555555555'],
  ['GET', '/api/workouts/exercises'], ['GET', '/api/workouts/plans'], ['GET', '/api/workouts/logs/11111111-2222-4333-8444-555555555555'], ['POST', '/api/workouts/logs/11111111-2222-4333-8444-555555555555'], ['POST', '/api/images/upload'], ['DELETE', '/api/images/delete'],
  ['GET', '/api/admin/dashboard'], ['GET', '/api/admin/users'], ['PUT', '/api/admin/users/11111111-2222-4333-8444-555555555555'], ['DELETE', '/api/admin/users/11111111-2222-4333-8444-555555555555'], ['POST', '/api/admin/trainers'], ['PUT', '/api/admin/trainers/1'], ['DELETE', '/api/admin/trainers/1'], ['POST', '/api/admin/membership-plans'], ['PUT', '/api/admin/membership-plans/1'], ['DELETE', '/api/admin/membership-plans/1'], ['GET', '/api/admin/attendance'],
  ['GET', '/api/admin/catalog/exercises'], ['GET', '/api/admin/catalog/exercises/1'], ['POST', '/api/admin/catalog/exercises'], ['PUT', '/api/admin/catalog/exercises/1'], ['POST', '/api/admin/catalog/exercises/1/archive'],
  ['GET', '/api/admin/catalog/workouts'], ['GET', '/api/admin/catalog/workouts/1'], ['POST', '/api/admin/catalog/workouts'], ['PUT', '/api/admin/catalog/workouts/1'], ['POST', '/api/admin/catalog/workouts/1/publish'], ['POST', '/api/admin/catalog/workouts/1/archive'],
  ['GET', '/api/admin/catalog/nutrition'], ['GET', '/api/admin/catalog/nutrition/1'], ['POST', '/api/admin/catalog/nutrition'], ['PUT', '/api/admin/catalog/nutrition/1'], ['POST', '/api/admin/catalog/nutrition/1/archive'],
];
test('allows all current non-JWT controller paths using only their actual HTTP methods', async (t) => {
  const f = await fixture(t);
  for (const [method, path] of routes) {
    const response = await request(f, path, { method, headers: { 'content-type': 'application/json', 'x-fitcore-bootstrap': '1' }, body: method === 'GET' ? '' : '{}' });
    assert.equal(response.status, 200, `${method} ${path}`);
  }
  assert.equal(f.requests.length, routes.length);
});
test('rejects unknown routes, JWT producers, wrong methods, absolute targets and path ambiguity before upstream', async (t) => {
  const f = await fixture(t);
  for (const [method, path] of [['POST', '/api/auth/registration/owner'], ['POST', '/api/auth/registration/invitation'], ['POST', '/api/auth/verification/request'], ['POST', '/api/auth/reset-password'], ['POST', '/api/auth/forgot-password'], ['GET', '/api/browser/verify-email'], ['PUT', '/api/browser/reset-password'], ['POST', '/api/auth/login'], ['POST', '/api/auth/register'], ['POST', '/api/auth/register-owner'], ['POST', '/api/auth/verify-email'], ['GET', '/api/browser/logout'], ['PUT', '/api/browser/login'], ['HEAD', '/api/auth/me'], ['OPTIONS', '/api/auth/me'], ['GET', '/api/admin/secret'], ['GET', '/api/users/1'], ['DELETE', '/api/gym/invitations/1'], ['PUT', '/api/admin/users/1'], ['PUT', '/api/admin/users/11111111-2222-4333-8444-555555555555/role'], ['PUT', '/api/admin/users/11111111-2222-4333-8444-555555555555/status'], ['POST', '/api/gym/unknown'], ['GET', 'https://evil.test/api/auth/me'], ['GET', '//evil.test/api/auth/me'], ['GET', '/api/gym/../auth/me'], ['GET', '/api/auth/%6de'], ['GET', '/api/auth/me/'], ['GET', '/api//auth/me'], ['GET', '/api/auth/me#fragment'], ['GET', '/api/auth/me?x=%zz'], ['GET', '/api/auth/me?x=%0d%0aX-Foo:bad'], ['GET', '/']]) {
    assert.ok([400, 404, 405].includes((await request(f, path, { method })).status), `${method} ${path}`);
  }
  assert.equal(f.requests.length, 0);
});
test('rejects cross-origin, missing Fetch Metadata and invalid bootstrap preconditions before upstream', async (t) => {
  const f = await fixture(t);
  for (const headers of [{ origin: 'https://evil.test' }, { 'sec-fetch-site': 'cross-site' }, { 'sec-fetch-site': '' }, { host: 'evil.test' }]) {
    assert.equal((await request(f, '/api/auth/me', { headers })).status, 403);
  }
  for (const headers of [{}, { 'content-type': 'application/json' }, { 'content-type': 'text/plain', 'x-fitcore-bootstrap': '1' }, { 'content-type': 'application/json', 'x-fitcore-bootstrap': '0' }]) {
    assert.equal((await request(f, '/api/browser/bootstrap', { method: 'POST', headers, body: '{}' })).status, 403);
  }
  assert.equal(f.requests.length, 0);
});
test('preserves exact multi-Set-Cookie only on permitted successful transition endpoints', async (t) => {
  const f = await fixture(t, { respond: (req, res) => {
    res.setHeader('Set-Cookie', req.url.endsWith('logout') ? [expired, preauth] : [req.url.endsWith('bootstrap') ? preauth : session]);
    res.end('{}');
  } });
  for (const [path, expected] of [['bootstrap', [preauth]], ['login', [session]], ['logout', [expired, preauth]]]) {
    const response = await request(f, `/api/browser/${path}`, { method: 'POST', headers: { 'content-type': 'application/json', 'x-fitcore-bootstrap': '1' }, body: '{}' });
    assert.equal(response.status, 200); assert.deepEqual(response.headers['set-cookie'], expected);
  }
});
test('fails closed on cookies from other routes, errors, or malformed cookie scope', async (t) => {
  const cases = [
    ['/api/auth/me', 'GET', 200, session], ['/api/browser/ack', 'POST', 200, session], ['/api/browser/login', 'POST', 401, session],
    ['/api/browser/bootstrap', 'POST', 200, session], ['/api/browser/login', 'POST', 200, 'jwt=forbidden; Path=/; Secure; HttpOnly; SameSite=Lax'],
    ['/api/browser/login', 'POST', 200, session + '; Domain=127.0.0.1'], ['/api/browser/login', 'POST', 200, session.replace('; Secure', '')],
  ];
  for (const [path, method, status, value] of cases) {
    const f = await fixture(t, { respond: (_req, res) => { res.statusCode = status; res.setHeader('Set-Cookie', value); res.end('{}'); } });
    const response = await request(f, path, { method, headers: { 'content-type': 'application/json', 'x-fitcore-bootstrap': '1' } });
    assert.equal(response.status, 502); assert.equal(response.headers['set-cookie'], undefined);
  }
});
test('passes security status, content type and CSV bytes through with no-store and without upstream policy headers', async (t) => {
  const f = await fixture(t, { respond: (_req, res, count) => {
    res.statusCode = count === 1 ? 423 : 200;
    res.setHeader('Content-Type', 'text/csv'); res.setHeader('Content-Disposition', 'attachment; filename="attendance.csv"');
    res.setHeader('Cache-Control', 'public'); res.setHeader('Access-Control-Allow-Origin', '*'); res.setHeader('X-FitCore-Gateway', 'secret');
    res.end('member,visits\r\nsynthetic,3\r\n');
  } });
  const response = await request(f, '/api/admin/attendance');
  assert.equal(response.status, 423); assert.equal(response.body, 'member,visits\r\nsynthetic,3\r\n');
  assert.equal(response.headers['content-type'], 'text/csv'); assert.match(response.headers['content-disposition'], /attendance.csv/);
  assert.equal(response.headers['cache-control'], 'no-store'); assert.equal(response.headers['access-control-allow-origin'], undefined); assert.equal(response.headers['x-fitcore-gateway'], undefined);
});
test('rejects declared and streamed request bodies beyond the configured bound', async (t) => {
  const f = await fixture(t, { options: { maxBodyBytes: 16 } });
  for (const extra of [{ body: 'x'.repeat(17), headers: { 'content-length': '17' } }, { chunks: ['x'.repeat(8), 'y'.repeat(9)] }]) {
    const response = await request(f, '/api/browser/login', { method: 'POST', ...extra });
    assert.equal(response.status, 413); assert.equal(response.headers['cache-control'], 'no-store');
  }
  assert.equal(f.requests.length, 0);
});
test('rejects oversized upstream response without leaking partial bytes or cookies', async (t) => {
  const f = await fixture(t, { options: { maxBodyBytes: 16 }, respond: (_req, res) => { res.write('secret-private-'); res.end('payload-too-long'); } });
  const response = await request(f);
  assert.equal(response.status, 502); assert.ok(!response.body.includes('secret-private'));
});
test('never follows upstream redirects or retries a timed-out transition', async (t) => {
  const redirect = await fixture(t, { respond: (_req, res) => { res.writeHead(307, { Location: 'https://evil.test', 'Set-Cookie': session }); res.end(); } });
  assert.equal((await request(redirect)).status, 502); assert.equal(redirect.requests.length, 1);
  const f = await fixture(t, { options: { timeoutMs: 100 }, respond: async (_req, res) => { await delay(250); res.setHeader('Set-Cookie', session); res.end('{}'); } });
  const response = await request(f, '/api/browser/login', { method: 'POST', body: '{}' });
  assert.equal(response.status, 504); assert.equal(response.headers['set-cookie'], undefined);
  await delay(350); assert.equal(f.requests.length, 1);
});
test('enforces TLS CA verification and returns bounded generic upstream errors', async (t) => {
  const f = await fixture(t, { options: { caPem: tls.cert } });
  const otherDir = join(tempDir, 'untrusted');
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=Untrusted synthetic TLS', '-keyout', otherDir + '.key', '-out', otherDir + '.crt'], { stdio: 'ignore' });
  const other = await readFile(otherDir + '.crt');
  const handler = createBff({ ...f.config, caPem: other });
  f.server.removeAllListeners('request'); f.server.on('request', handler);
  const response = await request(f);
  assert.equal(response.status, 502); assert.equal(f.requests.length, 0);
  assert.deepEqual(JSON.parse(response.body), { error: 'upstream_unavailable' });
  assert.ok(response.body.length < 100);
});
test('explicit-config CLI fails closed without a file and starts only on its configured HTTPS loopback origin', async (t) => {
  api();
  const missing = spawn(process.execPath, ['bff/start-local.mjs'], { cwd: new URL('..', import.meta.url), stdio: ['ignore', 'pipe', 'pipe'] });
  let missingError = ''; missing.stderr.on('data', (chunk) => { missingError += chunk; });
  assert.notEqual((await once(missing, 'exit'))[0], 0); assert.match(missingError, /config/i);
  const f = await fixture(t);
  // Reserve an available port, then release it for the CLI's explicit public origin.
  const reserve = https.createServer(tls); const origin = await listen(reserve); await new Promise((r) => reserve.close(r));
  const configFile = join(tempDir, 'cli-config.json');
  await writeFile(configFile, JSON.stringify({ ...f.config, publicOrigin: origin, caPem: tls.cert.toString(), tlsKeyPem: tls.key.toString(), tlsCertPem: tls.cert.toString() }), { mode: 0o600 });
  const cli = spawn(process.execPath, ['bff/start-local.mjs', '--config', configFile], { cwd: new URL('..', import.meta.url), stdio: ['ignore', 'pipe', 'pipe'] });
  t.after(() => cli.kill('SIGTERM'));
  let output = ''; let errors = '';
  cli.stdout.on('data', (chunk) => { output += chunk; }); cli.stderr.on('data', (chunk) => { errors += chunk; });
  for (let i = 0; i < 50 && !output.includes('listening'); i++) { if (cli.exitCode !== null) break; await delay(20); }
  assert.match(output, /listening/); assert.equal(errors, ''); assert.ok(!output.includes('PRIVATE KEY'));
  assert.equal((await request({ origin })).status, 200);
});

test('rejects upstream protocol upgrades immediately and destroys the upgraded connection', async (t) => {
  const f = await fixture(t, { options: { timeoutMs: 300 }, respond: (_req, res) => {
    res.writeHead(101, { Connection: 'Upgrade', Upgrade: 'websocket' }); res.flushHeaders();
  } });
  const response = await request(f);
  assert.equal(response.status, 502);
  assert.deepEqual(JSON.parse(response.body), { error: 'upstream_protocol_rejected' });
  assert.equal(f.requests.length, 1);
});

test('tokenless account receipts cannot forward any unexpected Set-Cookie', async (t) => {
  const f = await fixture(t, { respond: (_req,res) => { res.statusCode=202;res.setHeader('Set-Cookie',preauth);res.end(); } });
  for(const name of ['registration/owner','registration/invitation','verification/request','verify-email','forgot-password','reset-password']) {
    const r=await request(f,`/api/browser/${name}`,{method:'POST',body:'{}',headers:{'content-type':'application/json'}});
    assert.equal(r.status,502);assert.equal(r.headers['set-cookie'],undefined);
  }
});
