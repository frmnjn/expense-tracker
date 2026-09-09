import { useEffect, useMemo, useState } from 'react'
import { Modal, Stack, Paper, Group, Text, Button, Loader, Divider } from '@mantine/core'
import { useInvoiceDetail } from '../hooks/useScan'
import { getInvoicePhotoUrl } from '../services/expense'
import { formatCurrency } from '../utils/currency'
import { InvoiceThumb } from './InvoiceThumb'

function SubmittedExpensesModal({ invoiceId, type, onClose }: { invoiceId: string; type: 'image' | 'pdf'; onClose: () => void }) {
  const { data, isPending } = useInvoiceDetail(invoiceId)
  const [viewingPhoto, setViewingPhoto] = useState(false)

  const expenses = data?.expenses ?? []
  const total = useMemo(() => expenses.reduce((s, e) => s + Number(e.amount || 0), 0), [expenses])

  useEffect(() => {
    setViewingPhoto(false)
  }, [invoiceId])

  return (
    <Modal opened onClose={onClose} title="Rincian Pengeluaran" centered size="md"
      styles={{ body: { maxHeight: 'calc(100dvh - 140px)', overflowY: 'auto' } }}>
      {isPending ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : expenses.length === 0 ? (
        <Text size="sm" c="dimmed">Belum ada pengeluaran dari struk ini.</Text>
      ) : (
        <Stack gap="sm">
          <Paper withBorder p="xs" radius="md">
            <Group gap="md" wrap="nowrap" align="flex-start">
              <InvoiceThumb type={type} url={getInvoicePhotoUrl(invoiceId)} h={64} onClick={() => setViewingPhoto(true)} />
              <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                <Text size="sm" fw={600}>{data?.name ?? 'Struk'}</Text>
                <Text size="xs" c="dimmed">{expenses.length} pengeluaran</Text>
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">Total akhir</Text>
                  <Text size="sm" fw={700}>{formatCurrency(total)}</Text>
                </Group>
              </Stack>
            </Group>
          </Paper>

          <Divider label="Per budget" labelPosition="left" />

          {expenses.map((exp) => (
            <Paper key={exp.id} withBorder p="sm" radius="md">
              <Group justify="space-between" align="flex-start">
                <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                  <Text size="sm" fw={600} truncate>{exp.name}</Text>
                  <Text size="xs" c="dimmed">{exp.budget}</Text>
                </Stack>
                <Text size="sm" fw={700} style={{ whiteSpace: 'nowrap' }}>{formatCurrency(exp.amount)}</Text>
              </Group>
              {exp.description ? (
                <Text size="xs" c="dimmed" mt={6} style={{ whiteSpace: 'pre-wrap' }}>{exp.description}</Text>
              ) : null}
            </Paper>
          ))}

          <Button variant="light" size="xs" onClick={() => setViewingPhoto(true)}>
            Lihat foto/PDF struktu
          </Button>
        </Stack>
      )}

      <Modal opened={viewingPhoto} onClose={() => setViewingPhoto(false)} title={data?.name ?? 'Struk'} size="md" centered zIndex={1300}>
        {viewingPhoto && (type === 'pdf' ? (
          <Stack align="center" gap="sm">
            <Text fz={48}>📄</Text>
            <Button component="a" href={getInvoicePhotoUrl(invoiceId)} target="_blank" variant="light">Buka PDF</Button>
          </Stack>
        ) : (
          <img src={getInvoicePhotoUrl(invoiceId)} alt="Struk" style={{ maxWidth: '100%', maxHeight: '70vh' }} />
        ))}
      </Modal>
    </Modal>
  )
}

export default SubmittedExpensesModal
