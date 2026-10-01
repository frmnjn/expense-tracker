import { useEffect, type RefObject } from 'react'
import { useLocation } from 'react-router-dom'

// Posisi scroll per-pathname, in-memory selama sesi berjalan.
const positions = new Map<string, number>()

const MOBILE_QUERY = '(max-width: 47.99em)'

/**
 * Simpan & pulihkan posisi scroll tiap halaman. Di mobile scroll container-nya
 * `.mantine-AppShell-main`; di desktop scroll ada di window.
 */
export function useScrollMemory(ref: RefObject<HTMLElement | null>) {
  const { pathname } = useLocation()

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const isMobile = typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches
    const target = positions.get(pathname) ?? 0
    let raf = 0
    let attempts = 0

    const scrollTo = (y: number) => {
      if (isMobile) el.scrollTo(0, y)
      else window.scrollTo(0, y)
    }

    const restore = () => {
      const max = isMobile
        ? el.scrollHeight - el.clientHeight
        : document.documentElement.scrollHeight - window.innerHeight
      if (max >= target || attempts > 30) {
        scrollTo(Math.min(target, Math.max(0, max)))
        return
      }
      attempts++
      raf = requestAnimationFrame(restore)
    }

    if (target > 0) raf = requestAnimationFrame(restore)
    else scrollTo(0)

    return () => {
      cancelAnimationFrame(raf)
      positions.set(pathname, isMobile ? el.scrollTop : window.scrollY)
    }
  }, [pathname, ref])
}
