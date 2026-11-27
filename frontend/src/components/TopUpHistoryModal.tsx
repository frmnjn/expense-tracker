import { useMemo, useState } from 'react'
import { ActionIcon, Button, Group, Modal, Stack, Text } from '@mantine/core'
import { IconTrash } from '@tabler/icons-react'
import { useDeleteTopUp, useTopUps } from '../hooks/useTopUps'
import { useToast } from '../components/Toast'
import { formatCurrency } from '../utils/currency'
import { isDemoMode } from '../utils/demo'
import { isDateInPeriod } from '../utils/period'
import type { PeriodRange, TopUp } from '../types/expense'

function TopUpHistoryModal({
  opened,
  budget,
  currentPeriod,
  calendar,
  onClose,
}: {
  opened: boolean
  budget: string
  currentPeriod: string
  calendar: PeriodRange[]
  onClose: () => void
}) {
  const { data: topUpsData } = useTopUps()
  const deleteTopUp = useDeleteTopUp()
  const toast = useToast()
  const [pendingDelete, setPendingDelete] = useState<TopUp | null>(null)

  const history = useMemo(
    () => (topUpsData?.topUps ?? []).filter((t) => t.budget === budget).slice(),
    [topUpsData, budget],
  )

  const handleDelete = () => {
    if (!pendingDelete) return
    deleteTopUp.mutate(pendingDelete.id, {
      onSuccess: () => {
        toast.success('Top-up dihapus', { title: 'Berhasil' })
        setPendingDelete(null)
      },
      onError: (error) => {
        const message =
          (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Terjadi kesalahan, coba lagi'
        toast.error(message, { title: 'Gagal' })
      },
    })
  }

  return (
    <>
      <Modal opened={opened} onClose={onClose} title={`Riwayat Top-up — ${budget}`} centered>
        {history.length === 0 ? (
          <Text c="dimmed" ta="center" py="lg">
            Belum ada top-up untuk budget ini.
          </Text>
        ) : (
          <Stack gap="sm">
            {history.map((t) => (
              <Group key={t.id} justify="space-between" wrap="nowrap" gap="sm">
                <div style={{ minWidth: 0, flex: 1 }}>
                  <Text size="sm">{t.dateTime}</Text>
                  {t.description && (
                    <Text size="xs" c="dimmed" truncate>
                      {t.description}
                    </Text>
                  )}
                </div>
                <Group gap="xs" wrap="nowrap">
                  <Text ff="monospace" fw={600} c="green" style={{ whiteSpace: 'nowrap' }}>
                    +{formatCurrency(t.amount)}
                  </Text>
                  {!isDemoMode &&
                    currentPeriod &&
                    isDateInPeriod(t.dateTime, currentPeriod, calendar) && (
                      <ActionIcon
                        color="red"
                        variant="subtle"
                        size="sm"
                        aria-label={`Hapus top-up ${t.dateTime}`}
                        onClick={() => setPendingDelete(t)}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    )}
                </Group>
              </Group>
            ))}
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!pendingDelete}
        onClose={() => setPendingDelete(null)}
        title="Hapus top-up"
        centered
      >
        <Stack gap="xs">
          <Text>
            Yakin ingin menghapus top-up {pendingDelete ? formatCurrency(pendingDelete.amount) : ''}?
          </Text>
          <Text size="sm" c="red">
            Saldo budget akan dikurangi sebesar nominal top-up ini.
          </Text>
        </Stack>
        <Group justify="flex-end" mt="lg">
          <Button variant="default" onClick={() => setPendingDelete(null)}>
            Batal
          </Button>
          <Button color="red" loading={deleteTopUp.isPending} onClick={handleDelete}>
            Hapus
          </Button>
        </Group>
      </Modal>
    </>
  )
}

export default TopUpHistoryModal
