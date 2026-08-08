import { useQuery } from '@tanstack/react-query'
import { getInvoices } from '../services/expense'

export function useInvoices(dateTime: string | null) {
  return useQuery({
    queryKey: ['invoices', dateTime],
    queryFn: () => getInvoices(dateTime ?? ''),
    enabled: !!dateTime,
  })
}
