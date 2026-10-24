import { useId, useState } from 'react';
import { toDisplayError } from '../errors.js';

/**
 * 로그인·회원가입이 함께 쓰는 입력 양식. 검증은 서버가 한다 (SEC-01) — 여기서는 입력을 모아 보내고,
 * 서버가 준 message와 fields만 보여 준다.
 */
export default function AuthForm({ title, submitLabel, onSubmit }) {
  const emailId = useId();
  const passwordId = useId();
  const rememberId = useId();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ email, password, rememberMe });
    } catch (e) {
      setError(toDisplayError(e));
      setSubmitting(false);
    }
  }

  const invalid = (field) => (error?.fields.includes(field) ? true : undefined);

  return (
    <section>
      <h1>{title}</h1>
      <form onSubmit={handleSubmit} noValidate>
        <p>
          <label htmlFor={emailId}>이메일</label>
          <input
            id={emailId}
            name="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={invalid('email')}
          />
        </p>
        <p>
          <label htmlFor={passwordId}>비밀번호</label>
          <input
            id={passwordId}
            name="password"
            type="password"
            autoComplete={submitLabel === '회원가입' ? 'new-password' : 'current-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={invalid('password')}
          />
        </p>
        <p>
          <input
            id={rememberId}
            name="rememberMe"
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />
          <label htmlFor={rememberId}>로그인 상태 유지</label>
        </p>
        {error && <p role="alert">{error.message}</p>}
        <button type="submit" disabled={submitting}>
          {submitLabel}
        </button>
      </form>
    </section>
  );
}
