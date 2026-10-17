import { createContext } from 'react';

/** { status: 'loading' | 'ready', user: { id, displayName } | null, login, signup, logout } */
export const AuthContext = createContext(null);
