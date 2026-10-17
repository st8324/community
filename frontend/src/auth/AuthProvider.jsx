// 로그인 상태 (02 문서 5.8절). 토큰은 HttpOnly 쿠키라 JS가 모른다 — 여기는 "화면에 무엇을 보일지"만 안다.
// 서버가 매 요청을 다시 검증하므로 이 상태는 보안 장치가 아니다 (SEC-07).
import { useCallback, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth.js';
import { onSessionExpired } from '../api/client.js';
import { AuthContext } from './authContext.js';

export function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', user: null });

  useEffect(() => {
    let active = true;
    // 필요하면 client.js가 연장까지 한다. 실패(비로그인·연장 거절·네트워크)는 모두 "로그인 안 됨"으로 보여 준다.
    authApi
      .me()
      .then((user) => active && setState({ status: 'ready', user }))
      .catch(() => active && setState({ status: 'ready', user: null }));
    const unsubscribe = onSessionExpired(() => setState({ status: 'ready', user: null }));
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (credentials) => {
    const user = await authApi.login(credentials);
    setState({ status: 'ready', user });
  }, []);

  const signup = useCallback(async (credentials) => {
    const user = await authApi.signup(credentials);
    setState({ status: 'ready', user });
  }, []);

  // 요청이 실패하면 쿠키가 남아 있으므로 로그아웃된 것처럼 보이지 않게 상태를 그대로 둔다 (공용 PC 오인 방지).
  const logout = useCallback(async () => {
    await authApi.logout();
    setState({ status: 'ready', user: null });
  }, []);

  const value = useMemo(
    () => ({ ...state, login, signup, logout }),
    [state, login, signup, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
