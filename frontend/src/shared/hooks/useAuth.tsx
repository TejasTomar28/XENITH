import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { AuthResponse, Identity, LoginRequest, UserRole } from '../../types/auth';
import { authApi } from '../../api/auth.api';
import { getAuthToken, setAuthToken } from '../../api/client';

export interface AuthContextValue {
  user: Identity | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  role: UserRole | null;
  login: (credentials: LoginRequest) => Promise<AuthResponse>;
  logout: () => void;
  hasRole: (role: UserRole) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<Identity | null>(null);
  const [token, setTokenState] = useState<string | null>(getAuthToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Restore session on initial load
  useEffect(() => {
    const existingToken = getAuthToken();
    if (existingToken) {
      authApi
        .getMe()
        .then((userData) => {
          setUser(userData);
          setTokenState(existingToken);
        })
        .catch(() => {
          // Token invalid or expired
          setAuthToken(null);
          setUser(null);
          setTokenState(null);
        })
        .finally(() => {
          setIsLoading(false);
        });
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = useCallback(async (credentials: LoginRequest): Promise<AuthResponse> => {
    setIsLoading(true);
    try {
      const resp = await authApi.login(credentials);
      setTokenState(resp.access_token);
      setUser(resp.user);
      return resp;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    authApi.logout();
    setTokenState(null);
    setUser(null);
  }, []);

  const role = user?.roles?.[0] || null;

  const hasRole = useCallback(
    (targetRole: UserRole): boolean => {
      return !!user?.roles?.includes(targetRole);
    },
    [user]
  );

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token,
        isLoading,
        role,
        login,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
