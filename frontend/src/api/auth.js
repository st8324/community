// 인증 API (02 문서 5.3절). CSRF·연장·오류 변환은 client.js가 한다.
import { api } from './client.js';

/** 현재 사용자 { id, displayName }. 비로그인이면 ApiError(401). */
export function me() {
  return api.get('/api/auth/me');
}

/** 로그인. 서버가 받는 필드만 골라 보낸다 (그 밖의 필드는 서버도 무시하지만 보내지 않는다, AUTH-01 ④). */
export function login({ email, password, rememberMe = false }) {
  return api.post('/api/auth/login', { email, password, rememberMe });
}

/** 회원가입 — 성공하면 서버가 바로 로그인 쿠키를 준다 (AUTH-01 ①). */
export function signup({ email, password, rememberMe = false }) {
  return api.post('/api/auth/signup', { email, password, rememberMe });
}

/** 로그아웃 — 항상 204 (D-17). 서버가 두 토큰 쿠키를 지우고 리프레시 토큰을 폐기한다. */
export function logout() {
  return api.post('/api/auth/logout');
}
