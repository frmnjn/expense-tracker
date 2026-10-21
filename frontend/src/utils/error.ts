import { isDemoMode } from './demo'

export function getErrorMessage(error: unknown): string {
  const response = (error as { response?: { status?: number; data?: { message?: string } } })?.response
  const message = response?.data?.message
  if (message) return message
  if (isDemoMode && response?.status === 403) {
    return 'Ini cuma demo — data nggak bisa diubah.'
  }
  return 'Terjadi kesalahan, coba lagi'
}
