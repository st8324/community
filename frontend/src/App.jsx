// 화면과 경로 (02 문서 5.8절). 라우터(BrowserRouter)는 main.jsx가 감싼다 — 테스트는 MemoryRouter로 감싼다.
import { Route, Routes } from 'react-router';
import { AuthProvider } from './auth/AuthProvider.jsx';
import { LoginPage } from './auth/LoginPage.jsx';
import { SignupPage } from './auth/SignupPage.jsx';
import { Layout } from './Layout.jsx';
import { NotFoundPage } from './NotFoundPage.jsx';

// 게시글 목록은 F-3에서 이 자리에 붙인다.
function HomePage() {
  return (
    <section>
      <h2>게시글</h2>
    </section>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="signup" element={<SignupPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
