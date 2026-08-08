import { useMutation } from '@tanstack/react-query'
import { createExpense, uploadPhoto } from '../services/expense'
import type { ExpenseRequest } from '../types/expense'

export function useCreateExpense() {
  return useMutation({
    mutationFn: (request: ExpenseRequest) => createExpense(request),
  })
}

export function useUploadPhoto() {
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => uploadPhoto(id, file),
  })
}
