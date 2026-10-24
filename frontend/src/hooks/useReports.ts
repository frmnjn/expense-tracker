import { useQuery } from '@tanstack/react-query'
import { getReports } from '../services/reports'

export function useReports() {
  return useQuery({
    queryKey: ['reports'],
    queryFn: getReports,
  })
}
