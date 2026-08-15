import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { localStorageColorSchemeManager, MantineProvider } from '@mantine/core'
import { DatesProvider } from '@mantine/dates'
import '@mantine/core/styles.css'
import '@mantine/dates/styles.css'
import './index.css'
import App from './App.tsx'
import { ToastProvider } from './components/Toast.tsx'

// Console on-device untuk debugging HP: buka app dengan ?debug di URL.
if (new URLSearchParams(window.location.search).has('debug')) {
  const script = document.createElement('script')
  script.src = 'https://cdn.jsdelivr.net/npm/eruda'
  script.onload = () => {
    ;(window as unknown as { eruda?: { init: () => void } }).eruda?.init()
  }
  document.head.appendChild(script)
}

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
  })
}

const colorSchemeManager = localStorageColorSchemeManager({ key: 'expense-color-scheme' })
const queryClient = new QueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <MantineProvider colorSchemeManager={colorSchemeManager} defaultColorScheme="dark">
        <DatesProvider settings={{}}>
          <ToastProvider>
            <App />
          </ToastProvider>
        </DatesProvider>
      </MantineProvider>
    </QueryClientProvider>
  </StrictMode>,
)
