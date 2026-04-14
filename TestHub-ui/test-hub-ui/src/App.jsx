import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import AppLayout from './components/AppLayout';
import Dashboard from './pages/Dashboard';
import Projects from './pages/Projects';
import ProjectDetail from './pages/ProjectDetail';
import RunDetail from './pages/RunDetail';
import Runs from './pages/Runs';
import UsersPage from './pages/UsersPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* ── Pages publiques ────────────────────────────────────────── */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/change-password" element={<ChangePasswordPage />} />

          {/* ── Pages protégées ────────────────────────────────────────── */}
          <Route path="/" element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard"      element={<Dashboard />} />
            <Route path="projects"       element={<Projects />} />
            <Route path="projects/:id"   element={<ProjectDetail />} />
            <Route path="runs"           element={<Runs />} />
            <Route path="runs/:id"       element={<RunDetail />} />

            {/* ── ADMIN seulement ──────────────────────────────────────── */}
            <Route path="users" element={
              <ProtectedRoute minimumRole="ADMIN">
                <UsersPage />
              </ProtectedRoute>
            } />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}