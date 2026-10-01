import { useState } from 'react'
import {
  ActionIcon,
  Badge,
  Button,
  Container,
  Group,
  Loader,
  Paper,
  Select,
  Stack,
  Text,
} from '@mantine/core'
import { IconMail, IconRefresh } from '@tabler/icons-react'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { useDiscardEmail, useEmailImports, usePollEmails, useRetryEmailImport } from '../hooks/useEmailImports'
import { PageHeader } from '../components/PageHeader'
import { AppPagination } from '../components/AppPagination'
import ImportEmailModal from '../components/ImportEmailModal'
import EmailRincianModal from '../components/EmailRincianModal'
import { useToast } from '../components/Toast'
import { getErrorMessage } from '../utils/error'
import { formatCurrency } from '../utils/currency'
import type { EmailImport } from '../types/emailImport'

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'Semua' },
  { value: 'PENDING_REVIEW', label: 'Perlu Review' },
  { value: 'SCANNED', label: 'Auto-scan' },
  { value: 'FAILED', label: 'Gagal' },
  { value: 'IMPORTED', label: 'Selesai' },
  { value: 'DISCARDED', label: 'Dibuang' },
]

const STATUS_BADGE: Record<string, { label: string; color: string }> = {
  PENDING_REVIEW: { label: 'Perlu Review', color: 'blue' },
  IMPORTED: { label: 'Selesai', color: 'green' },
  DISCARDED: { label: 'Dibuang', color: 'gray' },
  FAILED: { label: 'Gagal', color: 'red' },
  SCANNED: { label: 'Auto-scan', color: 'teal' },
}

function InboxPage() {
  const [status, setStatus] = useState('ALL')
  const [importing, setImporting] = useState<EmailImport | null>(null)
  const [detail, setDetail] = useState<EmailImport | null>(null)
  const navigate = useNavigate()
  const { data, isPending } = useEmailImports(status)
  const poll = usePollEmails()
  const discard = useDiscardEmail()
  const retry = useRetryEmailImport()
  const toast = useToast()
  const imports = data?.imports ?? []

  const handlePoll = () => {
    poll.mutate(undefined, {
      onSuccess: (result) => {
        toast.success(
          result.count > 0 ? `${result.count} email diproses` : 'Tidak ada email baru',
          { title: 'Inbox diperbarui' },
        )
      },
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  const handleDiscard = (item: EmailImport) => {
    discard.mutate(item.id, {
      onSuccess: () => toast.success('Transaksi dibuang', { title: 'Berhasil' }),
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  const handleRetry = (item: EmailImport) => {
    retry.mutate(item.id, {
      onSuccess: () => toast.success('Email diproses ulang', { title: 'Berhasil' }),
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  return (
    <Container size="sm" px="md" py="lg">
      <Stack gap="lg">
        <PageHeader
          eyebrow="Transaksi Email"
          title="Inbox Email"
          subtitle="Transaksi dari email notifikasi bank. Periksa lalu impor menjadi pengeluaran."
          titleSize="clamp(1.5rem, 5vw, 2rem)"
        />

        <Group align="flex-end" wrap="nowrap">
          <Select
            label="Status"
            data={STATUS_OPTIONS}
            value={status}
            onChange={(v) => setStatus(v ?? 'ALL')}
            size="sm"
            style={{ flex: 1 }}
          />
          <ActionIcon
            variant="light"
            size="lg"
            onClick={handlePoll}
            loading={poll.isPending}
            aria-label="Segarkan"
          >
            <IconRefresh size={18} />
          </ActionIcon>
        </Group>

        {isPending ? (
          <Group justify="center" py="xl">
            <Loader />
          </Group>
        ) : imports.length === 0 ? (
          <Paper withBorder p="xl" radius="md">
            <Stack align="center" gap="sm">
              <IconMail size={40} aria-hidden />
              <Text fw={600}>Tidak ada transaksi email</Text>
              <Text size="sm" c="dimmed" ta="center">
                Ambil email terbaru dari inbox untuk mulai memproses transaksi.
              </Text>
              <Button
                variant="light"
                size="compact-sm"
                leftSection={<IconRefresh size={16} />}
                onClick={handlePoll}
                loading={poll.isPending}
              >
                Segarkan
              </Button>
            </Stack>
          </Paper>
        ) : (
          <>
            <Text size="sm" c="dimmed">
              {imports.length} transaksi
            </Text>
            <AppPagination data={imports}>
              {(pageData) => (
                <Stack gap="sm">
                  {pageData.map((item) => (
                    <Paper key={item.id} withBorder p="md" radius="md">
                      <Stack gap="xs">
                        <Group justify="space-between" align="flex-start" wrap="nowrap">
                          <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                            <Text fw={600} truncate>
                              {item.merchant ?? item.subject}
                            </Text>
                            <Text size="xs" c="dimmed" truncate title={item.sender}>
                              {item.sender}
                            </Text>
                          </Stack>
                          {item.amount != null && (
                            <Text fw={700} style={{ whiteSpace: 'nowrap' }}>
                              {formatCurrency(item.amount)}
                            </Text>
                          )}
                        </Group>

                        <Group gap="xs" wrap="wrap">
                          <Badge size="sm" variant="light">
                            {item.transactionAt
                              ? dayjs(item.transactionAt).format('DD MMM YYYY HH:mm')
                              : dayjs(item.receivedAt).format('DD MMM YYYY HH:mm')}
                          </Badge>
                          <Badge size="sm" variant="light" color={item.parseMethod === 'AI' ? 'grape' : item.parseMethod === 'SCAN' ? 'teal' : 'gray'}>
                            {item.parseMethod}
                          </Badge>
                          <Badge
                            size="sm"
                            variant="light"
                            color={STATUS_BADGE[item.status]?.color ?? 'gray'}
                          >
                            {STATUS_BADGE[item.status]?.label ?? item.status}
                          </Badge>
                          {item.status === 'FAILED' && item.errorMessage && (
                            <Badge size="sm" variant="light" color="red">
                              {item.errorMessage}
                            </Badge>
                          )}
                        </Group>

                        {item.status === 'PENDING_REVIEW' && (
                          <Group justify="flex-end" gap="xs">
                            <Button
                              size="xs"
                              variant="default"
                              onClick={() => handleDiscard(item)}
                              loading={discard.isPending}
                            >
                              Buang
                            </Button>
                            <Button size="xs" onClick={() => setImporting(item)}>
                              Impor
                            </Button>
                          </Group>
                        )}
                        {item.status === 'FAILED' && (
                          <Group justify="flex-end" gap="xs">
                            <Button
                              size="xs"
                              variant="default"
                              onClick={() => handleDiscard(item)}
                              loading={discard.isPending}
                            >
                              Buang
                            </Button>
                            <Button size="xs" variant="light" onClick={() => handleRetry(item)} loading={retry.isPending}>
                              Coba lagi
                            </Button>
                          </Group>
                        )}
                        {item.status === 'SCANNED' && (
                          <Group justify="flex-end" gap="xs">
                            <Button size="xs" variant="light" color="teal" onClick={() => navigate('/scan')}>
                              Lihat di Scan
                            </Button>
                          </Group>
                        )}
                        {item.expenseId && (
                          <Group justify="flex-end" gap="xs">
                            <Button size="xs" variant="light" onClick={() => setDetail(item)}>
                              Rincian
                            </Button>
                          </Group>
                        )}
                      </Stack>
                    </Paper>
                  ))}
                </Stack>
              )}
            </AppPagination>
          </>
        )}
      </Stack>

      {importing && <ImportEmailModal key={importing.id} item={importing} onClose={() => setImporting(null)} />}
      {detail && <EmailRincianModal key={detail.id} item={detail} onClose={() => setDetail(null)} />}
    </Container>
  )
}

export default InboxPage
