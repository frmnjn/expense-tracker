import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { createExpense, deletePhoto, uploadPhoto } from '../services/expense'
import type { ExpenseRequest } from '../types/expense'

export function useCreateExpense() {
  return useMutation({
    mutationFn: (request: ExpenseRequest) => createExpense(request),
  })
}

export function useUploadPhoto() {
  const queryClient = useQueryClient()
  const [progress, setProgress] = useState(0)
  const mutation = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      setProgress(0)
      return uploadPhoto(id, file, (percent) => setProgress(percent))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
    },
  })
  return { ...mutation, progress }
}

export function useDeletePhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deletePhoto(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
    },
  })
}
