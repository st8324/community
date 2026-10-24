import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../api/client.js';
import { toDisplayError } from '../errors.js';

/** 글 삭제 (POST-05). 바로 지우지 않고 확인을 한 번 묻는다. 성공하면 목록으로 이동한다. */
export default function DeleteControl({ postId }) {
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const sending = useRef(false);

  async function handleDelete() {
    if (sending.current) {
      return;
    }
    sending.current = true;
    setBusy(true);
    setError(null);
    try {
      await api.del(`/api/posts/${postId}`);
      navigate('/', { replace: true });
    } catch (e) {
      setError(toDisplayError(e).message);
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <>
      {confirming ? (
        <span>
          <span>이 글을 삭제할까요?</span>
          <button type="button" onClick={handleDelete} disabled={busy}>
            삭제 확인
          </button>
          <button type="button" onClick={() => setConfirming(false)} disabled={busy}>
            취소
          </button>
        </span>
      ) : (
        <button type="button" onClick={() => setConfirming(true)}>
          삭제
        </button>
      )}
      {error && <span role="alert">{error}</span>}
    </>
  );
}
