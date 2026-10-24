import { Navigate, useNavigate } from 'react-router';
import { useAuth } from '../auth/authContext.js';
import AuthForm from '../components/AuthForm.jsx';

export default function SignupPage() {
  const { user, signup } = useAuth();
  const navigate = useNavigate();
  if (user) {
    return <Navigate to="/" replace />; // 이미 로그인한 상태로 온 경우 (가입 직후 이동은 아래 onSubmit, AUTH-01 ①)
  }
  return (
    <AuthForm
      title="회원가입"
      submitLabel="회원가입"
      onSubmit={async ({ email, password, rememberMe }) => {
        await signup(email, password, rememberMe);
        // 상태 변경과 이동을 같은 순간에 반영해, 로그인 표시가 먼저 보이고 화면이 늦게 바뀌지 않게 한다.
        navigate('/', { replace: true });
      }}
    />
  );
}
