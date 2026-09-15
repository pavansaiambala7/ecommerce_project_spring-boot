// Single entry point for every API call. The backend has a handful of
// behaviours that are easy to get wrong in scattered fetch calls, so they are
// all handled exactly once here.

const ACCESS_TOKEN = { value: null };
const REFRESH_KEY = 'refreshToken';

export function setAccessToken(token) {
  ACCESS_TOKEN.value = token;
}

export function getRefreshToken() {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    // Private browsing and blocked site data both throw here.
    return null;
  }
}

export function setRefreshToken(token) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token);
    else localStorage.removeItem(REFRESH_KEY);
  } catch {
    /* Non-fatal: the session just won't survive a reload. */
  }
}

export class ApiError extends Error {
  constructor(message, status, errors) {
    super(message);
    this.status = status;
    this.errors = errors || null;
  }
}

async function readBody(response) {
  // DELETE /api/products/{id} answers 404 with an empty, non-JSON body, so a
  // blind response.json() would throw a parse error instead of surfacing the
  // real status.
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// Refresh rotates the token: the presented one is revoked the moment it is
// used. Two requests refreshing at once means the second presents a dead token
// and gets a 401, so every caller waits on the same in-flight promise.
let refreshInFlight = null;

async function refreshAccessToken() {
  if (refreshInFlight) return refreshInFlight;

  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  refreshInFlight = (async () => {
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      const body = await readBody(response);
      if (!response.ok || !body?.data) {
        setAccessToken(null);
        setRefreshToken(null);
        return null;
      }
      setAccessToken(body.data.accessToken);
      setRefreshToken(body.data.refreshToken);
      return body.data.accessToken;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

async function send(path, { method = 'GET', body, auth = true, retryOn401 = true, headers: extra } = {}) {
  // Only these four request headers pass CORS preflight. Adding any custom
  // header needs app.cors.allowed-headers extended on the backend first.
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && ACCESS_TOKEN.value) headers.Authorization = `Bearer ${ACCESS_TOKEN.value}`;
  // Only Idempotency-Key is expected here. Any other custom header has to be
  // added to the server's CORS allowed-headers list first or preflight fails.
  if (extra) Object.assign(headers, extra);

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && retryOn401 && auth) {
    const fresh = await refreshAccessToken();
    // The retry must carry the same headers, or an idempotent request would
    // lose its key and execute a second time after a token refresh.
    if (fresh) return send(path, { method, body, auth, retryOn401: false, headers: extra });
  }

  if (response.status === 429) {
    const retryAfter = response.headers.get('Retry-After');
    throw new ApiError(
      `Too many requests. Try again in ${retryAfter || 'a few'} seconds.`,
      429,
    );
  }

  const payload = await readBody(response);

  if (!response.ok) {
    throw new ApiError(
      payload?.message || `Request failed (${response.status})`,
      response.status,
      payload?.errors,
    );
  }

  // `data` is omitted entirely when the backend returns null (logout, cart
  // clear), so undefined here is success, not an error.
  return payload?.data ?? null;
}

export const api = {
  get: (path, opts) => send(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => send(path, { ...opts, method: 'POST', body }),
  put: (path, body, opts) => send(path, { ...opts, method: 'PUT', body }),
  patch: (path, body, opts) => send(path, { ...opts, method: 'PATCH', body }),
  del: (path, opts) => send(path, { ...opts, method: 'DELETE' }),
};

// Spring serializes Page<T> directly, and that shape shifts between Boot
// versions. Everything downstream reads this normalized object instead.
export function toPage(raw) {
  return {
    items: raw?.content ?? [],
    page: raw?.number ?? 0,
    size: raw?.size ?? 0,
    totalItems: raw?.totalElements ?? 0,
    totalPages: raw?.totalPages ?? 0,
    isLast: raw?.last ?? true,
  };
}
