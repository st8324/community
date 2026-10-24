/** 양의 정수 문자열만 글 번호로 인정한다 (앞자리 0·부호·소수점 불가). 아니면 요청하지 않는다 (02 문서 5.8절). */
export function parsePostId(raw) {
  return typeof raw === 'string' && /^[1-9][0-9]{0,18}$/.test(raw) ? raw : null;
}
