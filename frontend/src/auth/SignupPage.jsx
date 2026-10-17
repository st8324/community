import { useNavigate } from 'react-router';
import { CredentialsForm } from './CredentialsForm.jsx';
import { useAuth } from './useAuth.js';

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(credentials) {
    await signup(credentials); // 가입 직후 로그인 상태 (AUTH-01 ①)
    navigate('/', { replace: true });
  }

  return (
    <CredentialsForm
      title="회원가입"
      submitLabel="가입하기"
      passwordAutoComplete="new-password"
      passwordHint="비밀번호는 8~64자입니다."
      onSubmit={handleSubmit}
    />
  );
}
