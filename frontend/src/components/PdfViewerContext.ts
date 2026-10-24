import { createContext, useContext } from 'react'

export type PdfTarget = {
  url: string
  title?: string
  downloadUrl?: string
  fileName?: string
}

export type PdfViewerContextValue = {
  openPdf: (target: PdfTarget) => void
}

export const PdfViewerContext = createContext<PdfViewerContextValue | null>(null)

export function usePdfViewer(): PdfViewerContextValue {
  const context = useContext(PdfViewerContext)
  if (!context) {
    throw new Error('usePdfViewer harus dipakai di dalam PdfViewerProvider')
  }
  return context
}
