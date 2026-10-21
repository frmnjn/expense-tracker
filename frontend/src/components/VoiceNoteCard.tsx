import { ActionIcon, Badge, Button, Group, Loader, Paper, Stack, Text } from '@mantine/core'
import { IconMicrophone, IconTrash } from '@tabler/icons-react'
import dayjs from 'dayjs'
import { getVoiceNoteAudioUrl } from '../services/voiceNotes'
import type { VoiceNote } from '../types/expense'

const DATE_TIME_FORMAT = 'DD MMM YYYY HH:mm'

const STATUS_META: Record<string, { label: string; color: string }> = {
  ANALYZING: { label: 'Menunggu AI', color: 'blue' },
  TO_REVIEW: { label: 'Perlu Review', color: 'yellow' },
  SUBMITTED: { label: 'Selesai', color: 'green' },
  ERROR: { label: 'Gagal', color: 'red' },
  NOT_EXPENSE: { label: 'Bukan Pengeluaran', color: 'orange' },
}

function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '-'
  const total = Math.round(ms / 1000)
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}

export function VoiceNoteCard({
  note,
  onReview,
  onViewDetail,
  onRetry,
  onDelete,
}: {
  note: VoiceNote
  onReview: () => void
  onViewDetail: () => void
  onRetry: () => void
  onDelete: () => void
}) {
  const meta = STATUS_META[note.status] ?? { label: note.status, color: 'gray' }
  const retryCount = note.retryCount ?? 0
  const retryMax = note.retryMax ?? 0
  const provider = note.provider === 'GEMINI' ? 'Gemini' : note.provider ?? ''
  const retryLabel = retryCount > 0 ? ` · ${provider ? `${provider} ` : ''}retry ${retryCount}×` : ''
  const retryProgress = `${provider ? `${provider} ` : ''}${retryCount}/${retryMax}`

  return (
    <Paper withBorder p="sm" radius="md" style={{ borderLeft: `4px solid var(--mantine-color-${meta.color}-6)` }}>
      <Stack gap="xs">
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
            <Badge color={meta.color} variant="light" style={{ alignSelf: 'flex-start', maxWidth: '100%' }}>
              {meta.label}
              {retryLabel}
            </Badge>
            <Group gap={6} wrap="nowrap">
              <IconMicrophone size={16} aria-hidden />
              <Text size="sm" fw={600}>
                Rekaman {formatDuration(note.durationMs)}
              </Text>
            </Group>
            <Text size="xs" c="dimmed">
              {dayjs(note.createdAt).format(DATE_TIME_FORMAT)}
            </Text>
          </Stack>
        </Group>

        <audio controls preload="none" src={getVoiceNoteAudioUrl(note.id)} style={{ width: '100%' }} />

        {note.status === 'ANALYZING' && (
          <Group justify="space-between" align="center" wrap="nowrap">
            <Text size="sm" c="dimmed">
              AI sedang menganalisis…
            </Text>
            <Group gap={8} wrap="nowrap">
              {retryMax > 0 && (
                <Text size="xs" c={retryCount > 0 ? 'orange' : 'dimmed'}>
                  {retryProgress}
                </Text>
              )}
              <Loader size="xs" />
            </Group>
          </Group>
        )}

        {note.status === 'TO_REVIEW' && (
          <Group justify="space-between" wrap="nowrap" gap="xs">
            <Button size="sm" variant="light" style={{ flex: 1 }} onClick={onReview}>
              Tinjau
            </Button>
            <ActionIcon variant="light" color="red" size="lg" onClick={onDelete} aria-label="Hapus rekaman">
              <IconTrash size={18} />
            </ActionIcon>
          </Group>
        )}

        {note.status === 'SUBMITTED' && (
          <Button size="compact-sm" variant="light" style={{ alignSelf: 'flex-start' }} onClick={onViewDetail}>
            Lihat rincian
          </Button>
        )}

        {note.status === 'NOT_EXPENSE' && (
          <Group justify="space-between" wrap="nowrap" gap="xs">
            <Text size="sm" c="dimmed">
              Rekaman ini bukan catatan pengeluaran.
            </Text>
            <ActionIcon variant="light" color="red" size="lg" onClick={onDelete} aria-label="Hapus rekaman">
              <IconTrash size={18} />
            </ActionIcon>
          </Group>
        )}

        {note.status === 'ERROR' && (
          <Group justify="space-between" wrap="nowrap" gap="xs">
            <Group gap={6} wrap="nowrap">
              <Button size="compact-sm" variant="light" color="red" onClick={onRetry}>
                Coba lagi
              </Button>
              {retryCount > 0 && (
                <Text size="xs" c="dimmed">
                  {retryProgress}
                </Text>
              )}
            </Group>
            <ActionIcon variant="light" color="red" size="lg" onClick={onDelete} aria-label="Hapus rekaman">
              <IconTrash size={18} />
            </ActionIcon>
          </Group>
        )}
      </Stack>
    </Paper>
  )
}
