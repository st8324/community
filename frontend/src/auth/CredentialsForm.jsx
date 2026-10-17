// 로그인·회원가입 공통 입력 폼. 오류 문장은 서버의 고정 문장(ApiError.message)을 JSX 텍스트로만 보여 준다 (SEC-08).
import { useId, useState } from 'react';

const FALLBACK_MESSAGE = '일시적인 오류가 발생했습니다.';

export function CredentialsForm({
  title,
  submitLabel,
  passwordAutoComplete,
  passwordHint,
  onSubmit,
}) {
  const id = useId();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ email, password, rememberMe });
      // 성공하면 화면이 바뀌므로(navigate) 여기서 상태를 더 바꾸지 않는다.
    } catch (caught) {
      setError(caught);
      setPassword(''); // 틀린 비밀번호를 화면에 남기지 않는다
      setSubmitting(false);
    }
  }

  const invalid = (field) => (error?.fields?.includes(field) ? true : undefined);

  return (
    <section className="auth-form">
      <h2>{title}</h2>
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor={`${id}-email`}>이메일</label>
        <input
          id={`${id}-email`}
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-invalid={invalid('email')}
          required
        />
        <label htmlFor={`${id}-password`}>비밀번호</label>
        <input
          id={`${id}-password`}
          type="password"
          autoComplete={passwordAutoComplete}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={invalid('password')}
          aria-describedby={passwordHint ? `${id}-hint` : undefined}
          required
        />
        {passwordHint && (
          <p id={`${id}-hint`} className="hint">
            {passwordHint}
          </p>
        )}
        <label className="checkbox">
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />
          로그인 상태 유지
        </label>
        {error && (
          <p role="alert" className="error">
            {error.message || FALLBACK_MESSAGE}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitLabel}
        </button>
      </form>
    </section>
  );
}
