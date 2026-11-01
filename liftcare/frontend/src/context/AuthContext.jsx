import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { loginUser, registerUser, fetchCurrentUser, logoutUser } from '../services/api';

const AuthContext = createContext(null);

const TOKEN_KEY = 'liftcare_auth_token';
const USER_KEY = 'liftcare_auth_user';

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState(() => {
    try {
      const saved = localStorage.getItem(USER_KEY);
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(true);

  // Validate stored token on mount
  useEffect(() => {
    async function verifyStoredSession() {
      if (!token) {
        setLoading(false);
        return;
      }

      try {
        const freshUser = await fetchCurrentUser(token);
        setUser(freshUser);
        localStorage.setItem(USER_KEY, JSON.stringify(freshUser));
      } catch (err) {
        console.warn('Stored session invalid or expired:', err.message);
        // Token expired or invalid -> clear local credentials
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        setToken(null);
        setUser(null);
      } finally {
        setLoading(false);
      }
    }

    verifyStoredSession();
  }, [token]);

  const login = useCallback(async (email, password) => {
    const authData = await loginUser(email, password);
    setToken(authData.token);
    setUser(authData.user);
    localStorage.setItem(TOKEN_KEY, authData.token);
    localStorage.setItem(USER_KEY, JSON.stringify(authData.user));
    return authData.user;
  }, []);

  const register = useCallback(async (registrationData) => {
    const newUser = await registerUser(registrationData);
    return newUser;
  }, []);

  const logout = useCallback(async () => {
    await logoutUser();
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setToken(null);
    setUser(null);
  }, []);

  const value = {
    user,
    token,
    isAuthenticated: !!token && !!user,
    loading,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
