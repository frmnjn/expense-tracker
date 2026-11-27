import apiClient from './api'
import { getAccessCode } from '../utils/access'
import { demoReportUrl, isDemoMode } from '../utils/demo'
import type { ApiResponse } from '../types/expense'
import type { PeriodReportRegenerationResponse, PeriodReportsResponse } from '../types/report'

export async function getReports(): Promise<PeriodReportsResponse> {
  const response = await apiClient.get<ApiResponse<PeriodReportsResponse>>('/reports')
  return response.data.data ?? { reports: [] }
}

export async function regenerateReports(): Promise<PeriodReportRegenerationResponse> {
  const response = await apiClient.post<ApiResponse<PeriodReportRegenerationResponse>>('/reports/regenerate')
  return response.data.data ?? { regenerated: [], failed: [] }
}

function reportUrl(period: string, inline: boolean): string {
  if (isDemoMode) {
    return demoReportUrl(`report-${encodeURIComponent(period)}.pdf`)
  }
  const params = new URLSearchParams()
  const code = getAccessCode()
  if (code) params.set('access_code', code)
  if (inline) params.set('inline', 'true')
  const query = params.toString()
  return `${apiClient.defaults.baseURL}/reports/${encodeURIComponent(period)}${query ? `?${query}` : ''}`
}

export function getReportDownloadUrl(period: string): string {
  return reportUrl(period, false)
}

export function getReportViewUrl(period: string): string {
  return reportUrl(period, true)
}
