import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { isDemoMode } from '../utils/demo'
import { demoTutorialStep, tutorials } from '../utils/tutorialSteps'

const STORAGE_KEY = 'expense-tutorial-v1'
const memorySeen = new Set<string>()

function loadSeen(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    const stored = Array.isArray(parsed)
      ? parsed.filter((value): value is string => typeof value === 'string')
      : []
    return Array.from(new Set([...stored, ...memorySeen]))
  } catch {
    return Array.from(memorySeen)
  }
}

function markSeen(pathname: string) {
  memorySeen.add(pathname)
  try {
    const stored = loadSeen()
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored))
  } catch {
    // storage tidak tersedia: tutorial tetap bisa dipakai lewat tombol bantuan
  }
}

export function useTutorial() {
  const { pathname } = useLocation()
  const tutorial = tutorials[pathname]
  const steps = useMemo(
    () =>
      tutorial
        ? isDemoMode && pathname === '/catat'
          ? [...tutorial.steps, demoTutorialStep]
          : tutorial.steps
        : [],
    [tutorial, pathname],
  )
  const [opened, setOpened] = useState(false)
  const [stepIndex, setStepIndex] = useState(0)

  useEffect(() => {
    if (!tutorial) {
      // oxlint-disable-next-line react/set-state-in-effect -- sinkronisasi dengan route aktif
      setOpened(false)
      return
    }
    if (loadSeen().includes(pathname)) return
    setStepIndex(0)
    setOpened(true)
  }, [pathname, tutorial])

  const open = useCallback(() => {
    if (!tutorial) return
    setStepIndex(0)
    setOpened(true)
  }, [tutorial])

  const close = useCallback(() => {
    markSeen(pathname)
    setOpened(false)
  }, [pathname])

  const next = useCallback(() => {
    setStepIndex((current) => Math.min(current + 1, steps.length - 1))
  }, [steps.length])

  const back = useCallback(() => {
    setStepIndex((current) => Math.max(current - 1, 0))
  }, [])

  return {
    opened,
    stepIndex,
    steps,
    label: tutorial?.label ?? '',
    canOpen: !!tutorial,
    open,
    close,
    next,
    back,
  }
}
