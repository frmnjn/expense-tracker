import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Divider, Group, Loader, Modal, Paper, Stack, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { useInvoiceDetail } from '../hooks/useScan'
import { getInvoicePhotoUrl } from '../services/expense'
import { formatCurrency } from '../utils/currency'
import { InvoiceThumb } from './InvoiceThumb'

function SubmittedExpensesModal({ invoiceId, type, onClose }: { invoiceId: string; type: 'image' | 'pdf'; onClose: () => void }) {
  const { data, isPending } = useInvoiceDetail(invoiceId)
  const [viewingPhoto, setViewingPhoto] = useState(false)
  const isMobile = useMediaQuery('(max-width: 48em)')

  const expenses = data?.expenses ?? []
  const total = useMemo(() => expenses.reduce((s, e) => s + Number(e.amount || 0), 0), [expenses])

  useEffect(() => {
    setViewingPhoto(false)
  }, [invoiceId])

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
      ) : expenses.length === 0 ? (
        <Text size="sm" c="dimmed">Belum ada pengeluaran dari struk ini.</Text>
      ) : (
        <Stack gap="sm">
          <Paper withBorder p="sm" radius="md">
            <Group gap="md" wrap="nowrap" align="flex-start">
              <InvoiceThumb type={type} url={getInvoicePhotoUrl(invoiceId)} h={96} onClick={() => setViewingPhoto(true)} />
              <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                <Text size="sm" fw={600} truncate title={data?.name}>
                  {data?.name ?? 'Struk'}
                </Text>
                <Text size="xs" c="dimmed">{expenses.length} pengeluaran</Text>
                <Text size="xs" c="dimmed" mt={6}>Total akhir</Text>
                <Text fw={800} fz="xl" c="blue">{formatCurrency(total)}</Text>
              </Stack>
            </Group>
          </Paper>

          <Button variant="light" size="sm" onClick={() => setViewingPhoto(true)}>
            Lihat foto/PDF struk
          </Button>

          <Divider label="Per budget" labelPosition="left" />

          {expenses.map((exp) => (
            <Paper key={exp.id} withBorder p="sm" radius="md">
              <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
                <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
                  <Text size="sm" fw={600} title={exp.name}>{exp.name}</Text>
                  <Badge size="sm" variant="light" color="gray" style={{ alignSelf: 'flex-start' }}>
                    {exp.budget}
                  </Badge>
                </Stack>
                <Text size="sm" fw={700} style={{ whiteSpace: 'nowrap' }}>{formatCurrency(exp.amount)}</Text>
              </Group>
              {exp.description ? (
                <Text size="xs" c="dimmed" mt={8} style={{ whiteSpace: 'pre-wrap' }}>{exp.description}</Text>
              ) : null}
            </Paper>
          ))}
        </Stack>
      )}

      <Modal
        opened={viewingPhoto}
        onClose={() => setViewingPhoto(false)}
        title={data?.name ?? 'Struk'}
        size="md"
        centered
        fullScreen={isMobile}
        zIndex={1300}
      >
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
