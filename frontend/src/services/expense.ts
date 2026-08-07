import apiClient from './api'
import type {
  ApiResponse,
  ExpenseRequest,
  ExpensesResponse,
  OptionsResponse,
  PeriodsResponse,
} from '../types/expense'

export async function createExpense(request: ExpenseRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>('/expenses', request)
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

export async function updateExpense(id: string, request: ExpenseRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.put<ApiResponse<void>>(`/expenses/${id}`, request)
  return response.data
}

export async function deleteExpense(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.delete<ApiResponse<void>>(`/expenses/${id}`)
  return response.data
}
