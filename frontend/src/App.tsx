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

function Protected() {
  return getAccessCode() ? <AppLayout /> : <Navigate to="/lock" replace />
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/lock" element={<LockPage />} />
        <Route element={<Protected />}>
          <Route path="/" element={<Navigate to="/catat" replace />} />
          <Route path="/catat" element={<ExpensePage />} />
          <Route path="/scan" element={<ScanPage />} />
          <Route path="/inbox" element={<InboxPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/riwayat" element={<HistoryPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
