import React, { createContext, useContext, useState, useCallback } from 'react';

const AuthContext = createContext(null);

const TOKEN_KEY = 'testhub_token';
const USER_KEY  = 'testhub_user';

export function AuthProvider({ children }) {
  const [user,  setUser]  = useState(() => {
    const stored = localStorage.getItem(USER_KEY);
    return stored ? JSON.parse(stored) : null;
  });
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));

  const login = useCallback(async (username, password) => {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Identifiants incorrects');
    }

    const data = await res.json();
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(data));
    setToken(data.token);
    setUser(data);
    return data;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setToken(null);
    setUser(null);
  }, []);

  const changePassword = useCallback(async (currentPassword, newPassword) => {
    const res = await fetch('/api/auth/change-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({ currentPassword, newPassword }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Erreur changement mot de passe');
    }
    // Mettre à jour firstLogin en local
    const updated = { ...user, firstLogin: false };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    setUser(updated);
  }, [token, user]);

  // ── Helpers de rôle ──────────────────────────────────────────────────────
  const ROLE_ORDER = ['VIEWER', 'QA_ENGINEER', 'QA_LEAD', 'ADMIN'];

  const hasRole = useCallback((required) => {
    if (!user?.role) return false;
    return ROLE_ORDER.indexOf(user.role) >= ROLE_ORDER.indexOf(required);
  }, [user]);

  const isAdmin      = useCallback(() => hasRole('ADMIN'),       [hasRole]);
  const isQaLead     = useCallback(() => hasRole('QA_LEAD'),     [hasRole]);
  const isQaEngineer = useCallback(() => hasRole('QA_ENGINEER'), [hasRole]);

  return (
    <AuthContext.Provider value={{
      user, token,
      login, logout, changePassword,
      hasRole, isAdmin, isQaLead, isQaEngineer,
      isAuthenticated: !!token,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth doit être utilisé dans AuthProvider');
  return ctx;
}