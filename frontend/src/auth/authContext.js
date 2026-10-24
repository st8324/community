import { createContext, useContext } from 'react';

/** 로그인 상태 Context. 값: { status: 'loading' | 'ready', user: { id, displayName } | null, login, signup, logout } */
export const AuthContext = createContext(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (value === null) {
    throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있다.');
  }
  return value;
}
