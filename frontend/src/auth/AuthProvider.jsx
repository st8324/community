import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, onSessionExpired } from '../api/client.js';
import { AuthContext } from './authContext.js';

// 서버가 준 객체에서 화면에 필요한 두 값만 취한다 (이메일 원문·role은 응답에도 없지만 방어적으로 고른다, DATA-05).
function toUser(me) {
  return { id: me.id, displayName: String(me.displayName) };
}

/**
 * 로그인 상태 (02 문서 5.8절). 앱 시작 시 GET /api/auth/me로 확인한다 (필요하면 API 모듈이 연장).
 * 토큰은 HttpOnly 쿠키라 여기서 다루지 않고, 어떤 브라우저 저장소에도 두지 않는다 (SEC-05).
 */
export default function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', user: null });

  useEffect(() => {
    let active = true;
    api.get('/api/auth/me').then(
      (me) => {
        if (active) {
          setState({ status: 'ready', user: toUser(me) });
        }
      },
      () => {
        // 401(비회원)이든 일시 오류든 비회원 화면으로 시작한다. 일시 오류에는 logout을 부르지 않는다 (T-19).
        if (active) {
          setState({ status: 'ready', user: null });
        }
      },
    );
    // 연장이 거절돼 API 모듈이 로그아웃 처리했을 때 (AUTH-06, T-19)
    const unsubscribe = onSessionExpired(() => setState({ status: 'ready', user: null }));
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (email, password, rememberMe) => {
    const me = await api.post('/api/auth/login', { email, password, rememberMe });
    setState({ status: 'ready', user: toUser(me) });
  }, []);

  const signup = useCallback(async (email, password, rememberMe) => {
    const me = await api.post('/api/auth/signup', { email, password, rememberMe });
    setState({ status: 'ready', user: toUser(me) }); // 가입 직후 로그인 상태 (AUTH-01 ①)
  }, []);

  const logout = useCallback(async () => {
    await api.post('/api/auth/logout');
    setState({ status: 'ready', user: null });
  }, []);

  const value = useMemo(
    () => ({ ...state, login, signup, logout }),
    [state, login, signup, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
