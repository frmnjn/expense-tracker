import { ActionIcon, Badge, Box, Button, Group, Loader, Paper, Stack, Text } from '@mantine/core'
import dayjs from 'dayjs'
import { InvoiceThumb } from './InvoiceThumb'
import { getInvoicePhotoUrl } from '../services/expense'
import type { Invoice } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'

const STATUS_META: Record<string, { label: string; color: string }> = {
  ANALYZING: { label: 'Menunggu AI', color: 'blue' },
  TO_REVIEW: { label: 'Perlu Review', color: 'yellow' },
  SUBMITTED: { label: 'Selesai', color: 'green' },
  ERROR: { label: 'Gagal', color: 'red' },
  NOT_INVOICE: { label: 'Bukan Invoice', color: 'orange' },
}

export function InvoiceCard({
  invoice,
  onPreview,
  onReview,
  onViewDetail,
  onDelete,
  onRetry,
}: {
  invoice: Invoice
  onPreview: () => void
  onReview: () => void
  onViewDetail: () => void
  onDelete: () => void
  onRetry: () => void
}) {
  const meta = STATUS_META[invoice.status] ?? { label: invoice.status, color: 'gray' }
  const retryCount = invoice.retryCount ?? 0
  const retryMax = invoice.retryMax ?? 0
  const isRetrying = retryMax > 0 && retryCount > 0
  const retryLabel = retryCount > 0 ? ` · retry ${retryCount}×` : ''

  return (
    <Paper withBorder p="sm" radius="md" style={{ borderLeft: `4px solid var(--mantine-color-${meta.color}-6)` }}>
      <Group align="flex-start" wrap="nowrap" gap="sm">
        <Box w={88} style={{ flexShrink: 0 }}>
          <InvoiceThumb type={invoice.type} url={getInvoicePhotoUrl(invoice.id)} alt={invoice.name} h={92} onClick={onPreview} />
        </Box>

        <Stack gap={6} style={{ flex: 1, minWidth: 0 }}>
          <Group justify="space-between" align="center" wrap="nowrap" gap="xs">
            <Badge color={meta.color} variant="light">
              {meta.label}
              {retryLabel}
            </Badge>
            <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
              {dayjs(invoice.createdAt).format(DATE_TIME_FORMAT)}
            </Text>
          </Group>

          <Text size="sm" fw={600} truncate title={invoice.name}>
            {invoice.name?.trim() || 'Struk'}
          </Text>

          {invoice.status === 'ANALYZING' && (
            <Group justify="space-between" align="center" wrap="nowrap">
              <Text size="sm" c="dimmed">
                Menunggu AI…
              </Text>
              <Group gap={8} wrap="nowrap">
                {retryMax > 0 && (
                  <Text size="xs" c={isRetrying ? 'orange' : 'dimmed'}>
                    {retryCount}/{retryMax}
                  </Text>
                )}
                <Loader size="xs" />
              </Group>
            </Group>
          )}

          {invoice.status === 'TO_REVIEW' && (
            <Group justify="space-between" wrap="nowrap" gap="xs">
              <Button size="sm" variant="light" style={{ flex: 1 }} onClick={onReview}>
                Review
              </Button>
              <ActionIcon variant="light" color="red" size="lg" onClick={onDelete} aria-label="Hapus struk">
                🗑
              </ActionIcon>
            </Group>
          )}

          {invoice.status === 'SUBMITTED' && (
            <Group justify="space-between" wrap="nowrap" gap="xs">
              <Button size="compact-sm" variant="light" onClick={onPreview}>
                Lihat struk
              </Button>
              <Button size="compact-sm" variant="light" onClick={onViewDetail}>
                Lihat rincian
              </Button>
            </Group>
          )}

          {invoice.status === 'NOT_INVOICE' && (
            <Group justify="space-between" wrap="nowrap" gap="xs">
              <Text size="xs" c="dimmed">
                File ini bukan struk
              </Text>
              <ActionIcon variant="light" color="red" size="lg" onClick={onDelete} aria-label="Hapus struk">
                🗑
              </ActionIcon>
            </Group>
          )}

          {invoice.status === 'ERROR' && (
            <Group justify="space-between" wrap="nowrap" gap="xs">
              <Group gap={6} wrap="nowrap">
                <Button size="compact-sm" variant="light" color="red" onClick={onRetry}>
                  Coba lagi
                </Button>
                {isRetrying && (
                  <Text size="xs" c="dimmed">
                    {retryCount}/{retryMax}
                  </Text>
                )}
              </Group>
              <ActionIcon variant="light" color="red" size="lg" onClick={onDelete} aria-label="Hapus struk">
                🗑
              </ActionIcon>
            </Group>
          )}
        </Stack>
      </Group>
    </Paper>
  )
}
