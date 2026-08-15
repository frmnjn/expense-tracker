import apiClient from './api'
import { newIdempotencyKey } from '../utils/idempotency'
import { getAccessCode } from '../utils/access'
import type {
  ApiResponse,
  BatchExpenseRequest,
  BudgetCreateRequest,
  BudgetUpdateRequest,
  ExpenseRequest,
  ExpensesResponse,
  InvoiceDetail,
  InvoicesResponse,
  OptionsResponse,
  PeriodsResponse,
  SummaryResponse,
  TopUpRequest,
  TopUpsResponse,
  TrendResponse,
} from '../types/expense'

export async function createBudget(request: BudgetCreateRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>('/budgets', request, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return response.data
}

export async function updateBudget(name: string, request: BudgetUpdateRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.put<ApiResponse<void>>(`/budgets/${encodeURIComponent(name)}`, request)
  return response.data
}

export async function deleteBudget(name: string): Promise<ApiResponse<void>> {
  const response = await apiClient.delete<ApiResponse<void>>(`/budgets/${encodeURIComponent(name)}`)
  return response.data
}

export async function createExpense(request: ExpenseRequest): Promise<{ id: string }> {
  const response = await apiClient.post<ApiResponse<{ id: string }>>('/expenses', request, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return response.data.data ?? { id: '' }
}

function accessSuffix(): string {
  const code = getAccessCode()
  return code ? `?access_code=${encodeURIComponent(code)}` : ''
}

export function getPhotoUrl(id: string): string {
  return `${apiClient.defaults.baseURL}/expenses/${encodeURIComponent(id)}/photo${accessSuffix()}`
}

export function getInvoicePhotoUrl(id: string): string {
  return `${apiClient.defaults.baseURL}/invoices/${encodeURIComponent(id)}/photo${accessSuffix()}`
}

export async function getInvoices(dateTime: string, scanOnly = false, period?: string): Promise<InvoicesResponse> {
  const response = await apiClient.get<ApiResponse<InvoicesResponse>>('/invoices', {
    params: {
      ...(dateTime ? { date: dateTime } : {}),
      ...(scanOnly ? { scan: 'true' } : {}),
      ...(period ? { period } : {}),
    },
  })
  return response.data.data ?? { invoices: [] }
}

export async function uploadInvoice(
  file: File,
  dateTime: string,
  onProgress?: (percent: number) => void,
): Promise<{ invoiceId: string }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('date', dateTime)
  const response = await apiClient.post<ApiResponse<{ invoiceId: string }>>('/invoices', formData, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data.data ?? { invoiceId: '' }
}

export async function getInvoiceDetail(id: string): Promise<InvoiceDetail> {
  const response = await apiClient.get<ApiResponse<InvoiceDetail>>(`/invoices/${encodeURIComponent(id)}`)
  return response.data.data ?? { id, type: 'image', status: 'ERROR' }
}

export async function retryInvoiceAnalysis(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>(`/invoices/${encodeURIComponent(id)}/retry`)
  return response.data
}

export async function createExpensesBatch(request: BatchExpenseRequest): Promise<{ count: number }> {
  const response = await apiClient.post<ApiResponse<{ count: number }>>('/expenses/batch', request, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return response.data.data ?? { count: 0 }
}

export async function uploadPhoto(
  id: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<ApiResponse<void>> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await apiClient.post<ApiResponse<void>>(`/expenses/${id}/photo`, formData, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data
}

export async function deletePhoto(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.delete<ApiResponse<void>>(`/expenses/${id}/photo`)
  return response.data
}

export async function getOptions(): Promise<OptionsResponse> {
  const response = await apiClient.get<ApiResponse<OptionsResponse>>('/options')
  return response.data.data ?? { budgets: [] }
}

export async function getPeriods(): Promise<PeriodsResponse> {
  const response = await apiClient.get<ApiResponse<PeriodsResponse>>('/periods')
  return response.data.data ?? { periods: [] }
}

export async function getExpenses(period: string): Promise<ExpensesResponse> {
  const response = await apiClient.get<ApiResponse<ExpensesResponse>>('/expenses', {
    params: { period },
  })
  return response.data.data ?? { expenses: [] }
}

export async function getSummary(period: string): Promise<SummaryResponse> {
  const response = await apiClient.get<ApiResponse<SummaryResponse>>('/summary', {
    params: { period },
  })
  return response.data.data ?? { period, total: 0, count: 0, byBudget: [] }
}

export async function getTopUps(): Promise<TopUpsResponse> {
  const response = await apiClient.get<ApiResponse<TopUpsResponse>>('/topups')
  return response.data.data ?? { topUps: [] }
}

export async function createTopUp(request: TopUpRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>('/topups', request, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return response.data
}

export async function getTrend(months = 3): Promise<TrendResponse> {
  const response = await apiClient.get<ApiResponse<TrendResponse>>('/trend', {
    params: { months },
  })
  return response.data.data ?? { periods: [] }
}

export async function updateExpense(id: string, request: ExpenseRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.put<ApiResponse<void>>(`/expenses/${id}`, request)
  return response.data
}

export async function deleteExpense(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.delete<ApiResponse<void>>(`/expenses/${id}`)
  return response.data
}
