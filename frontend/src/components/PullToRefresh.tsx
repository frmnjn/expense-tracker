import { useEffect, useRef, useState, type RefObject } from 'react'
import { Box, Loader } from '@mantine/core'
import { useQueryClient } from '@tanstack/react-query'

const THRESHOLD = 64
const MAX_PULL = 90

/**
 * Tarik-untuk-menyegarkan: saat scroll container di posisi paling atas lalu
 * ditarik ke bawah, semua query aktif di-invalidate (refetch).
 * Hanya aktif pada perangkat sentuh.
 */
export function PullToRefresh({ scrollRef }: { scrollRef: RefObject<HTMLElement | null> }) {
  const queryClient = useQueryClient()
  const [distance, setDistance] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const distanceRef = useRef(0)
  const refreshingRef = useRef(false)

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    let startY = 0
    let tracking = false

    const onStart = (event: TouchEvent) => {
      tracking = el.scrollTop <= 0 && !refreshingRef.current
      if (tracking) startY = event.touches[0].clientY
    }

    const onMove = (event: TouchEvent) => {
      if (!tracking) return
      const dy = event.touches[0].clientY - startY
      if (dy <= 0) {
        tracking = false
        distanceRef.current = 0
        setDistance(0)
        return
      }
      distanceRef.current = Math.min(dy * 0.5, MAX_PULL)
      setDistance(distanceRef.current)
    }

    const onEnd = async () => {
      if (!tracking) return
      tracking = false
      if (distanceRef.current > THRESHOLD) {
        refreshingRef.current = true
        setRefreshing(true)
        setDistance(THRESHOLD)
        try {
          await queryClient.invalidateQueries()
        } finally {
          refreshingRef.current = false
          setRefreshing(false)
          distanceRef.current = 0
          setDistance(0)
        }
      } else {
        distanceRef.current = 0
        setDistance(0)
      }
    }

    el.addEventListener('touchstart', onStart, { passive: true })
    el.addEventListener('touchmove', onMove, { passive: true })
    el.addEventListener('touchend', onEnd)
    el.addEventListener('touchcancel', onEnd)
    return () => {
      el.removeEventListener('touchstart', onStart)
      el.removeEventListener('touchmove', onMove)
      el.removeEventListener('touchend', onEnd)
      el.removeEventListener('touchcancel', onEnd)
    }
  }, [scrollRef, queryClient])

  if (distance <= 0) return null

  return (
    <Box
      style={{
        position: 'fixed',
        top: 64,
        left: 0,
        right: 0,
        display: 'flex',
        justifyContent: 'center',
        pointerEvents: 'none',
        zIndex: 120,
      }}
    >
      <Loader
        size="sm"
        style={{
          marginTop: Math.max(0, distance - 28),
          opacity: refreshing || distance > 12 ? 1 : 0,
        }}
      />
    </Box>
  )
}
