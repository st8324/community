import { useId, useRef, useState } from 'react';
import { toDisplayError } from '../errors.js';

/**
 * 글 작성·수정 공용 폼. 입력값은 다듬지 않고 그대로 onSubmit에 넘긴다 — 검증과 공백 제거는 서버가 한다 (SEC-01).
 * 오류는 서버 message와 fields만 보여 주고, 그 밖의 예외는 고정 문장으로 가린다 (SEC-10).
 */
export default function PostForm({
  initialTitle = '',
  initialContent = '',
  submitLabel,
  onSubmit,
}) {
  const titleId = useId();
  const contentId = useId();
  const [title, setTitle] = useState(initialTitle);
  const [content, setContent] = useState(initialContent);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (sending.current) {
      return;
    }
    sending.current = true;
    setBusy(true);
    setError(null);
    try {
      await onSubmit({ title, content });
      // 성공하면 호출한 쪽이 화면을 옮긴다. 버튼은 그대로 잠가 두어 중복 제출을 막는다.
    } catch (e) {
      setError(toDisplayError(e));
      sending.current = false;
      setBusy(false);
    }
  }

  const invalid = (field) => (error?.fields.includes(field) ? true : undefined);

  return (
    <form onSubmit={handleSubmit} noValidate>
      <p>
        <label htmlFor={titleId}>제목</label>
        <input
          id={titleId}
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          aria-invalid={invalid('title')}
        />
      </p>
      <p>
        <label htmlFor={contentId}>본문</label>
        <textarea
          id={contentId}
          rows={10}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          aria-invalid={invalid('content')}
        />
      </p>
      {error && <p role="alert">{error.message}</p>}
      <p>
        <button type="submit" disabled={busy}>
          {submitLabel}
        </button>
      </p>
    </form>
  );
}
