import { useQuery } from '@tanstack/react-query'
import { getOptions } from '../services/expense'

export function useOptions() {
  return useQuery({
    queryKey: ['options'],
    queryFn: getOptions,
  })
}
