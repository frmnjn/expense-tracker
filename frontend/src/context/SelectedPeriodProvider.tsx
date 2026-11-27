import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { usePeriods } from '../hooks/useExpenses'
import { SelectedPeriodContext } from './SelectedPeriodContext'

const RESUME_TIMEOUT_MS = 30 * 60 * 1000

export function SelectedPeriodProvider({ children }: { children: ReactNode }) {
  const { data } = usePeriods()
  const currentPeriod = data?.currentPeriod ?? ''
  const [selected, setSelected] = useState<string | null>(null)
  const hiddenAtRef = useRef<number | null>(null)

  const periodOptions = useMemo(() => {
    const set = new Set<string>()
    if (currentPeriod) set.add(currentPeriod)
    for (const period of data?.periods ?? []) set.add(period)
    return Array.from(set)
  }, [currentPeriod, data?.periods])

  const defaultPeriod = currentPeriod || periodOptions[0] || null
  const period = selected && periodOptions.includes(selected) ? selected : defaultPeriod
  const setPeriod = useCallback((next: string | null) => setSelected(next), [])

  useEffect(() => {
    const handleVisibility = () => {
      if (document.visibilityState === 'hidden') {
        hiddenAtRef.current = Date.now()
        return
      }
      const hiddenAt = hiddenAtRef.current
      hiddenAtRef.current = null
      if (hiddenAt !== null && Date.now() - hiddenAt >= RESUME_TIMEOUT_MS) {
        setSelected(null)
      }
    }
    document.addEventListener('visibilitychange', handleVisibility)
    return () => document.removeEventListener('visibilitychange', handleVisibility)
  }, [])

  const value = useMemo(
    () => ({ period, setPeriod, currentPeriod, periodOptions }),
    [period, setPeriod, currentPeriod, periodOptions],
  )

  return <SelectedPeriodContext.Provider value={value}>{children}</SelectedPeriodContext.Provider>
}
