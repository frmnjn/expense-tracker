import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getReports, regenerateReports } from '../services/reports'

export function useReports() {
  return useQuery({
    queryKey: ['reports'],
    queryFn: getReports,
  })
}

export function useRegenerateReports() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: regenerateReports,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
    },
  })
}
