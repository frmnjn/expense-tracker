import { createContext, useCallback, useContext, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Paper, Stack, Text } from '@mantine/core'

type ToastTone = 'success' | 'error' | 'warning'

interface ToastItem {
  id: number
  title: string
  message: string
  tone: ToastTone
}

interface ToastOptions {
  title?: string
}

interface ToastApi {
  success: (message: string, options?: ToastOptions) => void
  error: (message: string, options?: ToastOptions) => void
  warning: (message: string, options?: ToastOptions) => void
}

const ToastContext = createContext<ToastApi | null>(null)

const DEFAULT_TITLE: Record<ToastTone, string> = {
  success: 'Berhasil',
  error: 'Gagal',
  warning: 'Perhatian',
}

const TONE_COLOR: Record<ToastTone, string> = {
  success: 'green.7',
  error: 'red.7',
  warning: 'orange.7',
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const idRef = useRef(0)

  const show = useCallback((tone: ToastTone, message: string, options?: ToastOptions) => {
    const id = ++idRef.current
    setToasts((prev) => [...prev, { id, title: options?.title ?? DEFAULT_TITLE[tone], message, tone }])
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id))
    }, 4000)
  }, [])

  const api: ToastApi = {
    success: (message, options) => show('success', message, options),
    error: (message, options) => show('error', message, options),
    warning: (message, options) => show('warning', message, options),
  }

  return (
    <ToastContext.Provider value={api}>
      {children}
      {/* pointer-events none agar tidak menghalangi scroll */}
      <Stack
        gap={8}
        style={{
          position: 'fixed',
          bottom: 'calc(96px + env(safe-area-inset-bottom, 0px))',
          left: 0,
          right: 0,
          zIndex: 1500,
          pointerEvents: 'none',
          alignItems: 'center',
          padding: '0 16px',
        }}
      >
        {toasts.map((t) => (
          <Paper
            key={t.id}
            withBorder
            p="sm"
            radius="md"
            style={{ pointerEvents: 'auto', width: '100%', maxWidth: 360 }}
          >
            <Stack gap={2}>
              <Text size="sm" fw={700} c={TONE_COLOR[t.tone]}>
                {t.title}
              </Text>
              <Text size="sm">{t.message}</Text>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider')
  }
  return ctx
}
