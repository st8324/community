import { Navigate } from 'react-router';
import { useAuth } from '../auth/authContext.js';

/**
 * 로그인이 필요한 화면을 감싼다. 비회원이면 자식을 그리지 않고(요청도 보내지 않고) /login으로 보낸다.
 * 화면 차단은 편의일 뿐이고, 실제 인가는 서버가 한다 (SEC-07).
 */
export default function RequireLogin({ children }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return children;
}
