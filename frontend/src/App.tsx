import { lazy } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import ExpensePage from './pages/ExpensePage'
import LockPage from './pages/LockPage'
import { getAccessCode } from './utils/access'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const HistoryPage = lazy(() => import('./pages/HistoryPage'))
const InboxPage = lazy(() => import('./pages/InboxPage'))
const ScanPage = lazy(() => import('./pages/ScanPage'))
const VoicePage = lazy(() => import('./pages/VoicePage'))
const ReportsPage = lazy(() => import('./pages/ReportsPage'))

function Protected() {
  return getAccessCode() ? <AppLayout /> : <Navigate to="/lock" replace />
}

const routerBasename = import.meta.env.BASE_URL.replace(/\/$/, '') || '/'

function App() {
  return (
    <BrowserRouter basename={routerBasename}>
      <Routes>
        <Route path="/lock" element={<LockPage />} />
        <Route element={<Protected />}>
          <Route path="/" element={<Navigate to="/catat" replace />} />
          <Route path="/catat" element={<ExpensePage />} />
          <Route path="/scan" element={<ScanPage />} />
          <Route path="/voice" element={<VoicePage />} />
          <Route path="/inbox" element={<InboxPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/riwayat" element={<HistoryPage />} />
          <Route path="/laporan" element={<ReportsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
