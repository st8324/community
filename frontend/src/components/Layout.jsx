import { useState } from 'react';
import { Link, Outlet } from 'react-router';
import { useAuth } from '../auth/authContext.js';
import { toDisplayError } from '../errors.js';

/** 상단 표시(로그인 상태·로그인/로그아웃)와 화면 자리. 표시명은 JSX 텍스트로만 넣는다 (SEC-08). */
export default function Layout() {
  const { user, logout } = useAuth();
  const [error, setError] = useState(null);

  async function handleLogout() {
    try {
      await logout();
      setError(null);
    } catch (e) {
      // 서버가 쿠키를 지우지 못했을 수 있으므로 로그인 상태를 그대로 두고 알린다.
      setError(toDisplayError(e).message);
    }
  }

  return (
    <>
      <header className="site-header">
        <nav>
          <Link to="/" className="brand">
            커뮤니티
          </Link>
          {user ? (
            <>
              <Link to="/posts/new">글쓰기</Link>
              <span className="user">{user.displayName}</span>
              <button type="button" onClick={handleLogout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to="/login">로그인</Link>
              <Link to="/signup">회원가입</Link>
            </>
          )}
        </nav>
      </header>
      {error && <p role="alert">{error}</p>}
      <main>
        <Outlet />
      </main>
    </>
  );
}
