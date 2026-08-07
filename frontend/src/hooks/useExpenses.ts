import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteExpense, getExpenses, getPeriods, getSummary, updateExpense } from '../services/expense'
import type { ExpenseRequest } from '../types/expense'

export function usePeriods() {
  return useQuery({
    queryKey: ['periods'],
    queryFn: getPeriods,
  })
}

export function useExpenses(period: string | null) {
  return useQuery({
    queryKey: ['expenses', period],
    queryFn: () => getExpenses(period ?? ''),
    enabled: !!period,
  })
}

export function useSummary(period: string | null) {
  return useQuery({
    queryKey: ['summary', period],
    queryFn: () => getSummary(period ?? ''),
    enabled: !!period,
  })
}

export function useUpdateExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: ExpenseRequest }) => updateExpense(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}

export function useDeleteExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteExpense(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}
