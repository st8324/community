// 상단(제목·로그인 상태) + 화면. 표시명은 서버가 마스킹한 값이고 JSX 텍스트로만 넣는다 (SEC-08).
import { useState } from 'react';
import { Link, Outlet, useNavigate } from 'react-router';
import { useAuth } from './auth/useAuth.js';

export function Layout() {
  return (
    <>
      <header className="site-header">
        <h1>
          <Link to="/">커뮤니티</Link>
        </h1>
        <AuthStatus />
      </header>
      <main>
        <Outlet />
      </main>
    </>
  );
}

function AuthStatus() {
  const { status, user, logout } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState(null);

  if (status === 'loading') {
    return null; // 확인 전에는 로그인/로그아웃 어느 쪽도 보이지 않게 (깜빡임 방지)
  }

  async function handleLogout() {
    setError(null);
    try {
      await logout();
      navigate('/');
    } catch (caught) {
      setError(caught);
    }
  }

  if (user === null) {
    return (
      <nav className="auth-status">
        <Link to="/login">로그인</Link>
        <Link to="/signup">회원가입</Link>
      </nav>
    );
  }

  return (
    <nav className="auth-status">
      <span className="display-name">{user.displayName}</span>
      <button type="button" onClick={handleLogout}>
        로그아웃
      </button>
      {error && (
        <p role="alert" className="error">
          {error.message}
        </p>
      )}
    </nav>
  );
}
