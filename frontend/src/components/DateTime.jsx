import { formatDateTime } from '../format.js';

/** 시각 표시. 서버 문자열은 속성·텍스트로만 들어간다 (SEC-08). */
export default function DateTime({ value }) {
  return <time dateTime={value}>{formatDateTime(value)}</time>;
}
