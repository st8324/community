import { useRef, useState } from 'react';
import { toDisplayError } from '../errors.js';

/**
 * "삭제 → 확인 → 실행" 공용 컨트롤 (POST-05, CMT-03). 바로 지우지 않고 확인을 한 번 묻고, 실행하는 동안 버튼을 잠가 중복 요청을 막는다.
 * 실패하면 서버 message(ApiError)만 보이고, 그 밖의 예외는 고정 문장으로 가린다 (SEC-10).
 *
 * @param question 확인 문구
 * @param onConfirm 실제 삭제 요청. 성공하면 호출한 쪽이 화면을 바꾼다.
 */
export default function ConfirmDelete({ question, onConfirm }) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const sending = useRef(false);

  async function handleConfirm() {
    if (sending.current) {
      return;
    }
    sending.current = true;
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      setConfirming(false);
    } catch (e) {
      setError(toDisplayError(e).message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <>
      {confirming ? (
        <span>
          <span>{question}</span>
          <button type="button" onClick={handleConfirm} disabled={busy}>
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
