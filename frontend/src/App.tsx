import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import DashboardPage from './pages/DashboardPage'
import ExpensePage from './pages/ExpensePage'
import HistoryPage from './pages/HistoryPage'
import InboxPage from './pages/InboxPage'
import LockPage from './pages/LockPage'
import ScanPage from './pages/ScanPage'
import { getAccessCode } from './utils/access'

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
