import https from 'node:https';
import { createHash, createPrivateKey, randomUUID, X509Certificate } from 'node:crypto';
import { SignJWT } from 'jose';

const MAX_BYTES = 1024 * 1024;
const MAX_TIMEOUT = 20_000;
const CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
const REQUEST_HEADERS = new Set(['cookie', 'origin', 'sec-fetch-site', 'sec-fetch-mode', 'sec-fetch-dest', 'sec-fetch-user', 'x-csrf-token', 'x-xsrf-token', 'content-type', 'x-fitcore-bootstrap']);
const RESPONSE_HEADERS = ['content-type', 'content-disposition', 'retry-after'];
const SESSION = '__Host-fitcore_session';
const PREAUTH = '__Host-fitcore_preauth';
const COOKIE_ROUTES = new Map([
  ['/api/browser/bootstrap', new Set([PREAUTH])],
  ['/api/browser/login', new Set([SESSION])],
  ['/api/browser/logout', new Set([SESSION, PREAUTH])],
]);

// Exhaustive method/path pairs from the existing controllers. No wildcard /api/auth
// rule: register/login/verify-email return legacy bearer JWTs and are excluded.
const UUID = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';
const ID = '[1-9][0-9]*';
const route = (method, path) => [method, new RegExp(`^${path}$`)];
const ROUTES = [
  route('GET', '/api/browser/(?:csrf|state)'),
  route('POST', '/api/browser/(?:bootstrap|login|logout|ack)'),
  route('GET', '/api/auth/me'),
  route('POST', '/api/auth/(?:registration/(?:owner|invitation)|verification/request|reset-password|forgot-password)'),
  route('GET', '/api/health(?:/ready)?'),
  route('GET', `/api/(?:users|dashboard|notifications)/${UUID}`),
  route('GET', '/api/gym(?:/invitations)?'),
  route('PATCH', '/api/gym'),
  route('POST', '/api/gym/invitations'),
  route('DELETE', `/api/gym/invitations/${UUID}`),
  route('GET', `/api/trainers(?:/sessions/${UUID})?`),
  route('POST', `/api/trainers/book/${UUID}/${ID}`),
  route('GET', `/api/memberships/(?:plans|user/${UUID}(?:/active)?)`),
  route('POST', `/api/memberships/(?:subscribe|renew|upgrade)/${UUID}/${ID}`),
  route('GET', `/api/nutrition/(?:plans|(?:dashboard|goal|logs)/${UUID})`),
  route('POST', `/api/nutrition/(?:goal|log)/${UUID}`),
  route('GET', `/api/tracking/(?:progress|attendance)/${UUID}`),
  route('POST', `/api/tracking/(?:progress|attendance/check-in)/${UUID}`),
  route('PUT', `/api/tracking/attendance/check-out/${ID}`),
  route('GET', `/api/workouts/(?:exercises|plans|logs/${UUID})`),
  route('POST', `/api/workouts/logs/${UUID}`),
  route('POST', '/api/images/upload'),
  route('DELETE', '/api/images/delete'),
  route('GET', '/api/admin/(?:dashboard|users|attendance)'),
  route('POST', '/api/admin/(?:trainers|membership-plans)'),
  route('PUT', `/api/admin/users/${UUID}`),
  route('DELETE', `/api/admin/users/${UUID}`),
  route('PUT', `/api/admin/(?:trainers|membership-plans)/${ID}`),
  route('DELETE', `/api/admin/(?:trainers|membership-plans)/${ID}`),
  route('GET', `/api/admin/catalog/(?:exercises|workouts|nutrition)(?:/${ID})?`),
  route('POST', '/api/admin/catalog/(?:exercises|workouts|nutrition)'),
  route('PUT', `/api/admin/catalog/(?:exercises|workouts|nutrition)/${ID}`),
  route('POST', `/api/admin/catalog/(?:exercises|workouts|nutrition)/${ID}/archive`),
  route('POST', `/api/admin/catalog/workouts/${ID}/publish`),
];

function origin(value, upstream = false) {
  if (typeof value !== 'string') throw new Error('Invalid BFF origin configuration');
  let parsed;
  try { parsed = new URL(value); } catch { throw new Error('Invalid BFF origin configuration'); }
  const loopback = upstream ? parsed.hostname === '127.0.0.1' : ['127.0.0.1', 'localhost', '[::1]'].includes(parsed.hostname);
  if (parsed.protocol !== 'https:' || !loopback || parsed.origin !== value || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('BFF requires a fixed HTTPS loopback origin');
  }
  return parsed;
}

function configure(options) {
  if (!options || typeof options !== 'object') throw new Error('Explicit BFF configuration is required');
  const upstream = origin(options.upstream, true);
  const publicOrigin = origin(options.publicOrigin);
  let key;
  try {
    key = createPrivateKey(options.privateKeyPem);
    if (key.type !== 'private' || key.asymmetricKeyType !== 'rsa' || key.asymmetricKeyDetails.modulusLength < 2048) throw new Error();
    if (!options.caPem || !new X509Certificate(options.caPem).ca) throw new Error();
  } catch {
    // Do not attach a cause: PEM contents and crypto error details never leave startup.
    throw new Error('Invalid BFF synthetic RSA key or TLS CA configuration');
  }
  const maxBodyBytes = options.maxBodyBytes ?? MAX_BYTES;
  const timeoutMs = options.timeoutMs ?? MAX_TIMEOUT;
  if (!Number.isSafeInteger(maxBodyBytes) || maxBodyBytes <= 0 || maxBodyBytes > MAX_BYTES || !Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || timeoutMs > MAX_TIMEOUT) {
    throw new Error('Invalid BFF request bounds');
  }
  return { upstream, publicOrigin, key, ca: options.caPem, maxBodyBytes, timeoutMs };
}

function securityHeaders(res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Security-Policy', CSP);
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
}
function fail(res, status, error) {
  if (res.destroyed || res.writableEnded) return;
  securityHeaders(res);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ error }));
}
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function peerIp(socket) {
  const address = socket.remoteAddress;
  if (address === '::ffff:127.0.0.1') return '127.0.0.1';
  return address === '127.0.0.1' || address === '::1' ? address : null;
}
function hasControl(value) {
  return [...value].some((character) => character.codePointAt(0) < 32 || character.codePointAt(0) === 127);
}
function pathFromTarget(target) {
  if (typeof target !== 'string' || target.length > 8192 || !target.startsWith('/api/') || (/[\\#\s]/.test(target) || hasControl(target))) return null;
  const path = target.split('?', 1)[0];
  // Never normalize an attack target into a legitimate route. Queries remain raw.
  if (path.includes('%') || path.includes('//')) return null;
  try {
    if (hasControl(decodeURIComponent(target))) return null;
  } catch { return null; }
  return path;
}
function repeatedSensitiveHeader(req) {
  const seen = new Set();
  for (let i = 0; i < req.rawHeaders.length; i += 2) {
    const name = req.rawHeaders[i].toLowerCase();
    if (REQUEST_HEADERS.has(name) || name === 'host') {
      if (seen.has(name)) return true;
      seen.add(name);
    }
  }
  return false;
}
function validCookies(cookies, path, method, status) {
  if (!cookies?.length) return true;
  const names = COOKIE_ROUTES.get(path);
  if (method !== 'POST' || status < 200 || status >= 300 || !names) return false;
  const seen = new Set();
  for (const cookie of cookies) {
    const [pair, ...parts] = cookie.split(';').map((part) => part.trim());
    const equal = pair.indexOf('=');
    const name = pair.slice(0, equal), value = pair.slice(equal + 1);
    if (equal < 1 || !names.has(name) || seen.has(name)) return false;
    seen.add(name);
    const attributes = new Map();
    for (const part of parts) {
      const [attribute, ...rest] = part.split('=');
      const normalized = attribute.toLowerCase();
      if (attributes.has(normalized)) return false;
      attributes.set(normalized, rest.join('='));
    }
    if (attributes.has('domain') || !attributes.has('secure') || !attributes.has('httponly') || attributes.get('path') !== '/' || attributes.get('samesite')?.toLowerCase() !== 'lax') return false;
    if (path === '/api/browser/logout' && name === SESSION) {
      if (value !== '' || attributes.get('max-age') !== '0') return false;
    } else if (!/^[A-Za-z0-9_-]{43}$/.test(value)) return false;
  }
  return true;
}

/** API-only Node HTTPS handler. Keep synthetic PEM material outside Git. */
export function createBff(options) {
  const config = configure(options);
  return function bff(req, res) {
    securityHeaders(res);
    const clientIp = peerIp(req.socket);
    if (!req.socket.encrypted || !clientIp || req.headers.host !== config.publicOrigin.host || repeatedSensitiveHeader(req)) {
      fail(res, 403, 'request_forbidden'); req.resume(); return;
    }
    const path = pathFromTarget(req.url);
    if (!path) { fail(res, 400, 'invalid_target'); req.resume(); return; }
    const matches = ROUTES.filter(([, pattern]) => pattern.test(path));
    if (!matches.length) { fail(res, 404, 'route_not_allowed'); req.resume(); return; }
    if (!matches.some(([method]) => method === req.method)) { fail(res, 405, 'method_not_allowed'); req.resume(); return; }
    const unsafe = req.method !== 'GET';
    if (req.headers['sec-fetch-site'] !== 'same-origin' || (req.headers.origin && req.headers.origin !== config.publicOrigin.origin) || (unsafe && req.headers.origin !== config.publicOrigin.origin)) {
      fail(res, 403, 'request_forbidden'); req.resume(); return;
    }
    if (path === '/api/browser/bootstrap' && (req.headers['x-fitcore-bootstrap'] !== '1' || !/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(req.headers['content-type'] ?? ''))) {
      fail(res, 403, 'bootstrap_forbidden'); req.resume(); return;
    }
    if (req.headers['content-encoding'] || (req.headers['content-length'] && (!/^\d+$/.test(req.headers['content-length']) || Number(req.headers['content-length']) > config.maxBodyBytes))) {
      fail(res, req.headers['content-encoding'] ? 415 : 413, 'invalid_body'); req.resume(); return;
    }
    let completed = false, outgoing, incoming;
    const finishError = (status, error) => {
      if (completed) return;
      completed = true;
      clearTimeout(timer);
      outgoing?.destroy(); incoming?.destroy();
      fail(res, status, error);
    };
    const timer = setTimeout(() => finishError(504, 'gateway_timeout'), config.timeoutMs);
    const stop = () => {
      if (completed) return;
      completed = true; clearTimeout(timer); outgoing?.destroy(); incoming?.destroy();
    };
    res.once('close', stop);
    req.once('aborted', stop);
    req.once('error', () => finishError(400, 'invalid_body'));
    const chunks = []; let size = 0;
    req.on('data', (chunk) => {
      if (completed) return;
      size += chunk.length;
      if (size > config.maxBodyBytes) { finishError(413, 'body_too_large'); req.resume(); }
      else chunks.push(chunk);
    });
    req.once('end', async () => {
      if (completed) return;
      const body = Buffer.concat(chunks);
      try {
        const now = Math.floor(Date.now() / 1000);
        const proof = await new SignJWT({ method: req.method, target: req.url, bodySha256: hash(body), cookieSha256: hash(req.headers.cookie ?? ''), clientIp })
          .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
          .setIssuer('fitcore-local-bff').setAudience('fitcore-browser-spike')
          .setIssuedAt(now).setExpirationTime(now + 30).setJti(randomUUID()).sign(config.key);
        if (completed) return;
        const headers = {};
        for (const name of REQUEST_HEADERS) if (req.headers[name] !== undefined) headers[name] = req.headers[name];
        headers['x-fitcore-gateway'] = proof;
        headers['content-length'] = String(body.length);
        outgoing = https.request(config.upstream, {
          method: req.method, path: req.url, headers, ca: config.ca,
          rejectUnauthorized: true, agent: false,
        }, (upstreamResponse) => {
          incoming = upstreamResponse;
          const status = incoming.statusCode;
          if (status >= 300 && status < 400) { finishError(502, 'upstream_redirect'); return; }
          const cookies = incoming.headers['set-cookie'];
          if (!validCookies(cookies, path, req.method, status)) { finishError(502, 'upstream_cookie_rejected'); return; }
          const responseChunks = []; let responseSize = 0;
          incoming.on('data', (chunk) => {
            if (completed) return;
            responseSize += chunk.length;
            if (responseSize > config.maxBodyBytes) finishError(502, 'upstream_body_too_large');
            else responseChunks.push(chunk);
          });
          incoming.once('error', () => finishError(502, 'upstream_unavailable'));
          incoming.once('aborted', () => finishError(502, 'upstream_unavailable'));
          incoming.once('end', () => {
            if (completed) return;
            completed = true; clearTimeout(timer);
            res.statusCode = status;
            for (const name of RESPONSE_HEADERS) if (incoming.headers[name] !== undefined) res.setHeader(name, incoming.headers[name]);
            if (cookies) res.setHeader('Set-Cookie', cookies);
            res.end(Buffer.concat(responseChunks));
          });
        });
        outgoing.once('upgrade', (_response, socket) => {
          socket.destroy();
          finishError(502, 'upstream_protocol_rejected');
        });
        outgoing.once('error', () => finishError(502, 'upstream_unavailable'));
        outgoing.end(body);
      } catch {
        finishError(502, 'gateway_unavailable');
      }
    });
  };
}
