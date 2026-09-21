import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { discardEmail, getEmailImports, importEmail, pollEmails, retryEmailImport } from '../services/emailImports'
import type { ExpenseRequest } from '../types/expense'

export function useEmailImports(status: string) {
  return useQuery({
    queryKey: ['email-imports', status],
    queryFn: () => getEmailImports(status),
  })
}

export function useImportEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request, force }: { id: string; request: ExpenseRequest; force?: boolean }) =>
      importEmail(id, request, force),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-imports'] })
      queryClient.invalidateQueries({ queryKey: ['options'] })
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['summary'] })
    },
  })
}

export function useDiscardEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => discardEmail(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-imports'] })
    },
  })
}

export function usePollEmails() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: pollEmails,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-imports'] })
    },
  })
}

export function useRetryEmailImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => retryEmailImport(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-imports'] })
    },
  })
}
