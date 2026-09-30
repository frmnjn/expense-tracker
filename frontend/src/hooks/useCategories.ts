import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createCategory, deleteCategory, updateCategory } from '../services/expense'
import type { CategoryCreateRequest, CategoryUpdateRequest } from '../types/expense'

export function useCreateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CategoryCreateRequest) => createCategory(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}

export function useUpdateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: CategoryUpdateRequest }) =>
      updateCategory(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}

export function useDeleteCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}
