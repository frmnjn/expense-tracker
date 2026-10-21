import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Group, Paper, Stack, Text } from '@mantine/core'
import { IconMicrophone, IconPlayerStopFilled } from '@tabler/icons-react'

const MAX_DURATION_MS = 60_000
const MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']

function extensionFor(mime: string): string {
  const normalized = mime.split(';')[0].trim().toLowerCase()
  if (normalized.includes('mp4')) return 'mp4'
  if (normalized.includes('ogg')) return 'ogg'
  if (normalized.includes('mpeg')) return 'mp3'
  if (normalized.includes('wav')) return 'wav'
  return 'webm'
}

function formatDuration(ms: number): string {
  const total = Math.floor(ms / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export function VoiceRecorder({
  onRecorded,
  disabled,
}: {
  onRecorded: (file: File, durationMs: number) => void
  disabled?: boolean
}) {
  const [recording, setRecording] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const startedAtRef = useRef(0)
  const intervalRef = useRef<number | null>(null)
  const stopTimeoutRef = useRef<number | null>(null)
  const onRecordedRef = useRef(onRecorded)
  onRecordedRef.current = onRecorded

  const cleanup = () => {
    if (intervalRef.current !== null) {
      window.clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    if (stopTimeoutRef.current !== null) {
      window.clearTimeout(stopTimeoutRef.current)
      stopTimeoutRef.current = null
    }
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
  }

  useEffect(() => cleanup, [])

  const start = async () => {
    setError(null)
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('Browser ini tidak mendukung perekaman suara.')
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const preferred = MIME_CANDIDATES.find((type) => MediaRecorder.isTypeSupported(type))
      const recorder = preferred ? new MediaRecorder(stream, { mimeType: preferred }) : new MediaRecorder(stream)
      const mime = recorder.mimeType || preferred || 'audio/webm'

      chunksRef.current = []
      streamRef.current = stream
      recorderRef.current = recorder
      startedAtRef.current = Date.now()
      setElapsed(0)
      setRecording(true)

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunksRef.current.push(event.data)
      }
      recorder.onstop = () => {
        const durationMs = Math.min(Date.now() - startedAtRef.current, MAX_DURATION_MS)
        const blob = new Blob(chunksRef.current, { type: mime })
        chunksRef.current = []
        cleanup()
        setRecording(false)
        setElapsed(durationMs)
        if (blob.size > 0) {
          const extension = extensionFor(mime)
          const file = new File([blob], `voice-${Date.now()}.${extension}`, { type: mime.split(';')[0] })
          onRecordedRef.current(file, durationMs)
        }
      }
      recorder.start()
      intervalRef.current = window.setInterval(() => {
        setElapsed(Date.now() - startedAtRef.current)
      }, 200)
      stopTimeoutRef.current = window.setTimeout(() => {
        if (recorder.state !== 'inactive') recorder.stop()
      }, MAX_DURATION_MS)
    } catch {
      cleanup()
      setRecording(false)
      setError('Tidak bisa mengakses mikrofon. Izinkan akses mikrofon dan gunakan HTTPS atau localhost.')
    }
  }

  const stop = () => {
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop()
    }
  }

  return (
    <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="md">
      <Stack gap="sm" align="center">
        {recording ? (
          <>
            <Text fz={42} fw={700} ff="monospace" lh={1}>
              {formatDuration(elapsed)}
            </Text>
            <Text size="sm" c="dimmed">
              Maksimal 60 detik. Tekan stop bila selesai.
            </Text>
            <Button
              color="red"
              size="lg"
              radius="xl"
              leftSection={<IconPlayerStopFilled size={20} />}
              onClick={stop}
              fullWidth
            >
              Stop
            </Button>
          </>
        ) : (
          <>
            <Button
              color="red"
              size="xl"
              radius="xl"
              leftSection={<IconMicrophone size={24} />}
              onClick={() => void start()}
              disabled={disabled}
              fullWidth
            >
              Rekam Suara
            </Button>
            <Text size="sm" c="dimmed" ta="center">
              Contoh: “Belanja sayur di tukang sayur komplek hari ini jam 3 sore lima puluh ribu.”
            </Text>
          </>
        )}
        {error && (
          <Alert color="red" variant="light" w="100%">
            <Group gap="xs" wrap="nowrap" align="flex-start">
              <Text size="sm">{error}</Text>
            </Group>
          </Alert>
        )}
      </Stack>
    </Paper>
  )
}
