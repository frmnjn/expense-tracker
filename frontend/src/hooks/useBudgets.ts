import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createBudget, deleteBudget, updateBudget } from '../services/expense'
import type { BudgetCreateRequest, BudgetUpdateRequest, OptionsResponse } from '../types/expense'

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
    onMutate: async (name) => {
      await queryClient.cancelQueries({ queryKey: ['options'] })
      const previous = queryClient.getQueryData<OptionsResponse>(['options'])
      queryClient.setQueryData<OptionsResponse>(['options'], (old) =>
        old ? { ...old, budgets: old.budgets.filter((b) => b.name !== name) } : old,
      )
      return { previous }
    },
    onError: (_error, _name, context) => {
      if (context?.previous) queryClient.setQueryData(['options'], context.previous)
    },
    onSettled: () => {
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
