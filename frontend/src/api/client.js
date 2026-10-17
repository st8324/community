// API 모듈 — fetch를 직접 호출하는 유일한 곳 (02 문서 5.8절, ESLint가 강제).
// CSRF 헤더, 오류 변환, 401 → 연장 → 재시도를 여기서 한 번만 처리한다. 화면은 이 모듈의 함수만 쓴다.
//
// 이 모듈은 토큰을 다루지 않는다. 액세스·리프레시 토큰은 HttpOnly 쿠키라 JS가 읽을 수 없고,
// 브라우저가 같은 출처 요청에 자동으로 실어 보낸다 (SEC-05). JS가 읽는 쿠키는 XSRF-TOKEN 하나뿐이다.

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const REFRESH_PATH = '/api/auth/refresh';
const LOGOUT_PATH = '/api/auth/logout';

// 여기의 401은 "자격증명이 틀림" 또는 "연장 자체가 거절됨"이라 연장으로 풀 수 없다 (02 5.8절).
// /api/auth/me는 일반 요청과 같이 연장한다 — 새로고침 후 로그인 상태 유지에 필요.
const NO_REFRESH_PATHS = new Set([
  '/api/auth/login',
  '/api/auth/signup',
  REFRESH_PATH,
  LOGOUT_PATH,
]);

// 서버의 고정 문장을 쓸 수 없을 때의 문장. 서버가 보낸 비표준 본문은 화면에 옮기지 않는다 (SEC-10).
const UNKNOWN_MESSAGE = '일시적인 오류가 발생했습니다.';
const NETWORK_MESSAGE = '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';

/** 4xx·5xx·네트워크 오류. 화면은 code로 분기하고 message를 그대로 보여 준다. */
export class ApiError extends Error {
  constructor({ status, code, fields = [], message }) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

// ── 로그아웃 알림 ─────────────────────────────────────────────

const sessionExpiredListeners = new Set();

/** 연장이 거절돼 로그아웃 상태가 됐을 때 호출된다. 반환값을 부르면 구독 해제. */
export function onSessionExpired(listener) {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

// ── 공개 함수 ─────────────────────────────────────────────────

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path),
};

async function request(method, path, body) {
  assertApiPath(path);
  const response = await send(method, path, body);
  if (response.status !== 401 || NO_REFRESH_PATHS.has(pathOf(path))) {
    return parse(response);
  }

  const outcome = await refreshOnce();
  if (outcome === 'refreshed') {
    // 다시 보낸 요청이 또 401이어도 더 연장하지 않는다 (T-17: 1회만).
    return parse(await send(method, path, body));
  }
  if (outcome === 'rejected' && method === 'GET') {
    // logout이 남은 무효 액세스 쿠키를 지웠다 → 공개 조회는 비회원으로 볼 수 있다 (T-19).
    return parse(await send(method, path, body));
  }
  if (outcome instanceof ApiError) {
    throw outcome; // 연장 요청의 일시 오류 (네트워크·5xx)
  }
  return parse(response);
}

/** 같은 출처의 /api/ 경로만 허용 (SEC-13). 절대 URL·`//host`·다른 경로는 요청하지 않는다. */
function assertApiPath(path) {
  if (typeof path !== 'string' || !path.startsWith('/api/')) {
    throw new ApiError({
      status: 0,
      code: 'INVALID_PATH',
      message: '잘못된 요청 경로입니다.',
    });
  }
}

function pathOf(path) {
  const end = path.search(/[?#]/);
  return end === -1 ? path : path.slice(0, end);
}

// ── 연장 (AUTH-06, T-17, T-19) ────────────────────────────────

let pendingRefresh = null;

/**
 * 동시에 여러 요청이 401이어도 연장은 한 번만 보낸다. 리프레시 토큰은 1회용이라, 두 번 보내면 두 번째가
 * "재사용"으로 판정돼 묶음 전체가 폐기된다 (01 7장).
 *
 * @returns {'refreshed' | 'rejected' | ApiError} 거절(401)일 때만 logout + 알림. 일시 오류는 ApiError.
 */
function refreshOnce() {
  if (!pendingRefresh) {
    pendingRefresh = refresh().finally(() => {
      pendingRefresh = null;
    });
  }
  return pendingRefresh;
}

async function refresh() {
  let response;
  try {
    response = await send('POST', REFRESH_PATH);
  } catch (error) {
    return error; // 네트워크 오류 — 로그인 상태는 그대로 둔다
  }
  if (response.ok) {
    return 'refreshed';
  }
  if (response.status !== 401) {
    return toApiError(response); // 5xx 등 일시 오류 — logout하면 유효한 로그인이 끝나 버린다
  }

  // 거절: 서버가 리프레시 쿠키만 지웠다. 남은 액세스 쿠키(HttpOnly)는 logout 응답으로 지운다.
  try {
    await send('POST', LOGOUT_PATH);
  } catch {
    // logout 실패는 무시한다. 다음 요청이 다시 401 → 연장 거절 경로로 온다.
  }
  for (const listener of [...sessionExpiredListeners]) {
    listener();
  }
  return 'rejected';
}

// ── 전송 ──────────────────────────────────────────────────────

async function send(method, path, body) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    const token = await csrfToken();
    if (token !== null) {
      headers[CSRF_HEADER] = token; // 없으면 서버가 403 CSRF_INVALID로 거절한다
    }
  }
  try {
    return await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', message: NETWORK_MESSAGE });
  }
}

// ── CSRF (SEC-05) ─────────────────────────────────────────────

let pendingCsrf = null;

/** XSRF-TOKEN 쿠키 값. 없으면 GET /api/csrf로 받는다 (동시 요청은 한 번만). */
async function csrfToken() {
  const existing = readCookie(CSRF_COOKIE);
  if (existing !== null) {
    return existing;
  }
  if (!pendingCsrf) {
    pendingCsrf = fetch('/api/csrf', { method: 'GET', credentials: 'same-origin' })
      .catch(() => null) // 실패하면 헤더 없이 보낸다 → 서버의 403을 그대로 보여 준다
      .finally(() => {
        pendingCsrf = null;
      });
  }
  await pendingCsrf;
  return readCookie(CSRF_COOKIE);
}

function readCookie(name) {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) {
      const value = cookie.slice(prefix.length);
      return value === '' ? null : decodeURIComponent(value);
    }
  }
  return null;
}

// ── 응답 해석 ─────────────────────────────────────────────────

async function parse(response) {
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (text === '') {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError({
      status: response.status,
      code: 'UNKNOWN_ERROR',
      message: UNKNOWN_MESSAGE,
    });
  }
}

/** 표준 오류 형식 {code, message, fields}만 믿는다. 그 밖의 본문은 버리고 고정 문장을 쓴다. */
async function toApiError(response) {
  let body = null;
  try {
    body = await response.json();
  } catch {
    // JSON이 아님 (프록시 HTML 등)
  }
  if (body && typeof body.code === 'string' && typeof body.message === 'string') {
    const fields = Array.isArray(body.fields)
      ? body.fields.filter((field) => typeof field === 'string')
      : [];
    return new ApiError({
      status: response.status,
      code: body.code,
      fields,
      message: body.message,
    });
  }
  return new ApiError({ status: response.status, code: 'UNKNOWN_ERROR', message: UNKNOWN_MESSAGE });
}
