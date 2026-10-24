import { Route, Routes } from 'react-router';
import AuthProvider from './auth/AuthProvider.jsx';
import { useAuth } from './auth/authContext.js';
import Layout from './components/Layout.jsx';
import HomePage from './pages/HomePage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import SignupPage from './pages/SignupPage.jsx';

// 로그인 상태를 알기 전에는 화면을 그리지 않는다 (비회원 화면이 잠깐 보였다 바뀌는 것을 막는다).
function AppRoutes() {
  const { status } = useAuth();
  if (status === 'loading') {
    return <p role="status">불러오는 중…</p>;
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignupPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

// 라우터(BrowserRouter)는 main.jsx에서 감싼다 — 테스트는 MemoryRouter로 감싼다.
export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  );
}
