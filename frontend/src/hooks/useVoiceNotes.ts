import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  deleteVoiceNote,
  getVoiceNoteDetail,
  getVoiceNotes,
  retryVoiceNote,
  uploadVoiceNote,
} from '../services/voiceNotes'

export function useVoiceNotes(period: string | null) {
  return useQuery({
    queryKey: ['voice-notes', period],
    queryFn: () => getVoiceNotes(period ?? ''),
    enabled: !!period,
    refetchInterval: (query) => {
      const hasAnalyzing = query.state.data?.voiceNotes.some((n) => n.status === 'ANALYZING')
      return hasAnalyzing ? 1000 : false
    },
  })
}

export function useVoiceNoteDetail(id: string | null) {
  return useQuery({
    queryKey: ['voice-note-detail', id],
    queryFn: () => getVoiceNoteDetail(id ?? ''),
    enabled: !!id,
    refetchInterval: (query) => (query.state.data?.status === 'ANALYZING' ? 1000 : false),
  })
}

export function useUploadVoiceNote() {
  const queryClient = useQueryClient()
  const [progress, setProgress] = useState(0)
  const mutation = useMutation({
    mutationFn: ({ file, dateTime, durationMs }: { file: File; dateTime: string; durationMs: number }) => {
      setProgress(0)
      return uploadVoiceNote(file, dateTime, durationMs, (percent) => setProgress(percent))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['voice-notes'] })
    },
  })
  return { ...mutation, progress }
}

export function useRetryVoiceNote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => retryVoiceNote(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['voice-notes'] })
      queryClient.invalidateQueries({ queryKey: ['voice-note-detail'] })
    },
  })
}

export function useDeleteVoiceNote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteVoiceNote(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['voice-notes'] })
      queryClient.invalidateQueries({ queryKey: ['voice-note-detail'] })
    },
  })
}
