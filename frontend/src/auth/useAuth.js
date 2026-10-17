import { useContext } from 'react';
import { AuthContext } from './authContext.js';

export function useAuth() {
  const value = useContext(AuthContext);
  if (value === null) {
    throw new Error('useAuth는 AuthProvider 안에서만 쓴다.');
  }
  return value;
}
