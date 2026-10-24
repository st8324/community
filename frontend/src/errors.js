import { ApiError } from './api/client.js';

// 서버·API 모듈이 만든 ApiError의 message만 화면에 옮긴다. 그 밖의 예외는 내용을 숨긴다 (SEC-10).
const UNKNOWN_MESSAGE = '일시적인 오류가 발생했습니다.';

/** 화면에 보여 줄 오류 문장과, 검증에 실패한 입력칸 이름 목록. */
export function toDisplayError(error) {
  if (error instanceof ApiError) {
    return { message: error.message, fields: error.fields };
  }
  return { message: UNKNOWN_MESSAGE, fields: [] };
}
