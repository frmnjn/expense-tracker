import apiClient from './api'
import type { ApiResponse, ExpenseRequest, OptionsResponse } from '../types/expense'

export async function createExpense(request: ExpenseRequest): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>('/expenses', request)
  return response.data
}

export async function getOptions(): Promise<OptionsResponse> {
  const response = await apiClient.get<ApiResponse<OptionsResponse>>('/options')
  return response.data.data ?? { budgets: [] }
}
