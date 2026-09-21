import apiClient from './api'
import type { ApiResponse, ExpenseRequest } from '../types/expense'
import type { EmailImportsResponse } from '../types/emailImport'

export async function getEmailImports(status?: string): Promise<EmailImportsResponse> {
  const response = await apiClient.get<ApiResponse<EmailImportsResponse>>('/email-imports', {
    params: status ? { status } : undefined,
  })
  return response.data.data ?? { imports: [] }
}

export async function importEmail(
  id: string,
  request: ExpenseRequest,
  force = false,
): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>(
    `/email-imports/${encodeURIComponent(id)}/import`,
    request,
    { params: force ? { force: 'true' } : undefined },
  )
  return response.data
}

export async function discardEmail(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>(
    `/email-imports/${encodeURIComponent(id)}/discard`,
  )
  return response.data
}

export async function retryEmailImport(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>(
    `/email-imports/${encodeURIComponent(id)}/retry`,
  )
  return response.data
}

export async function pollEmails(): Promise<{ count: number }> {
  const response = await apiClient.post<ApiResponse<{ count: number }>>('/email-imports/poll')
  return response.data.data ?? { count: 0 }
}
