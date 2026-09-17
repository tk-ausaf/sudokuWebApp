import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { clearGuestId } from '../utils/guestIdentity.js';

const AuthContext = createContext(null);

const TOKEN_KEY = 'sudoku_jwt_token';
const NAME_KEY = 'sudoku_username';

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [username, setUsername] = useState(() => localStorage.getItem(NAME_KEY));
  // Recovery email is optional and not persisted client-side - re-fetched from the account
  // profile whenever we have a token, so it can't go stale across tabs/devices. undefined =
  // not loaded yet (don't nag), null = loaded and confirmed absent (do nag), string = present.
  const [email, setEmail] = useState(undefined);

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
    clearGuestId();
    params.delete('token');
    params.delete('username');
    const remaining = params.toString();
    window.history.replaceState({}, '', window.location.pathname + (remaining ? `?${remaining}` : ''));
  }, []);

  useEffect(() => {
    if (!token) {
      setEmail(undefined);
      return;
    }
    api
      .getMe(token)
      .then((profile) => setEmail(profile.email ?? null))
      .catch(() => {});
  }, [token]);

  const updateEmail = useCallback(async (newEmail) => {
    const profile = await api.updateEmail(token, newEmail);
    setEmail(profile.email);
  }, [token]);

  const login = useCallback(async (name, password) => {
    const newToken = await api.login(name, password);
    if (!newToken) {
      throw new Error('Invalid username or password');
    }
    setToken(newToken);
    setUsername(name);
    clearGuestId();
  }, []);

  const register = useCallback(async (name, password, email) => {
    const created = await api.register(name, password, email);
    if (!created) {
      throw new Error('That username is already taken');
    }
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUsername(null);
    setEmail(undefined);
  }, []);

  const value = { token, username, email, isLoggedIn: Boolean(token), login, register, logout, updateEmail };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
