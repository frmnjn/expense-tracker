import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { Button, Group, Loader, Modal, Stack, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { IconAlertTriangle, IconDownload, IconExternalLink } from '@tabler/icons-react'
import { PdfViewerContext } from './PdfViewerContext'
import type { PdfTarget } from './PdfViewerContext'

type PdfLoadState =
  | { status: 'idle' }
  | { status: 'loading'; pages: number; rendered: number }
  | { status: 'ready'; pages: number; rendered: number }
  | { status: 'error'; message: string }

function PdfViewerModal({
  target,
  onClose,
}: {
  target: PdfTarget | null
  onClose: () => void
}) {
  const isMobile = useMediaQuery('(max-width: 48em)')
  const [containerEl, setContainerEl] = useState<HTMLDivElement | null>(null)
  const [state, setState] = useState<PdfLoadState>({ status: 'idle' })
  const opened = target !== null

  // Back di PWA harus menutup viewer, bukan menutup aplikasi.
  useEffect(() => {
    if (!opened) return
    window.history.pushState({ __pdfViewer: true }, '')
    const handlePop = () => onClose()
    window.addEventListener('popstate', handlePop)
    return () => {
      window.removeEventListener('popstate', handlePop)
      const historyState = window.history.state as { __pdfViewer?: boolean } | null
      if (historyState?.__pdfViewer) {
        window.history.back()
      }
    }
  }, [opened, onClose])

  useEffect(() => {
    if (!opened || !target?.url || !containerEl) return
    const container = containerEl

    let cancelled = false
    let destroyPdf: (() => void) | null = null
    container.innerHTML = ''
    setState({ status: 'loading', pages: 0, rendered: 0 })

    const renderPdf = async () => {
      try {
        const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs')
        const workerSrc = (await import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url')).default
        pdfjs.GlobalWorkerOptions.workerSrc = workerSrc

        const loadingTask = pdfjs.getDocument({ url: target.url })
        destroyPdf = () => {
          void loadingTask.destroy()
        }
        const pdf = await loadingTask.promise
        if (cancelled) return

        setState({ status: 'loading', pages: pdf.numPages, rendered: 0 })
        const width = Math.min(Math.max(container.clientWidth || 360, 280), 900)
        const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)

        for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber++) {
          if (cancelled) return
          const page = await pdf.getPage(pageNumber)
          const baseViewport = page.getViewport({ scale: 1 })
          const viewport = page.getViewport({ scale: width / baseViewport.width })
          const canvas = document.createElement('canvas')
          canvas.width = Math.floor(viewport.width * pixelRatio)
          canvas.height = Math.floor(viewport.height * pixelRatio)
          canvas.style.width = `${Math.floor(viewport.width)}px`
          canvas.style.height = `${Math.floor(viewport.height)}px`
          canvas.style.display = 'block'
          canvas.style.margin = '0 auto 10px'
          canvas.style.background = '#ffffff'
          canvas.style.borderRadius = '4px'
          canvas.style.boxShadow = '0 1px 4px rgba(0, 0, 0, 0.18)'

          const context = canvas.getContext('2d')
          if (!context) {
            throw new Error('Canvas tidak tersedia untuk menampilkan PDF')
          }
          await page.render({
            canvas,
            canvasContext: context,
            viewport,
            transform: pixelRatio === 1 ? undefined : [pixelRatio, 0, 0, pixelRatio, 0, 0],
          }).promise
          if (cancelled) return
          container.appendChild(canvas)
          setState((current) => current.status === 'error'
            ? current
            : { status: pageNumber === pdf.numPages ? 'ready' : 'loading', pages: pdf.numPages, rendered: pageNumber })
        }
      } catch (error) {
        if (!cancelled) {
          setState({
            status: 'error',
            message: error instanceof Error ? error.message : 'Gagal memuat PDF',
          })
        }
      }
    }

    void renderPdf()

    return () => {
      cancelled = true
      destroyPdf?.()
      container.innerHTML = ''
    }
  }, [opened, target, containerEl])

  const close = () => onClose()
  const downloadUrl = target?.downloadUrl ?? target?.url

  return (
    <Modal
      opened={opened}
      onClose={close}
      title={target?.title ?? 'Dokumen PDF'}
      fullScreen={isMobile}
      centered={!isMobile}
      size="xl"
      zIndex={3000}
      closeOnClickOutside={false}
      styles={isMobile ? {
        body: { height: 'calc(100dvh - 60px)', display: 'flex', flexDirection: 'column' },
      } : undefined}
    >
      <Stack gap="sm" h={isMobile ? '100%' : undefined}>
        <Group justify="space-between" gap="xs">
          <Text size="xs" c="dimmed">
            {state.status === 'loading' && state.pages > 0
              ? `Memuat halaman ${state.rendered + 1} dari ${state.pages}…`
              : state.status === 'loading'
                ? 'Memuat PDF…'
                : state.status === 'ready'
                  ? `${state.pages} halaman`
                  : null}
          </Text>
          <Group gap="xs" wrap="nowrap">
            {target?.url && (
              <Button
                component="a"
                href={target.url}
                target="_blank"
                rel="noreferrer"
                variant="subtle"
                size="compact-sm"
                leftSection={<IconExternalLink size={14} />}
              >
                Tab baru
              </Button>
            )}
            {downloadUrl && (
              <Button
                component="a"
                href={downloadUrl}
                download={target?.fileName}
                variant="light"
                size="compact-sm"
                leftSection={<IconDownload size={14} />}
              >
                Unduh
              </Button>
            )}
          </Group>
        </Group>

        {state.status === 'error' ? (
          <Stack align="center" gap="sm" py="xl">
            <IconAlertTriangle size={36} aria-hidden />
            <Text fw={600}>PDF gagal dimuat</Text>
            <Text size="sm" c="dimmed" ta="center">
              {state.message}
            </Text>
            {target?.url && (
              <Button component="a" href={target.url} target="_blank" rel="noreferrer">
                Buka di tab baru
              </Button>
            )}
          </Stack>
        ) : (
          <div
            ref={setContainerEl}
            style={{
              height: isMobile ? 'calc(100dvh - 160px)' : '75vh',
              minHeight: 240,
              overflowY: 'auto',
              background: '#dee2e6',
              borderRadius: 8,
              padding: 8,
            }}
          />
        )}

        {state.status === 'loading' && (
          <Group justify="center" py="sm">
            <Loader size="sm" />
          </Group>
        )}
      </Stack>
    </Modal>
  )
}

export function PdfViewerProvider({ children }: { children: ReactNode }) {
  const [target, setTarget] = useState<PdfTarget | null>(null)
  const openPdf = useCallback((next: PdfTarget) => setTarget(next), [])
  const close = useCallback(() => setTarget(null), [])
  const value = useMemo(() => ({ openPdf }), [openPdf])

  return (
    <PdfViewerContext.Provider value={value}>
      {children}
      <PdfViewerModal target={target} onClose={close} />
    </PdfViewerContext.Provider>
  )
}
