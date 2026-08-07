import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createTopUp, getTopUps } from '../services/expense'
import type { TopUpRequest } from '../types/expense'

export function useTopUps() {
  return useQuery({
    queryKey: ['topups'],
    queryFn: getTopUps,
  })
}

export function useCreateTopUp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: TopUpRequest) => createTopUp(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['topups'] })
      queryClient.invalidateQueries({ queryKey: ['options'] })
    },
  })
}
