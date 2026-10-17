// 자리표시 (F-1 테스트 커밋). 구현은 다음 커밋에서 한다 — 테스트가 "모듈 없음"이 아니라 각 단언에서 실패하게 하려는 것.
export class ApiError extends Error {}

const notImplemented = () => Promise.reject(new Error('F-1 구현 전'));

export const api = {
  get: notImplemented,
  post: notImplemented,
  put: notImplemented,
  del: notImplemented,
};

export function onSessionExpired() {
  return () => {};
}
