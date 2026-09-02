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
