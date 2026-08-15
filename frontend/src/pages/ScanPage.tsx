import { useMemo, useRef, useState } from 'react'
import {
  Badge,
  Button,
  Container,
  Group,
  Image,
  Loader,
  Modal,
  Paper,
  Progress,
  Select,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import dayjs from 'dayjs'
import { useScanInvoices, useUploadInvoice, useRetryAnalysis } from '../hooks/useScan'
import { usePeriods } from '../hooks/useExpenses'
import { getInvoicePhotoUrl } from '../services/expense'
import { getErrorMessage } from '../utils/error'
import { InvoiceThumb } from '../components/InvoiceThumb'
import ReviewModal from '../components/ReviewModal'
import { AppPagination } from '../components/AppPagination'
import { useToast } from '../components/Toast'
import type { Invoice } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'
const PERIOD_MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']

function periodOf(date: Date): string {
  let y = date.getFullYear()
  let m = date.getMonth()
  if (date.getDate() < 25) {
    m -= 1
    if (m < 0) {
      m = 11
      y -= 1
    }
  }
  return `${y}-${PERIOD_MONTHS[m]}-${PERIOD_MONTHS[(m + 1) % 12]}`
}

function ScanPage() {
  const [reviewId, setReviewId] = useState<string | null>(null)
  const [viewingInvoice, setViewingInvoice] = useState<Invoice | null>(null)
  const cameraRef = useRef<HTMLInputElement>(null)
  const galleryRef = useRef<HTMLInputElement>(null)
  const isMobile = useMediaQuery('(max-width: 48em)')

  // Periode berjalan (default). Tanggal belanja diambil otomatis dari struk oleh AI (fallback ke waktu sistem).
  const currentPeriod = periodOf(new Date())
  const nowDate = dayjs().format(DATE_TIME_FORMAT)
  const { data: periodsData } = usePeriods()
  const [period, setPeriod] = useState<string>(currentPeriod)
  const { data, isPending } = useScanInvoices(period)
  const upload = useUploadInvoice()
  const retry = useRetryAnalysis()
  const toast = useToast()
  const invoices = data?.invoices ?? []

  const periodOptions = useMemo(() => {
    const set = new Set<string>([currentPeriod, ...(periodsData?.periods ?? [])])
    return Array.from(set).map((p) => ({ value: p, label: p }))
  }, [periodsData, currentPeriod])

  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [sortBy, setSortBy] = useState<string>('newest')

  const visible = useMemo(() => {
    let list = data?.invoices ?? []
    if (statusFilter !== 'all') {
      list = list.filter((i) => i.status === statusFilter)
    }
    const sorted = [...list]
    sorted.sort((a, b) => {
      const cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      return sortBy === 'oldest' ? cmp : -cmp
    })
    return sorted
  }, [data, statusFilter, sortBy])

  const pick = (file: File | null) => {
    if (!file) return
    upload.mutate(
      { file, dateTime: nowDate },
      {
        onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal upload' }),
      },
    )
  }

  const handleRetry = (id: string) => {
    retry.mutate(id, {
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  return (
    <Container size="sm" px="md" py="lg">
      <Stack gap="lg">
        <div>
          <Text size="sm" c="blue" fw={700} mb={4}>
            AI RECEIPT SCANNER
          </Text>
          <Title order={1} size="clamp(1.5rem, 5vw, 2rem)">
            Scan Struk dengan AI
          </Title>
          <Text size="sm" c="dimmed" mt={5}>
            Upload foto atau PDF struk, lalu biarkan AI membaca detail pengeluaran.
          </Text>
        </div>

        <Stack>
          <Text size="sm" c="dimmed">
            Tanggal belanja diambil otomatis dari struk. Jika tidak terbaca, dipakai waktu saat ini.
          </Text>

          <Group grow={isMobile}>
            <Button fullWidth onClick={() => cameraRef.current?.click()} disabled={upload.isPending}>
              📷 Ambil Foto
            </Button>
            <Button fullWidth variant="light" onClick={() => galleryRef.current?.click()} disabled={upload.isPending}>
              🖼 Galeri / PDF
            </Button>
          </Group>

          {upload.isPending && (
            <Paper withBorder p="sm" radius="md">
              <Group justify="space-between" mb={4}>
                <Text size="sm">Mengupload...</Text>
                <Text size="sm" c="dimmed">
                  {upload.progress}%
                </Text>
              </Group>
              <Progress value={upload.progress} striped animated />
            </Paper>
          )}

          <Select
            label="Periode"
            placeholder="Pilih periode"
            data={periodOptions}
            value={period}
            onChange={(v) => setPeriod(v ?? currentPeriod)}
            searchable
            size="sm"
          />

          {invoices.length > 0 && (
            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs">
              <Select
                label="Filter status"
                size="xs"
                data={[
                  { value: 'all', label: 'Semua' },
                  { value: 'ANALYZING', label: 'Menunggu AI' },
                  { value: 'TO_REVIEW', label: 'Perlu Review' },
                  { value: 'SUBMITTED', label: 'Selesai' },
                  { value: 'ERROR', label: 'Gagal' },
                  { value: 'NOT_INVOICE', label: 'Bukan Invoice' },
                ]}
                value={statusFilter}
                onChange={(v) => setStatusFilter(v ?? 'all')}
              />
              <Select
                label="Urutkan"
                size="xs"
                data={[
                  { value: 'newest', label: 'Terbaru dulu' },
                  { value: 'oldest', label: 'Terlama dulu' },
                ]}
                value={sortBy}
                onChange={(v) => setSortBy(v ?? 'newest')}
              />
            </SimpleGrid>
          )}

          {isPending ? (
            <Group justify="center" py="xl">
              <Loader />
            </Group>
          ) : invoices.length === 0 ? (
            <Text size="sm" c="dimmed">
              Belum ada struk di periode ini. Upload foto/PDF untuk dianalisis.
            </Text>
          ) : (
            <AppPagination data={visible} key={`${statusFilter}|${sortBy}|${period}`}>
              {(pageData) =>
                pageData.length === 0 ? (
                  <Text size="sm" c="dimmed">
                    Tidak ada struk yang cocok dengan filter.
                  </Text>
                ) : (
                  <SimpleGrid cols={isMobile ? 2 : 3} spacing="sm">
              {pageData.map((inv) => (
                <Paper key={inv.id} withBorder p="xs" radius="md">
                  <Stack gap={6}>
                    <InvoiceThumb
                      type={inv.type}
                      url={getInvoicePhotoUrl(inv.id)}
                      h={90}
                      onClick={() => setViewingInvoice(inv)}
                    />
                    {inv.name ? (
                      <Text size="xs" c="dimmed" truncate title={inv.name}>
                        {inv.name}
                      </Text>
                    ) : null}
                    {inv.status === 'ANALYZING' && (
                      <Group justify="space-between" wrap="nowrap">
                        <Text size="xs" c="dimmed">
                          Menunggu AI…
                        </Text>
                        <Loader size="xs" />
                      </Group>
                    )}
                    {inv.status === 'TO_REVIEW' && (
                      <Button size="xs" variant="light" fullWidth onClick={() => setReviewId(inv.id)}>
                        Review
                      </Button>
                    )}
                    {inv.status === 'SUBMITTED' && (
                      <Badge color="green" variant="light">
                        Selesai
                      </Badge>
                    )}
                    {inv.status === 'NOT_INVOICE' && (
                      <Badge color="orange" variant="light">
                        Bukan Invoice
                      </Badge>
                    )}
                    {inv.status === 'ERROR' && (
                      <Group justify="space-between" wrap="nowrap">
                        <Text size="xs" c="red">
                          Gagal
                        </Text>
                        <Button size="compact-xs" variant="subtle" color="red" onClick={() => handleRetry(inv.id)}>
                          Coba lagi
                        </Button>
                      </Group>
                    )}
                  </Stack>
                </Paper>
              ))}
                  </SimpleGrid>
                )
              }
            </AppPagination>
          )}
        </Stack>
      </Stack>

      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        style={{ display: 'none' }}
        onChange={(e) => pick(e.target.files?.[0] ?? null)}
      />
      <input
        ref={galleryRef}
        type="file"
        accept="image/*,application/pdf"
        style={{ display: 'none' }}
        onChange={(e) => pick(e.target.files?.[0] ?? null)}
      />

      {reviewId && (
        <ReviewModal
          invoiceId={reviewId}
          onClose={() => setReviewId(null)}
          onSubmitted={() => setReviewId(null)}
        />
      )}

      <Modal
        opened={!!viewingInvoice}
        onClose={() => setViewingInvoice(null)}
        title={viewingInvoice?.name ?? 'Invoice'}
        size="md"
        centered
      >
        {viewingInvoice &&
          (viewingInvoice.type === 'pdf' ? (
            <Stack align="center" gap="sm">
              <Text fz={48}>📄</Text>
              <Button component="a" href={getInvoicePhotoUrl(viewingInvoice.id)} target="_blank" variant="light">
                Buka PDF
              </Button>
            </Stack>
          ) : (
            <Image src={getInvoicePhotoUrl(viewingInvoice.id)} alt={viewingInvoice.name ?? 'Invoice'} fit="contain" mah="70vh" />
          ))}
      </Modal>
    </Container>
  )
}

export default ScanPage
