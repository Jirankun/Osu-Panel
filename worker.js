// MIT License
// Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio

// Osu! Panel — Cloudflare Worker
// Environment: https://api-osupanel.zhyllanfyllah.my.id
//
// This worker holds the osu! Client ID + Secret and issues tokens to the
// app (Flutter / native Android). The app never sees the Client Secret.
//
// ── CONFIGURATION (Worker settings → Variables & Secrets) ──
//   CLIENT_ID     = "65842"            (var)
//   CLIENT_SECRET = "<osu! secret>"    (secret — hidden)
//
// The secret MUST live in the CLIENT_SECRET environment variable — there is
// intentionally NO fallback secret in this file (public repository).
const FALLBACK_CLIENT_ID = '65842';
const FALLBACK_CLIENT_SECRET = '';

const OS_TOKEN_URL = 'https://osu.ppy.sh/oauth/token';

// Redirect URIs allowed to exchange a code (must match exactly the osu!
// callback registered for the application). Defense in depth — osu! also
// validates it.
const FALLBACK_ALLOWED_REDIRECT_URIS = ['osupanel://callback'];

// Rate limit: 30 requests / 60 seconds per IP for every /auth/* endpoint.
const RATE_LIMIT_MAX = 30;
const RATE_LIMIT_WINDOW_MS = 60_000;

// ── CORS (kept: useful when testing from a browser) ──
const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

const TOKEN_CACHE_HEADERS = { 'Cache-Control': 'no-store' };

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, ...extraHeaders, 'Content-Type': 'application/json' },
  });
}

/** Token response — must never be cached by a browser/proxy. */
function jsonToken(data, status = 200) {
  return json(data, status, TOKEN_CACHE_HEADERS);
}

function getClientId(env) {
  return env?.CLIENT_ID || FALLBACK_CLIENT_ID;
}

function getClientSecret(env) {
  return env?.CLIENT_SECRET || FALLBACK_CLIENT_SECRET;
}

function getAllowedRedirectUris(env) {
  const raw = env?.ALLOWED_REDIRECT_URI;
  if (raw) return raw.split(',').map((s) => s.trim()).filter(Boolean);
  return FALLBACK_ALLOWED_REDIRECT_URIS;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ── Fetch an osu! token with retry for transient errors ──
// osu! can answer 429 (rate limit — the CODE IS NOT USED, safe to retry)
// or a brief 5xx. A short retry with backoff + honoring `Retry-After` makes
// one-tap login much more robust. Permanent errors (400/401 invalid_grant,
// etc.) are passed through as-is.
async function fetchOsuToken(params, attempts = 3) {
  let delayMs = 400;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    const resp = await fetch(OS_TOKEN_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: params.toString(),
    });

    // Success, permanent error, or last attempt → return as-is.
    const transient = resp.status === 429 || resp.status >= 500;
    if (resp.ok || !transient || attempt === attempts) return resp;

    const retryAfterSec = Number(resp.headers.get('Retry-After'));
    const waitMs = retryAfterSec > 0 ? retryAfterSec * 1000 : delayMs;
    console.log(
      `osu! token ${resp.status} (attempt ${attempt}/${attempts}) → retry in ${waitMs}ms`,
    );
    await sleep(waitMs);
    delayMs *= 2;
  }
  // Unreachable — the loop always returns on the last attempt.
}

// ── Per-IP rate limiter (in-memory, sliding window) ──
// Note: this Map is per-isolate — enough to curb light abuse.
const rateHits = new Map();

function isRateLimited(request) {
  const ip = request.headers.get('CF-Connecting-IP');
  if (!ip) return false;

  const now = Date.now();
  const cutoff = now - RATE_LIMIT_WINDOW_MS;
  const timestamps = (rateHits.get(ip) || []).filter((t) => t > cutoff);

  if (timestamps.length >= RATE_LIMIT_MAX) {
    rateHits.set(ip, timestamps);
    return true;
  }

  timestamps.push(now);
  rateHits.set(ip, timestamps);
  return false;
}

// ── Read a JSON body safely → null when malformed ──
async function readJsonBody(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

// ── POST /auth/code — Exchange an OAuth code for user tokens ──
async function handleAuthCode(request, env) {
  const body = await readJsonBody(request);
  const code = body?.code;
  const redirectUri = body?.redirect_uri;
  const codeVerifier = body?.code_verifier;

  if (!code || !redirectUri) {
    return json({ error: 'Missing code or redirect_uri' }, 400);
  }

  if (!getAllowedRedirectUris(env).includes(redirectUri)) {
    return json({ error: 'redirect_uri not allowed' }, 400);
  }

  const params = new URLSearchParams();
  params.append('client_id', getClientId(env));
  params.append('client_secret', getClientSecret(env));
  params.append('grant_type', 'authorization_code');
  params.append('code', code);
  params.append('redirect_uri', redirectUri);
  // PKCE (RFC 7636): osu! requires the code_verifier when exchanging a code.
  // Only forwarded when valid (43–128 unreserved chars) — a bad verifier is
  // rejected by osu! anyway, no need to forward it as-is.
  if (typeof codeVerifier === 'string' && codeVerifier.length >= 43 && codeVerifier.length <= 128) {
    params.append('code_verifier', codeVerifier);
  }

  const tokenResp = await fetchOsuToken(params);

  const data = await tokenResp.json().catch(() => null);
  if (!tokenResp.ok) {
    // The osu! status is passed through as-is (400/401 invalid_grant,
    // 429 rate limit, etc.). Logged so it shows up in the dashboard
    // (previously it failed silently — the dashboard looked like "0 errors").
    console.log(
      'auth/code — osu! rejected the code exchange:',
      tokenResp.status,
      JSON.stringify(data),
    );
    return jsonToken(
      { error: 'Failed to exchange code', detail: data },
      tokenResp.status,
    );
  }

  return jsonToken({
    access_token: data.access_token,
    refresh_token: data.refresh_token ?? null,
    expires_in: data.expires_in,
    token_type: data.token_type ?? 'Bearer',
  });
}

// ── POST /auth/refresh — Refresh an access token ──
async function handleAuthRefresh(request, env) {
  const body = await readJsonBody(request);
  const refreshToken = body?.refresh_token;

  if (!refreshToken) {
    return json({ error: 'Missing refresh_token' }, 400);
  }

  const params = new URLSearchParams();
  params.append('client_id', getClientId(env));
  params.append('client_secret', getClientSecret(env));
  params.append('grant_type', 'refresh_token');
  params.append('refresh_token', refreshToken);
  // Note: scope is NOT sent — the refresh token carries the original grant's
  // scope; sending scope can trigger a scope-mismatch error on some servers.

  const tokenResp = await fetchOsuToken(params);

  const data = await tokenResp.json().catch(() => null);
  if (!tokenResp.ok) {
    console.log(
      'auth/refresh — osu! rejected the refresh:',
      tokenResp.status,
      JSON.stringify(data),
    );
    return jsonToken(
      { error: 'Failed to refresh token', detail: data },
      tokenResp.status,
    );
  }

  return jsonToken({
    access_token: data.access_token,
    refresh_token: data.refresh_token ?? refreshToken,
    expires_in: data.expires_in,
    token_type: data.token_type ?? 'Bearer',
  });
}

// ── POST /auth/token — Client credentials token (quick login / identifier) ──
async function handleAuthToken(request, env) {
  const body = await readJsonBody(request);
  const identifier = body?.identifier;

  if (!identifier) {
    return json({ error: 'Missing identifier' }, 400);
  }

  // NOTE: a client credentials token is NOT user-specific; `identifier` is
  // only forwarded so the app knows which user to fetch afterwards.
  const params = new URLSearchParams();
  params.append('client_id', getClientId(env));
  params.append('client_secret', getClientSecret(env));
  params.append('grant_type', 'client_credentials');
  params.append('scope', 'public');

  const tokenResp = await fetchOsuToken(params);

  if (!tokenResp.ok) {
    const errText = await tokenResp.text().catch(() => '');
    console.log(
      'auth/token — osu! rejected client credentials:',
      tokenResp.status,
      errText,
    );
    return jsonToken(
      { error: 'Failed to get osu! token', detail: errText },
      tokenResp.status,
    );
  }

  const tokenData = await tokenResp.json();
  return jsonToken({
    access_token: tokenData.access_token,
    expires_in: tokenData.expires_in,
    token_type: tokenData.token_type,
    identifier: identifier,
  });
}

// ── GET /auth/diag — Diagnostics (troubleshooting only) ──
// Reports which client id the worker actually uses (env vs fallback) plus
// the result of ONE token probe to osu! (no retry), so it is clear whether
// these credentials are accepted from the worker's egress IP.
async function handleDiag(request, env) {
  try {
    const probe = new URLSearchParams();
    probe.append('client_id', getClientId(env));
    probe.append('client_secret', getClientSecret(env));
    probe.append('grant_type', 'client_credentials');
    probe.append('scope', 'public');

    const started = Date.now();
    const resp = await fetch(OS_TOKEN_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: probe.toString(),
    });
    const ms = Date.now() - started;
    const body = (await resp.text().catch(() => ''))
      .replace(/("access_token"\s*:\s*")[^"]+/, '$1MASKED');

    return json({
      clientId: getClientId(env),
      clientIdSource: env?.CLIENT_ID ? 'env' : 'fallback',
      secretSource: env?.CLIENT_SECRET ? 'env' : 'fallback',
      osuProbe: { status: resp.status, ms, body: body.slice(0, 200) },
    });
  } catch (e) {
    return json({ error: 'Diag probe failed', detail: e.message }, 500);
  }
}

// ── Main ──
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    // Normalize trailing slash (/auth/code/ == /auth/code)
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const method = request.method;

    // CORS preflight
    if (method === 'OPTIONS') {
      return new Response(null, { headers: CORS_HEADERS });
    }

    // Rate limit only the auth endpoints (prevent token spam)
    if (path.startsWith('/auth/')) {
      if (isRateLimited(request)) {
        return json({ error: 'Too many requests, try again later' }, 429);
      }
    }

    if (path === '/auth/code' && method === 'POST') {
      return handleAuthCode(request, env).catch((e) =>
        json({ error: 'Internal error', detail: e.message }, 500),
      );
    }

    if (path === '/auth/refresh' && method === 'POST') {
      return handleAuthRefresh(request, env).catch((e) =>
        json({ error: 'Internal error', detail: e.message }, 500),
      );
    }

    if (path === '/auth/token' && method === 'POST') {
      return handleAuthToken(request, env).catch((e) =>
        json({ error: 'Internal error', detail: e.message }, 500),
      );
    }

    if (path === '/auth/diag' && method === 'GET') {
      return handleDiag(request, env);
    }

    // Health check
    if ((path === '/' || path === '/health') && method === 'GET') {
      return json({ status: 'ok', service: 'osu-panel-worker' });
    }

    return json({ error: 'Not found' }, 404);
  },
};
