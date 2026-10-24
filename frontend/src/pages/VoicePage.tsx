import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Container,
  Group,
  Loader,
  Modal,
  Paper,
  Progress,
  Select,
  Stack,
  Text,
} from '@mantine/core'
import { IconMicrophone, IconSend, IconTrash } from '@tabler/icons-react'
import dayjs from 'dayjs'
import { PageHeader } from '../components/PageHeader'
import { VoiceRecorder } from '../components/VoiceRecorder'
import { VoiceNoteCard } from '../components/VoiceNoteCard'
import { ErrorState } from '../components/ErrorState'
import VoiceReviewModal from '../components/VoiceReviewModal'
import VoiceNoteDetailModal from '../components/VoiceNoteDetailModal'
import { useToast } from '../components/Toast'
import { useDeleteVoiceNote, useRetryVoiceNote, useUploadVoiceNote, useVoiceNotes } from '../hooks/useVoiceNotes'
import { usePeriods } from '../hooks/useExpenses'
import { currentPeriod as fallbackCurrentPeriod } from '../utils/period'
import { getErrorMessage } from '../utils/error'
import type { VoiceNote } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'

interface PendingRecording {
  file: File
  url: string
  durationMs: number
}

function VoicePage() {
  const { data: periodsData } = usePeriods()
  const runningPeriod = periodsData?.currentPeriod || fallbackCurrentPeriod()
  const [period, setPeriod] = useState<string | null>(null)
  const activePeriod = period ?? runningPeriod
  const { data, isPending, isError, refetch } = useVoiceNotes(activePeriod)
  const upload = useUploadVoiceNote()
  const retry = useRetryVoiceNote()
  const remove = useDeleteVoiceNote()
  const toast = useToast()

  const [pending, setPending] = useState<PendingRecording | null>(null)
  const [reviewId, setReviewId] = useState<string | null>(null)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<VoiceNote | null>(null)

  useEffect(() => {
    return () => {
      if (pending) URL.revokeObjectURL(pending.url)
    }
  }, [pending])

  const periodOptions = useMemo(() => {
    const set = new Set<string>([runningPeriod, ...(periodsData?.periods ?? [])])
    return Array.from(set).map((value) => ({ value, label: value }))
  }, [periodsData, runningPeriod])

  const handleRecorded = (file: File, durationMs: number) => {
    const url = URL.createObjectURL(file)
    setPending({ file, url, durationMs })
  }

  const discardPending = () => {
    setPending(null)
  }

  const submitPending = () => {
    if (!pending || upload.isPending) return
    upload.mutate(
      {
        file: pending.file,
        dateTime: dayjs().format(DATE_TIME_FORMAT),
        durationMs: pending.durationMs,
      },
      {
        onSuccess: (result) => {
          setPending(null)
          if (result.voiceNoteId) {
            toast.success('Rekaman dikirim, menunggu analisis AI', { title: 'Terkirim' })
          } else {
            toast.error('Rekaman gagal diproses', { title: 'Gagal' })
          }
        },
        onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal upload rekaman' }),
      },
    )
  }

  const handleRetryAnalysis = (id: string) => {
    retry.mutate(id, {
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  const handleDelete = () => {
    if (!deleting) return
    remove.mutate(deleting.id, {
      onSuccess: () => {
        toast.success('Rekaman dihapus', { title: 'Berhasil' })
        setDeleting(null)
      },
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  const notes = data?.voiceNotes ?? []

  return (
    <Container size="sm" px="md" py="lg" pb={{ base: 'calc(96px + env(safe-area-inset-bottom, 0px))', sm: 'lg' }}>
      <Stack gap="lg">
        <PageHeader
          eyebrow="Voice Assistant"
          title="Catat dengan Suara"
          subtitle="Rekam ucapan, biarkan AI mengisi detail pengeluaran, lalu tinjau sebelum disimpan."
          titleSize="clamp(1.5rem, 5vw, 2rem)"
        />

        <VoiceRecorder onRecorded={handleRecorded} disabled={!!pending} />

        {pending && (
          <Paper withBorder p="sm" radius="md">
            <Stack gap="xs">
              <Text size="sm" fw={600}>
                Rekaman siap
              </Text>
              <Text size="xs" c="dimmed">
                Dengarkan dulu. Kirim ke AI bila sudah bagus, atau hapus untuk merekam ulang.
              </Text>
              <audio controls preload="metadata" src={pending.url} style={{ width: '100%' }} />
              {upload.isPending ? (
                <>
                  <Group justify="space-between" mb={4}>
                    <Text size="sm">Mengunggah rekaman…</Text>
                    <Text size="sm" c="dimmed">
                      {upload.progress}%
                    </Text>
                  </Group>
                  <Progress value={upload.progress} striped animated />
                </>
              ) : (
                <Group grow>
                  <Button variant="light" color="red" leftSection={<IconTrash size={16} />} onClick={discardPending}>
                    Hapus
                  </Button>
                  <Button leftSection={<IconSend size={16} />} onClick={submitPending}>
                    Kirim ke AI
                  </Button>
                </Group>
              )}
            </Stack>
          </Paper>
        )}

        <Select
          label="Periode"
          data={periodOptions}
          value={activePeriod}
          onChange={setPeriod}
          searchable
          size="sm"
        />

        {isPending ? (
          <Group justify="center" py="xl">
            <Loader />
          </Group>
        ) : isError ? (
          <ErrorState message="Gagal memuat rekaman." onRetry={() => void refetch()} />
        ) : notes.length === 0 ? (
          <Paper withBorder p="xl" radius="md">
            <Stack align="center" gap={4}>
              <IconMicrophone size={40} aria-hidden />
              <Text fw={600}>Belum ada rekaman</Text>
              <Text size="sm" c="dimmed" ta="center">
                Tekan tombol rekam di atas untuk mencatat pengeluaran dengan suara.
              </Text>
            </Stack>
          </Paper>
        ) : (
          <Stack gap="sm">
            <Text size="sm" c="dimmed">
              {notes.length} rekaman
            </Text>
            {notes.map((note) => (
              <VoiceNoteCard
                key={note.id}
                note={note}
                onReview={() => setReviewId(note.id)}
                onViewDetail={() => setDetailId(note.id)}
                onRetry={() => handleRetryAnalysis(note.id)}
                onDelete={() => setDeleting(note)}
              />
            ))}
          </Stack>
        )}

        <Alert color="blue" variant="light">
          <Text size="sm">
            Rekaman disimpan dan hanya bisa dianalisis jika browser diizinkan mengakses mikrofon (HTTPS atau localhost).
          </Text>
        </Alert>
      </Stack>

      {reviewId && (
        <VoiceReviewModal
          voiceNoteId={reviewId}
          onClose={() => setReviewId(null)}
          onSubmitted={() => setReviewId(null)}
        />
      )}

      {detailId && <VoiceNoteDetailModal voiceNoteId={detailId} onClose={() => setDetailId(null)} />}

      <Modal opened={!!deleting} onClose={() => setDeleting(null)} title="Hapus rekaman?" centered>
        <Stack>
          <Text size="sm">Rekaman dan file audio akan dihapus permanen. Lanjutkan?</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setDeleting(null)}>
              Batal
            </Button>
            <Button color="red" onClick={handleDelete} loading={remove.isPending}>
              Hapus
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Container>
  )
}

export default VoicePage
