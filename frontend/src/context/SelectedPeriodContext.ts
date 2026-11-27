import { createContext, useContext } from 'react'

export interface SelectedPeriodContextValue {
  period: string | null
  setPeriod: (period: string | null) => void
  currentPeriod: string
  periodOptions: string[]
}

export const SelectedPeriodContext = createContext<SelectedPeriodContextValue | null>(null)

export function useSelectedPeriod(): SelectedPeriodContextValue {
  const context = useContext(SelectedPeriodContext)
  if (!context) {
    throw new Error('useSelectedPeriod harus dipakai di dalam SelectedPeriodProvider')
  }
  return context
}
