import { BrowserRouter, Route, Routes } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage'
import ExpensePage from './pages/ExpensePage'
import HistoryPage from './pages/HistoryPage'
import ScanPage from './pages/ScanPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ExpensePage />} />
        <Route path="/catat" element={<ExpensePage />} />
        <Route path="/scan" element={<ScanPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/riwayat" element={<HistoryPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
