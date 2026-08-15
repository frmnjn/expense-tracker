import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  createExpensesBatch,
  getInvoiceDetail,
  getInvoices,
  retryInvoiceAnalysis,
  uploadInvoice,
} from '../services/expense'

export function useScanInvoices(period: string | null) {
  return useQuery({
    queryKey: ['scan-invoices', period],
    queryFn: () => getInvoices('', true, period ?? undefined),
    enabled: !!period,
    refetchInterval: (query) => {
      const hasAnalyzing = query.state.data?.invoices.some((i) => i.status === 'ANALYZING')
      return hasAnalyzing ? 3000 : false
    },
  })
}

export function useInvoiceDetail(id: string | null) {
  return useQuery({
    queryKey: ['invoice-detail', id],
    queryFn: () => getInvoiceDetail(id ?? ''),
    enabled: !!id,
    refetchInterval: (query) => (query.state.data?.status === 'ANALYZING' ? 3000 : false),
  })
}

export function useUploadInvoice() {
  const queryClient = useQueryClient()
  const [progress, setProgress] = useState(0)
  const mutation = useMutation({
    mutationFn: ({ file, dateTime }: { file: File; dateTime: string }) => {
      setProgress(0)
      return uploadInvoice(file, dateTime, (percent) => setProgress(percent))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-invoices'] })
    },
  })
  return { ...mutation, progress }
}

export function useRetryAnalysis() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => retryInvoiceAnalysis(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-invoices'] })
      queryClient.invalidateQueries({ queryKey: ['invoice-detail'] })
    },
  })
}

export function useCreateExpenseBatch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: Parameters<typeof createExpensesBatch>[0]) => createExpensesBatch(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-invoices'] })
      queryClient.invalidateQueries({ queryKey: ['invoice-detail'] })
      queryClient.invalidateQueries({ queryKey: ['options'] })
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['summary'] })
    },
  })
}
