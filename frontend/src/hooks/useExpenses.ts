import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteExpense,
  getExpense,
  getExpenses,
  getPeriods,
  getSummary,
  getTrend,
  updateExpense,
} from '../services/expense'
import type { ExpensesResponse, ExpenseRequest } from '../types/expense'

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

export function useExpense(id: string | null | undefined) {
  return useQuery({
    queryKey: ['expense', id],
    queryFn: () => getExpense(id ?? ''),
    enabled: !!id,
  })
}

export function useSummary(period: string | null) {
  return useQuery({
    queryKey: ['summary', period],
    queryFn: () => getSummary(period ?? ''),
    enabled: !!period,
  })
}

export function useTrend(months = 3) {
  return useQuery({
    queryKey: ['trend', months],
    queryFn: () => getTrend(months),
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
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['expenses'] })
      const snapshots = queryClient.getQueriesData<ExpensesResponse>({ queryKey: ['expenses'] })
      queryClient.setQueriesData<ExpensesResponse>({ queryKey: ['expenses'] }, (old) =>
        old ? { ...old, expenses: old.expenses.filter((e) => e.id !== id) } : old,
      )
      return { snapshots }
    },
    onError: (_error, _id, context) => {
      context?.snapshots.forEach(([key, data]) => queryClient.setQueryData(key, data))
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['options'] })
      queryClient.invalidateQueries({ queryKey: ['summary'] })
    },
  })
}
