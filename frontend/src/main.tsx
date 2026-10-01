import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createTheme, localStorageColorSchemeManager, MantineProvider } from '@mantine/core'
import { DatesProvider } from '@mantine/dates'
import '@mantine/core/styles.css'
import '@mantine/dates/styles.css'
import './index.css'
import App from './App.tsx'
import ErrorBoundary from './components/ErrorBoundary.tsx'
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

// Aksen brand diselaraskan dengan email notifier (#863bff).
const theme = createTheme({
  primaryColor: 'brand',
  colors: {
    brand: [
      '#f5f0ff',
      '#e6dbff',
      '#cab4ff',
      '#ac8aff',
      '#966aff',
      '#8a54ff',
      '#863bff',
      '#7733e6',
      '#6a2bcb',
      '#5b22a8',
    ],
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <MantineProvider theme={theme} colorSchemeManager={colorSchemeManager} defaultColorScheme="dark">
        <DatesProvider settings={{}}>
          <ToastProvider>
            <ErrorBoundary>
              <App />
            </ErrorBoundary>
          </ToastProvider>
        </DatesProvider>
      </MantineProvider>
    </QueryClientProvider>
  </StrictMode>,
)
