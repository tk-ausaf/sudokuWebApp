import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api/client.js';

const AuthContext = createContext(null);

const TOKEN_KEY = 'sudoku_jwt_token';
const NAME_KEY = 'sudoku_username';

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [username, setUsername] = useState(() => localStorage.getItem(NAME_KEY));

  useEffect(() => {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  }, [token]);

  useEffect(() => {
    if (username) {
      localStorage.setItem(NAME_KEY, username);
    } else {
      localStorage.removeItem(NAME_KEY);
    }
  }, [username]);

  // Google sign-in redirects here with ?token=&username= (the backend hands the JWT back this
  // way since it lives in localStorage, not a cookie). Consume it once and strip it from the
  // address bar so it doesn't linger in browser history.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const googleToken = params.get('token');
    const googleUsername = params.get('username');
    if (!googleToken || !googleUsername) {
      return;
    }
    setToken(googleToken);
    setUsername(googleUsername);
    params.delete('token');
    params.delete('username');
    const remaining = params.toString();
    window.history.replaceState({}, '', window.location.pathname + (remaining ? `?${remaining}` : ''));
  }, []);

  const login = useCallback(async (name, password) => {
    const newToken = await api.login(name, password);
    if (!newToken) {
      throw new Error('Invalid username or password');
    }
    setToken(newToken);
    setUsername(name);
  }, []);

  const register = useCallback(async (name, password) => {
    const created = await api.register(name, password);
    if (!created) {
      throw new Error('That username is already taken');
    }
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUsername(null);
  }, []);

  const value = { token, username, isLoggedIn: Boolean(token), login, register, logout };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
