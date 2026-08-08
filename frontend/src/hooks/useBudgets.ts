import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createBudget, deleteBudget, updateBudget } from '../services/expense'
import type { BudgetCreateRequest, BudgetUpdateRequest } from '../types/expense'

export function useCreateBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: BudgetCreateRequest) => createBudget(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}

export function useDeleteBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => deleteBudget(name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}

export function useUpdateBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ name, request }: { name: string; request: BudgetUpdateRequest }) =>
      updateBudget(name, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}
