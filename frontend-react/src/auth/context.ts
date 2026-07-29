import { createContext } from 'react';
import type { User } from '../api/types';

export interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (redirectTo?: string) => void;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
