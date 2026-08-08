import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createExpense, deletePhoto, uploadPhoto } from '../services/expense'
import type { ExpenseRequest } from '../types/expense'

export function useCreateExpense() {
  return useMutation({
    mutationFn: (request: ExpenseRequest) => createExpense(request),
  })
}

export function useUploadPhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => uploadPhoto(id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
    },
  })
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
