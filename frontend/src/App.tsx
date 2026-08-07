import { BrowserRouter, Route, Routes } from 'react-router-dom'
import ExpensePage from './pages/ExpensePage'
import HistoryPage from './pages/HistoryPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ExpensePage />} />
        <Route path="/catat" element={<ExpensePage />} />
        <Route path="/riwayat" element={<HistoryPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
