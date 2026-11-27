import { useEffect, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { ActionIcon, Box, Group, Modal, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { IconRestore, IconRotateClockwise, IconZoomIn, IconZoomOut } from '@tabler/icons-react'

const MIN_SCALE = 1
const MAX_SCALE = 5

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

interface Point {
  x: number
  y: number
}

function distance(a: Point, b: Point) {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

export function ImageViewerModal({
  opened,
  onClose,
  src,
  alt,
  title,
}: {
  opened: boolean
  onClose: () => void
  src: string
  alt?: string
  title?: string
}) {
  const isMobile = useMediaQuery('(max-width: 48em)')
  const [scale, setScale] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [position, setPosition] = useState<Point>({ x: 0, y: 0 })
  const pointersRef = useRef<Map<number, Point>>(new Map())
  const panStartRef = useRef<{ pointer: Point; position: Point } | null>(null)
  const pinchStartRef = useRef<{ distance: number; scale: number } | null>(null)

  const reset = () => {
    setScale(1)
    setRotation(0)
    setPosition({ x: 0, y: 0 })
    pointersRef.current.clear()
    panStartRef.current = null
    pinchStartRef.current = null
  }

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- reset viewer setiap kali dibuka/gambar berganti
    if (opened) reset()
  }, [opened, src])

  const applyScale = (next: number) => {
    const clamped = clamp(next, MIN_SCALE, MAX_SCALE)
    setScale(clamped)
    if (clamped === MIN_SCALE) setPosition({ x: 0, y: 0 })
  }

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    pointersRef.current.set(event.pointerId, { x: event.clientX, y: event.clientY })
    if (pointersRef.current.size === 1) {
      panStartRef.current = { pointer: { x: event.clientX, y: event.clientY }, position }
    } else if (pointersRef.current.size === 2) {
      const [a, b] = Array.from(pointersRef.current.values())
      pinchStartRef.current = { distance: distance(a, b), scale }
      panStartRef.current = null
    }
  }

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!pointersRef.current.has(event.pointerId)) return
    pointersRef.current.set(event.pointerId, { x: event.clientX, y: event.clientY })

    if (pointersRef.current.size >= 2 && pinchStartRef.current) {
      const [a, b] = Array.from(pointersRef.current.values())
      const next = pinchStartRef.current.scale * (distance(a, b) / pinchStartRef.current.distance)
      applyScale(next)
      return
    }

    if (pointersRef.current.size === 1 && panStartRef.current && scale > 1) {
      const start = panStartRef.current
      setPosition({
        x: start.position.x + event.clientX - start.pointer.x,
        y: start.position.y + event.clientY - start.pointer.y,
      })
    }
  }

  const handlePointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    pointersRef.current.delete(event.pointerId)
    if (pointersRef.current.size < 2) pinchStartRef.current = null
    if (pointersRef.current.size === 0) {
      panStartRef.current = null
      return
    }
    const [remaining] = Array.from(pointersRef.current.values())
    panStartRef.current = { pointer: remaining, position }
  }

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={title ?? 'Foto'}
      fullScreen={isMobile}
      centered={!isMobile}
      size="xl"
      zIndex={3000}
      styles={
        isMobile
          ? { body: { height: 'calc(100dvh - 60px)', display: 'flex', flexDirection: 'column' } }
          : undefined
      }
    >
      <Box
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onWheel={(event) => {
          event.preventDefault()
          applyScale(scale * (event.deltaY < 0 ? 1.2 : 1 / 1.2))
        }}
        onDoubleClick={() => {
          if (scale > 1) {
            applyScale(1)
          } else {
            setScale(2)
          }
        }}
        style={{
          flex: 1,
          minHeight: 0,
          height: isMobile ? undefined : '70vh',
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--mantine-color-dark-9)',
          borderRadius: 8,
          touchAction: 'none',
          cursor: scale > 1 ? 'grab' : 'zoom-in',
        }}
      >
        <img
          src={src}
          alt={alt ?? title ?? 'Foto'}
          draggable={false}
          style={{
            maxWidth: '100%',
            maxHeight: '100%',
            objectFit: 'contain',
            userSelect: 'none',
            pointerEvents: 'none',
            transform: `translate(${position.x}px, ${position.y}px) scale(${scale}) rotate(${rotation}deg)`,
          }}
        />
      </Box>

      <Group justify="center" gap="xs" mt="sm" wrap="nowrap">
        <ActionIcon variant="light" aria-label="Zoom out" onClick={() => applyScale(scale / 1.25)}>
          <IconZoomOut size={18} />
        </ActionIcon>
        <ActionIcon variant="light" aria-label="Reset" onClick={reset}>
          <IconRestore size={18} />
        </ActionIcon>
        <ActionIcon variant="light" aria-label="Zoom in" onClick={() => applyScale(scale * 1.25)}>
          <IconZoomIn size={18} />
        </ActionIcon>
        <ActionIcon variant="light" aria-label="Putar kiri" onClick={() => setRotation((value) => value - 90)}>
          <IconRotateClockwise size={18} style={{ transform: 'scaleX(-1)' }} />
        </ActionIcon>
        <ActionIcon variant="light" aria-label="Putar kanan" onClick={() => setRotation((value) => value + 90)}>
          <IconRotateClockwise size={18} />
        </ActionIcon>
        <Text size="xs" c="dimmed" w={48} ta="center">
          {Math.round(scale * 100)}%
        </Text>
      </Group>
    </Modal>
  )
}
