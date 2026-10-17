// API 모듈(02 문서 5.8절, 03 문서 5.6절 F-1) 테스트.
// 실제 서버 없이 가짜 fetch로 "서버가 이렇게 답하면 모듈이 이렇게 한다"를 고정한다.
// fetch 가짜는 이 폴더(src/api)의 테스트에서만 쓴다 (HD-29, ESLint가 강제 — HD-31).
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const CSRF_COOKIE = 'XSRF-TOKEN';

function setCookie(name, value) {
  document.cookie = `${name}=${value}; path=/`;
}

function clearCookie(name) {
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
}

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function noContent() {
  return new Response(null, { status: 204 });
}

function unauthorized() {
  return json(401, { code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', fields: [] });
}

/**
 * 가짜 서버. routes는 'METHOD /path' → (call) => Response | Promise<Response>.
 * 처리기가 없는 경로는 테스트 작성 실수이므로 바로 실패시킨다.
 */
function fakeServer(routes) {
  const calls = [];
  const fetchMock = vi.fn(async (input, init = {}) => {
    const method = init.method ?? 'GET';
    const path = String(input);
    const call = {
      method,
      path,
      headers: new Headers(init.headers),
      body: init.body,
      credentials: init.credentials,
    };
    calls.push(call);
    const handler = routes[`${method} ${path}`];
    if (!handler) {
      throw new Error(`가짜 서버에 처리기 없음: ${method} ${path}`);
    }
    return handler(call);
  });
  vi.stubGlobal('fetch', fetchMock);
  const callsTo = (method, path) => calls.filter((c) => c.method === method && c.path === path);
  return { calls, callsTo };
}

let client;

beforeEach(async () => {
  // 모듈 안의 상태(진행 중인 연장, 알림 구독자)를 테스트마다 새로 시작한다.
  vi.resetModules();
  client = await import('./client.js');
  setCookie(CSRF_COOKIE, 'csrf-1');
});

afterEach(() => {
  vi.unstubAllGlobals();
  clearCookie(CSRF_COOKIE);
});

describe('요청 기본 규칙', () => {
  it('GET은 같은 출처 쿠키를 보내고 X-XSRF-TOKEN은 붙이지 않는다', async () => {
    const server = fakeServer({ 'GET /api/posts': () => json(200, { items: [] }) });

    const data = await client.api.get('/api/posts');

    expect(data).toEqual({ items: [] });
    const [call] = server.calls;
    expect(call.credentials).toBe('same-origin');
    expect(call.headers.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('상태 변경 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다', async () => {
    const server = fakeServer({ 'POST /api/posts': () => json(201, { id: 1 }) });

    await client.api.post('/api/posts', { title: 't', content: 'c' });

    const [call] = server.calls;
    expect(call.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
    expect(call.headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse(call.body)).toEqual({ title: 't', content: 'c' });
  });

  it('PUT·DELETE에도 X-XSRF-TOKEN을 보낸다', async () => {
    const server = fakeServer({
      'PUT /api/posts/1': () => json(200, { id: 1 }),
      'DELETE /api/posts/1': () => noContent(),
    });

    await client.api.put('/api/posts/1', { title: 't', content: 'c' });
    await client.api.del('/api/posts/1');

    expect(server.calls.map((c) => c.headers.get('X-XSRF-TOKEN'))).toEqual(['csrf-1', 'csrf-1']);
  });

  it('XSRF-TOKEN 쿠키가 없으면 /api/csrf로 먼저 받은 뒤 그 값을 보낸다', async () => {
    clearCookie(CSRF_COOKIE);
    const server = fakeServer({
      'GET /api/csrf': () => {
        setCookie(CSRF_COOKIE, 'fresh'); // 서버의 Set-Cookie 흉내
        return noContent();
      },
      'POST /api/auth/logout': () => noContent(),
    });

    await client.api.post('/api/auth/logout');

    expect(server.calls.map((c) => `${c.method} ${c.path}`)).toEqual([
      'GET /api/csrf',
      'POST /api/auth/logout',
    ]);
    expect(server.calls[1].headers.get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('쿠키가 없는 상태 변경 요청이 동시에 와도 /api/csrf는 한 번만 부른다', async () => {
    clearCookie(CSRF_COOKIE);
    const server = fakeServer({
      'GET /api/csrf': async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        setCookie(CSRF_COOKIE, 'fresh');
        return noContent();
      },
      'POST /api/posts': () => json(201, { id: 1 }),
    });

    await Promise.all([client.api.post('/api/posts', {}), client.api.post('/api/posts', {})]);

    expect(server.callsTo('GET', '/api/csrf')).toHaveLength(1);
  });

  it('204 응답은 null을 돌려준다', async () => {
    fakeServer({ 'DELETE /api/posts/1': () => noContent() });

    expect(await client.api.del('/api/posts/1')).toBeNull();
  });

  it.each([
    'https://evil.example/api/posts',
    '//evil.example/api/posts',
    '/actuator/health',
    'api/posts',
  ])('/api/로 시작하지 않는 경로(%s)는 요청하지 않고 오류를 던진다 (SEC-13)', async (path) => {
    const server = fakeServer({});

    await expect(client.api.get(path)).rejects.toThrow();
    expect(server.calls).toHaveLength(0);
  });
});

describe('오류 → ApiError', () => {
  it('표준 오류 본문을 ApiError { status, code, fields, message }로 바꾼다', async () => {
    fakeServer({
      'POST /api/posts': () =>
        json(400, {
          code: 'VALIDATION_FAILED',
          message: '입력값을 확인해 주세요.',
          fields: ['title'],
        }),
    });

    const error = await client.api.post('/api/posts', { title: '' }).catch((e) => e);

    expect(error).toBeInstanceOf(client.ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      fields: ['title'],
      message: '입력값을 확인해 주세요.',
    });
  });

  it('표준 형식이 아닌 오류 본문은 화면에 옮기지 않고 UNKNOWN_ERROR + 고정 문장으로 바꾼다', async () => {
    const html = '<html><body>502 Bad Gateway nginx/1.2.3 /internal/path</body></html>';
    fakeServer({
      'GET /api/posts': () =>
        new Response(html, { status: 502, headers: { 'Content-Type': 'text/html' } }),
    });

    const error = await client.api.get('/api/posts').catch((e) => e);

    expect(error).toBeInstanceOf(client.ApiError);
    expect(error.status).toBe(502);
    expect(error.code).toBe('UNKNOWN_ERROR');
    expect(error.fields).toEqual([]);
    expect(error.message).not.toContain('nginx');
    expect(error.message.length).toBeGreaterThan(0);
  });

  it('네트워크 오류는 status 0, NETWORK_ERROR로 바꾼다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );

    const error = await client.api.get('/api/posts').catch((e) => e);

    expect(error).toBeInstanceOf(client.ApiError);
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR', fields: [] });
  });
});

describe('401 → 연장 (AUTH-06, T-17)', () => {
  it('401이면 연장을 한 번 하고 원래 요청을 한 번 다시 보낸다', async () => {
    let loggedIn = false;
    const server = fakeServer({
      'GET /api/posts/1': () => (loggedIn ? json(200, { id: 1 }) : unauthorized()),
      'POST /api/auth/refresh': () => {
        loggedIn = true;
        return json(200, { id: 7, displayName: 'a***@x.com' });
      },
    });

    const data = await client.api.get('/api/posts/1');

    expect(data).toEqual({ id: 1 });
    expect(server.calls.map((c) => `${c.method} ${c.path}`)).toEqual([
      'GET /api/posts/1',
      'POST /api/auth/refresh',
      'GET /api/posts/1',
    ]);
    // 연장도 상태 변경 요청이라 CSRF가 필요하다 (AUTH-06 ⑦)
    expect(server.callsTo('POST', '/api/auth/refresh')[0].headers.get('X-XSRF-TOKEN')).toBe(
      'csrf-1',
    );
  });

  it('/api/auth/me의 401도 연장한다 (새로고침 시 로그인 상태 유지)', async () => {
    let loggedIn = false;
    const server = fakeServer({
      'GET /api/auth/me': () =>
        loggedIn ? json(200, { id: 7, displayName: 'a***@x.com' }) : unauthorized(),
      'POST /api/auth/refresh': () => {
        loggedIn = true;
        return json(200, { id: 7, displayName: 'a***@x.com' });
      },
    });

    expect(await client.api.get('/api/auth/me')).toEqual({ id: 7, displayName: 'a***@x.com' });
    expect(server.callsTo('POST', '/api/auth/refresh')).toHaveLength(1);
  });

  it('다시 보낸 요청도 401이면 또 연장하지 않고 401을 던진다', async () => {
    const server = fakeServer({
      'GET /api/posts/1': () => unauthorized(),
      'POST /api/auth/refresh': () => json(200, { id: 7, displayName: 'a***@x.com' }),
    });

    const error = await client.api.get('/api/posts/1').catch((e) => e);

    expect(error).toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(server.callsTo('POST', '/api/auth/refresh')).toHaveLength(1);
    expect(server.callsTo('GET', '/api/posts/1')).toHaveLength(2);
  });

  it.each([
    ['/api/auth/login', 'INVALID_CREDENTIALS'],
    ['/api/auth/signup', 'UNAUTHORIZED'],
    ['/api/auth/refresh', 'UNAUTHORIZED'],
    ['/api/auth/logout', 'UNAUTHORIZED'],
  ])('연장 제외 경로(%s)의 401은 연장하지 않는다', async (path, code) => {
    const server = fakeServer({
      [`POST ${path}`]: () => json(401, { code, message: '거절', fields: [] }),
    });

    const error = await client.api.post(path, {}).catch((e) => e);

    expect(error).toMatchObject({ status: 401, code });
    expect(server.calls).toHaveLength(1);
  });

  it('동시에 401이 세 건 나도 연장 요청은 한 번만 보내고 세 건 모두 다시 보낸다', async () => {
    let loggedIn = false;
    const routes = {
      'POST /api/auth/refresh': async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        loggedIn = true;
        return json(200, { id: 7, displayName: 'a***@x.com' });
      },
    };
    for (const id of [1, 2, 3]) {
      routes[`GET /api/posts/${id}`] = () => (loggedIn ? json(200, { id }) : unauthorized());
    }
    const server = fakeServer(routes);

    const results = await Promise.all([1, 2, 3].map((id) => client.api.get(`/api/posts/${id}`)));

    expect(results).toEqual([{ id: 1 }, { id: 2 }, { id: 3 }]);
    expect(server.callsTo('POST', '/api/auth/refresh')).toHaveLength(1);
  });
});

describe('연장 거절 (T-19)', () => {
  it('연장이 401이면 logout으로 쿠키를 지우고, 로그아웃을 알리고, GET은 비회원으로 한 번 다시 보낸다', async () => {
    let staleCookie = true;
    const onExpired = vi.fn();
    client.onSessionExpired(onExpired);
    const server = fakeServer({
      'GET /api/posts/1': () => (staleCookie ? unauthorized() : json(200, { id: 1 })),
      'POST /api/auth/refresh': () => unauthorized(),
      'POST /api/auth/logout': () => {
        staleCookie = false; // 서버가 액세스 쿠키까지 지운다
        return noContent();
      },
    });

    const data = await client.api.get('/api/posts/1');

    expect(data).toEqual({ id: 1 });
    expect(server.calls.map((c) => `${c.method} ${c.path}`)).toEqual([
      'GET /api/posts/1',
      'POST /api/auth/refresh',
      'POST /api/auth/logout',
      'GET /api/posts/1',
    ]);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('연장이 401이면 GET이 아닌 요청은 다시 보내지 않고 401을 던진다', async () => {
    const onExpired = vi.fn();
    client.onSessionExpired(onExpired);
    const server = fakeServer({
      'POST /api/posts': () => unauthorized(),
      'POST /api/auth/refresh': () => unauthorized(),
      'POST /api/auth/logout': () => noContent(),
    });

    const error = await client.api.post('/api/posts', { title: 't', content: 'c' }).catch((e) => e);

    expect(error).toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(server.callsTo('POST', '/api/posts')).toHaveLength(1);
    expect(server.callsTo('POST', '/api/auth/logout')).toHaveLength(1);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('동시 401에서 연장이 거절돼도 logout과 알림은 한 번씩만', async () => {
    const onExpired = vi.fn();
    client.onSessionExpired(onExpired);
    const routes = {
      'POST /api/auth/refresh': async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        return unauthorized();
      },
      'POST /api/auth/logout': () => noContent(),
    };
    for (const id of [1, 2, 3]) {
      routes[`POST /api/posts/${id}`] = () => unauthorized();
    }
    const server = fakeServer(routes);

    const results = await Promise.allSettled(
      [1, 2, 3].map((id) => client.api.post(`/api/posts/${id}`, {})),
    );

    expect(results.every((r) => r.status === 'rejected' && r.reason.status === 401)).toBe(true);
    expect(server.callsTo('POST', '/api/auth/refresh')).toHaveLength(1);
    expect(server.callsTo('POST', '/api/auth/logout')).toHaveLength(1);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['5xx', () => json(500, { code: 'INTERNAL_ERROR', message: '오류', fields: [] })],
    [
      '네트워크 오류',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ])('연장 요청이 %s이면 logout하지 않고 로그아웃도 알리지 않는다', async (_label, refresh) => {
    const onExpired = vi.fn();
    client.onSessionExpired(onExpired);
    const server = fakeServer({
      'GET /api/posts/1': () => unauthorized(),
      'POST /api/auth/refresh': refresh,
    });

    const error = await client.api.get('/api/posts/1').catch((e) => e);

    expect(error).toBeInstanceOf(client.ApiError);
    expect(server.callsTo('POST', '/api/auth/logout')).toHaveLength(0);
    expect(server.callsTo('GET', '/api/posts/1')).toHaveLength(1);
    expect(onExpired).not.toHaveBeenCalled();
  });

  it('알림 구독은 해제할 수 있다', async () => {
    const onExpired = vi.fn();
    const unsubscribe = client.onSessionExpired(onExpired);
    unsubscribe();
    fakeServer({
      'POST /api/posts': () => unauthorized(),
      'POST /api/auth/refresh': () => unauthorized(),
      'POST /api/auth/logout': () => noContent(),
    });

    await client.api.post('/api/posts', {}).catch(() => {});

    expect(onExpired).not.toHaveBeenCalled();
  });
});
