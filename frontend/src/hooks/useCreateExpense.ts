import { useMutation } from '@tanstack/react-query'
import { createExpense } from '../services/expense'
import type { ExpenseRequest } from '../types/expense'

export function useCreateExpense() {
  return useMutation({
    mutationFn: (request: ExpenseRequest) => createExpense(request),
  })
}
