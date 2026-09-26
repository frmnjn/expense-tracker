import { Badge, Divider, Group, Loader, Modal, Paper, Stack, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import dayjs from 'dayjs'
import type { ReactNode } from 'react'
import { useExpense } from '../hooks/useExpenses'
import { formatCurrency } from '../utils/currency'
import type { EmailImport } from '../types/emailImport'

const DATE_TIME_FORMAT = 'DD MMM YYYY HH:mm'

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text size="sm" fw={600} ta="right" style={{ flex: 1, minWidth: 0, wordBreak: 'break-word' }}>
        {children}
      </Text>
    </Group>
  )
}

function EmailRincianModal({ item, onClose }: { item: EmailImport; onClose: () => void }) {
  const { data: expense, isPending } = useExpense(item.expenseId ?? null)
  const isMobile = useMediaQuery('(max-width: 48em)')

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
      {isPending && item.expenseId ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : expense ? (
        <Stack gap="sm">
          <Paper withBorder p="sm" radius="md">
            <Stack gap={4}>
              <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
                <Text size="sm" fw={600} style={{ flex: 1, minWidth: 0 }}>
                  {expense.name}
                </Text>
                <Text size="sm" fw={700} style={{ whiteSpace: 'nowrap' }}>
                  {formatCurrency(expense.amount)}
                </Text>
              </Group>
              <Text size="xs" c="dimmed" truncate title={item.sender}>
                {item.sender}
              </Text>
              <Text size="xs" c="dimmed" truncate title={item.subject}>
                {item.subject}
              </Text>
            </Stack>
          </Paper>

          <Paper withBorder p="sm" radius="md">
            <Stack gap={8}>
              <DetailRow label="Budget">
                <Badge size="sm" variant="light" color="gray">
                  {expense.budget}
                </Badge>
              </DetailRow>
              <DetailRow label="Nominal">{formatCurrency(expense.amount)}</DetailRow>
              <DetailRow label="Waktu transaksi">{dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}</DetailRow>
              {expense.description ? (
                <DetailRow label="Deskripsi">{expense.description}</DetailRow>
              ) : null}
            </Stack>
          </Paper>

          <Divider label="Email" labelPosition="left" />

          <Paper withBorder p="sm" radius="md">
            <Stack gap={8}>
              <DetailRow label="Diterima">{dayjs(item.receivedAt).format(DATE_TIME_FORMAT)}</DetailRow>
              <DetailRow label="Metode parse">
                <Badge size="sm" variant="light" color={item.parseMethod === 'AI' ? 'grape' : 'gray'}>
                  {item.parseMethod}
                </Badge>
              </DetailRow>
            </Stack>
          </Paper>
        </Stack>
      ) : (
        <Text size="sm" c="dimmed">
          Pengeluaran tidak ditemukan.
        </Text>
      )}
    </Modal>
  )
}

export default EmailRincianModal