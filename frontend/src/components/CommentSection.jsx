import { useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client.js';
import { useAuth } from '../auth/authContext.js';
import { toDisplayError } from '../errors.js';
import { useApiGet } from '../hooks/useApiGet.js';
import { isCommentList } from '../shapes.js';
import ConfirmDelete from './ConfirmDelete.jsx';
import DateTime from './DateTime.jsx';

/**
 * 댓글 영역 (CMT-01~03, 02 문서 5.8절). 목록은 글과 따로 불러와, 댓글 오류가 글 보기를 막지 않게 한다.
 * 내용·표시명은 JSX 텍스트로만 넣는다 (SEC-08). 삭제 버튼은 서버가 계산한 canDelete가 true일 때만 보이며,
 * 이는 표시용 힌트일 뿐 실제 허용은 요청 때 서버가 다시 확인한다 (SEC-07).
 */
export default function CommentSection({ postId }) {
  const { user } = useAuth();
  const [reload, setReload] = useState(0);
  const { loading, data, error } = useApiGet(`/api/posts/${postId}/comments`, reload);
  const refresh = () => setReload((n) => n + 1);

  let list;
  if (loading) {
    list = <p role="status">댓글을 불러오는 중…</p>;
  } else if (error || !isCommentList(data)) {
    list = <p role="alert">{toDisplayError(error).message}</p>;
  } else if (data.items.length === 0) {
    list = <p>아직 댓글이 없습니다.</p>;
  } else {
    list = (
      <ul className="comment-list">
        {data.items.map((c) => (
          <li key={c.id}>
            <p className="meta">
              <span>{c.authorName}</span> <DateTime value={c.createdAt} />
            </p>
            <p className="post-content">{c.content}</p>
            {c.canDelete && (
              <p className="actions">
                <ConfirmDelete
                  question="이 댓글을 삭제할까요?"
                  onConfirm={async () => {
                    await api.del(`/api/comments/${c.id}`);
                    refresh();
                  }}
                />
              </p>
            )}
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section className="comments">
      <h2>댓글</h2>
      {list}
      {user ? (
        <CommentForm postId={postId} onCreated={refresh} />
      ) : (
        <p>
          <Link to="/login">로그인하면 댓글을 쓸 수 있어요</Link>
        </p>
      )}
    </section>
  );
}

/** 입력은 다듬지 않고 그대로 보낸다 — 검증과 공백 제거는 서버가 한다 (SEC-01). */
function CommentForm({ postId, onCreated }) {
  const inputId = useId();
  const [content, setContent] = useState('');
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
      await api.post(`/api/posts/${postId}/comments`, { content });
      setContent('');
      onCreated();
    } catch (e) {
      setError(toDisplayError(e));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <p>
        <label htmlFor={inputId}>댓글 내용</label>
        <textarea
          id={inputId}
          rows={3}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          aria-invalid={error?.fields.includes('content') ? true : undefined}
        />
      </p>
      {error && <p role="alert">{error.message}</p>}
      <p>
        <button type="submit" disabled={busy}>
          댓글 등록
        </button>
      </p>
    </form>
  );
}
