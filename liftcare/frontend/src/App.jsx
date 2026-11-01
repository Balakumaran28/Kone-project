import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import PublicHeader from './components/PublicHeader';
import PortalHeader from './components/PortalHeader';
import Sidebar from './components/Sidebar';
import PublicFooter from './components/PublicFooter';
import PortalFooter from './components/PortalFooter';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import BuildingsPage from './pages/BuildingsPage';
import BuildingDetailPage from './pages/BuildingDetailPage';
import IncidentsPage from './pages/IncidentsPage';
import ReportIncidentPage from './pages/ReportIncidentPage';
import IncidentDetailPage from './pages/IncidentDetailPage';
import ControlRoomPage from './pages/ControlRoomPage';
import OfflineQueuePage from './pages/OfflineQueuePage';
import LocalSyncStatusPage from './pages/LocalSyncStatusPage';
import CentralDashboardPage from './pages/CentralDashboardPage';
import ProtectedRoute from './components/ProtectedRoute';
import ErrorBoundary from './components/ErrorBoundary';
import './App.css';

/* Public routes: full-width marketing site experience */
const PUBLIC_PATHS = ['/', '/login', '/register'];

function AppShell() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  const isPublicRoute = PUBLIC_PATHS.includes(location.pathname);

  /* ── Public site layout ───────────────────────────── */
  if (isPublicRoute && !isAuthenticated) {
    return (
      <div className="app-container">
        <PublicHeader />
        <main style={{ flex: 1 }}>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            {/* Catch-all in public context */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <PublicFooter />
      </div>
    );
  }

  /* ── Operations portal layout ─────────────────────── */
  return (
    <div className="app-container">
      <PortalHeader />
      <div className="app-layout">
        {isAuthenticated && <Sidebar />}
        <main className="main-viewport">
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            <Route path="/dashboard" element={
              <ProtectedRoute><DashboardPage /></ProtectedRoute>
            } />
            <Route path="/buildings" element={
              <ProtectedRoute><BuildingsPage /></ProtectedRoute>
            } />
            <Route path="/buildings/:id" element={
              <ProtectedRoute><BuildingDetailPage /></ProtectedRoute>
            } />
            <Route path="/incidents" element={
              <ProtectedRoute><IncidentsPage /></ProtectedRoute>
            } />
            <Route path="/incidents/report" element={
              <ProtectedRoute><ReportIncidentPage /></ProtectedRoute>
            } />
            <Route path="/incidents/:id" element={
              <ProtectedRoute><IncidentDetailPage /></ProtectedRoute>
            } />
            <Route path="/control-room" element={
              <ProtectedRoute><ControlRoomPage /></ProtectedRoute>
            } />
            <Route path="/cloud-sync" element={
              <ProtectedRoute><LocalSyncStatusPage /></ProtectedRoute>
            } />
            <Route path="/central-dashboard" element={
              <ProtectedRoute><CentralDashboardPage /></ProtectedRoute>
            } />
            <Route path="/offline-queue" element={<OfflineQueuePage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
      <PortalFooter />
    </div>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <AppShell />
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
}
