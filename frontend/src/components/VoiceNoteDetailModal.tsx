import { Button, Divider, Group, Loader, Modal, Paper, Stack, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { IconMicrophone } from '@tabler/icons-react'
import dayjs from 'dayjs'
import { useVoiceNoteDetail } from '../hooks/useVoiceNotes'
import { getVoiceNoteAudioUrl } from '../services/voiceNotes'
import { formatCurrency } from '../utils/currency'
import type { Expense } from '../types/expense'

const DATE_TIME_FORMAT = 'DD MMM YYYY HH:mm'

function ExpenseDetail({ expense }: { expense: Expense }) {
  return (
    <Paper withBorder p="sm" radius="md">
      <Stack gap={6}>
        <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
          <Text size="sm" fw={600} style={{ flex: 1, minWidth: 0 }}>
            {expense.name}
          </Text>
          <Text size="sm" fw={700} style={{ whiteSpace: 'nowrap' }}>
            {formatCurrency(expense.amount)}
          </Text>
        </Group>
        <Text size="xs" c="dimmed">
          {dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}
        </Text>
        <Text size="xs" c="dimmed">
          Budget: <Text span fw={600}>{expense.budget}</Text>
        </Text>
        {expense.category ? (
          <Text size="xs" c="dimmed">
            Category: <Text span fw={600}>{expense.category}</Text>
          </Text>
        ) : null}
        {expense.description ? (
          <Text size="xs" c="dimmed" style={{ whiteSpace: 'pre-wrap' }}>
            {expense.description}
          </Text>
        ) : null}
      </Stack>
    </Paper>
  )
}

function VoiceNoteDetailModal({ voiceNoteId, onClose }: { voiceNoteId: string; onClose: () => void }) {
  const { data, isPending, isError, refetch } = useVoiceNoteDetail(voiceNoteId)
  const isMobile = useMediaQuery('(max-width: 48em)')
  const expenses = data?.expenses ?? []
  const total = expenses.reduce((sum, expense) => sum + Number(expense.amount || 0), 0)

  return (
    <Modal
      opened
      onClose={onClose}
      title="Rincian Pengeluaran"
      centered
      fullScreen={isMobile}
      size="md"
      styles={{ body: { maxHeight: isMobile ? undefined : 'calc(100dvh - 140px)', overflowY: 'auto' } }}
    >
      {isPending ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : isError ? (
        <Stack align="center" gap="sm" py="md">
          <Text size="sm" c="red" ta="center">
            Gagal memuat rincian rekaman.
          </Text>
          <Button variant="light" onClick={() => void refetch()}>
            Coba lagi
          </Button>
        </Stack>
      ) : (
        <Stack gap="sm">
          <Paper withBorder p="sm" radius="md">
            <Stack gap={6}>
              <Group gap="xs" wrap="nowrap">
                <IconMicrophone size={18} aria-hidden />
                <Text size="sm" fw={600}>
                  Rekaman asli
                </Text>
              </Group>
              <audio controls preload="metadata" src={getVoiceNoteAudioUrl(voiceNoteId)} style={{ width: '100%' }} />
              {data?.analysis?.transcript ? (
                <Text size="sm" c="dimmed">
                  “{data.analysis.transcript}”
                </Text>
              ) : null}
            </Stack>
          </Paper>

          {expenses.length === 0 ? (
            <Text size="sm" c="dimmed">
              Pengeluaran tidak ditemukan atau sudah dihapus.
            </Text>
          ) : (
            <>
              <Paper withBorder p="sm" radius="md">
                <Group justify="space-between">
                  <Stack gap={2}>
                    <Text size="sm" c="dimmed">
                      Total pengeluaran
                    </Text>
                    <Text size="xs" c="dimmed">
                      {expenses.length} pengeluaran
                    </Text>
                  </Stack>
                  <Text fw={800} fz="xl" c="blue">
                    {formatCurrency(total)}
                  </Text>
                </Group>
              </Paper>

              <Divider label="Detail" labelPosition="left" />

              {expenses.map((expense) => (
                <ExpenseDetail key={expense.id} expense={expense} />
              ))}
            </>
          )}
        </Stack>
      )}
    </Modal>
  )
}

export default VoiceNoteDetailModal
