import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, getRefreshToken, setAccessToken, setRefreshToken } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const applyTokens = useCallback((tokens) => {
    setAccessToken(tokens.accessToken);
    setRefreshToken(tokens.refreshToken);
    setUser({ username: tokens.username, role: tokens.role });
  }, []);

  // A refresh token in localStorage survives a reload but the access token does
  // not, so trade it for a fresh pair on mount to restore the session.
  useEffect(() => {
    const stored = getRefreshToken();
    if (!stored) {
      setLoading(false);
      return;
    }
    api
      .post('/api/auth/refresh', { refreshToken: stored }, { auth: false })
      .then((tokens) => applyTokens(tokens))
      .catch(() => {
        setAccessToken(null);
        setRefreshToken(null);
      })
      .finally(() => setLoading(false));
  }, [applyTokens]);

  const login = useCallback(
    async (username, password) => {
      // Deliberately never retried on failure: the auth tier allows only
      // 5 requests per minute per IP, and a retry loop locks the user out.
      const tokens = await api.post('/api/auth/login', { username, password }, { auth: false });
      applyTokens(tokens);
      return tokens;
    },
    [applyTokens],
  );

  const register = useCallback(
    async (details) => {
      const tokens = await api.post('/api/auth/register', details, { auth: false });
      applyTokens(tokens);
      return tokens;
    },
    [applyTokens],
  );

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) await api.post('/api/auth/logout', { refreshToken });
    } catch {
      // Revoking server-side is best effort; clearing locally is what matters.
    }
    setAccessToken(null);
    setRefreshToken(null);
    setUser(null);
  }, []);

  const value = {
    user,
    loading,
    login,
    register,
    logout,
    isAuthenticated: user !== null,
    // The API reports roles as stored: ROLE_ADMIN or ROLE_NORMAL. ROLE_USER is
    // a Spring authority that never appears in any JSON payload.
    isAdmin: user?.role === 'ROLE_ADMIN',
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
