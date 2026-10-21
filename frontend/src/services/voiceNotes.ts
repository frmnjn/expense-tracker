import apiClient from './api'
import { newIdempotencyKey } from '../utils/idempotency'
import { getAccessCode } from '../utils/access'
import type { ApiResponse, VoiceNoteDetail, VoiceNotesResponse } from '../types/expense'

export async function uploadVoiceNote(
  file: File,
  dateTime: string,
  durationMs: number,
  onProgress?: (percent: number) => void,
): Promise<{ voiceNoteId: string }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('date', dateTime)
  formData.append('durationMs', String(Math.round(durationMs)))
  const response = await apiClient.post<ApiResponse<{ voiceNoteId: string }>>('/voice-notes', formData, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data.data ?? { voiceNoteId: '' }
}

export async function getVoiceNotes(period: string): Promise<VoiceNotesResponse> {
  const response = await apiClient.get<ApiResponse<VoiceNotesResponse>>('/voice-notes', {
    params: { period },
  })
  return response.data.data ?? { voiceNotes: [] }
}

export async function getVoiceNoteDetail(id: string): Promise<VoiceNoteDetail> {
  const response = await apiClient.get<ApiResponse<VoiceNoteDetail>>(`/voice-notes/${encodeURIComponent(id)}`)
  return response.data.data ?? { id, status: 'ERROR' }
}

export async function retryVoiceNote(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.post<ApiResponse<void>>(`/voice-notes/${encodeURIComponent(id)}/retry`)
  return response.data
}

export async function deleteVoiceNote(id: string): Promise<ApiResponse<void>> {
  const response = await apiClient.delete<ApiResponse<void>>(`/voice-notes/${encodeURIComponent(id)}`)
  return response.data
}

export function getVoiceNoteAudioUrl(id: string): string {
  const code = getAccessCode()
  const suffix = code ? `?access_code=${encodeURIComponent(code)}` : ''
  return `${apiClient.defaults.baseURL}/voice-notes/${encodeURIComponent(id)}/audio${suffix}`
}
