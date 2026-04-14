import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
 
/**
 * Route protégée — redirige vers /login si non authentifié.
 * Si minimumRole fourni → redirige vers / si rôle insuffisant.
 *
 * Usage :
 *   <ProtectedRoute>                          → authentifié seulement
 *   <ProtectedRoute minimumRole="QA_LEAD">    → QA_LEAD ou supérieur
 *   <ProtectedRoute minimumRole="ADMIN">      → ADMIN seulement
 */
export default function ProtectedRoute({ children, minimumRole }) {
  const { isAuthenticated, hasRole, user } = useAuth();
 
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
 
  // Forcer changement password premier login
  if (user?.firstLogin && window.location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
 
  if (minimumRole && !hasRole(minimumRole)) {
    return <Navigate to="/" replace />;
  }
 
  return children;
}