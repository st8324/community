const formatter = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });

/** 서버의 UTC ISO-8601 시각을 사용자 현지 시각 문장으로. 해석할 수 없으면 빈 문자열. */
export function formatDateTime(iso) {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : formatter.format(date);
}
