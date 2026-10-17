// 인증 API 함수 (02 문서 5.3절). 요청 모양만 고정한다 — CSRF·연장은 client.test.js가 검증한다.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { login, logout, me, signup } from './auth.js';

let calls;

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=csrf-1; path=/';
  calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path, init = {}) => {
      calls.push({ method: init.method ?? 'GET', path, body: init.body });
      if (path === '/api/auth/logout') {
        return new Response(null, { status: 204 });
      }
      return new Response(JSON.stringify({ id: 7, displayName: 'a***@x.com' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('인증 API', () => {
  it('me는 GET /api/auth/me로 현재 사용자를 받는다', async () => {
    expect(await me()).toEqual({ id: 7, displayName: 'a***@x.com' });
    expect(calls).toEqual([{ method: 'GET', path: '/api/auth/me', body: undefined }]);
  });

  it.each([
    ['login', login, '/api/auth/login'],
    ['signup', signup, '/api/auth/signup'],
  ])('%s는 email·password·rememberMe만 보낸다', async (_name, fn, path) => {
    const user = await fn({
      email: 'a@x.com',
      password: 'pw-12345',
      rememberMe: true,
      role: 'ADMIN',
    });

    expect(user).toEqual({ id: 7, displayName: 'a***@x.com' });
    expect(calls).toHaveLength(1);
    expect(calls[0]).toMatchObject({ method: 'POST', path });
    expect(JSON.parse(calls[0].body)).toEqual({
      email: 'a@x.com',
      password: 'pw-12345',
      rememberMe: true,
    });
  });

  it('rememberMe를 주지 않으면 false로 보낸다', async () => {
    await login({ email: 'a@x.com', password: 'pw-12345' });

    expect(JSON.parse(calls[0].body).rememberMe).toBe(false);
  });

  it('logout은 POST /api/auth/logout을 보낸다', async () => {
    expect(await logout()).toBeNull();
    expect(calls).toEqual([{ method: 'POST', path: '/api/auth/logout', body: undefined }]);
  });
});
