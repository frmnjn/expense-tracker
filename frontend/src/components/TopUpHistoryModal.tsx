import { useMemo } from 'react'
import { Group, Modal, Stack, Text } from '@mantine/core'
import { useTopUps } from '../hooks/useTopUps'
import { formatCurrency } from '../utils/currency'

function TopUpHistoryModal({
  opened,
  budget,
  onClose,
}: {
  opened: boolean
  budget: string
  onClose: () => void
}) {
  const { data: topUpsData } = useTopUps()

  const history = useMemo(
    () => (topUpsData?.topUps ?? []).filter((t) => t.budget === budget).slice().reverse(),
    [topUpsData, budget],
  )

  return (
    <Modal opened={opened} onClose={onClose} title={`Riwayat Top-up — ${budget}`} centered>
      {history.length === 0 ? (
        <Text c="dimmed" ta="center" py="lg">
          Belum ada top-up untuk budget ini.
        </Text>
      ) : (
        <Stack gap="sm">
          {history.map((t) => (
            <Group key={t.id} justify="space-between">
              <div>
                <Text size="sm">{t.dateTime}</Text>
                {t.description && (
                  <Text size="xs" c="dimmed">
                    {t.description}
                  </Text>
                )}
              </div>
              <Text ff="monospace" fw={600} c="green">
                +{formatCurrency(t.amount)}
              </Text>
            </Group>
          ))}
        </Stack>
      )}
    </Modal>
  )
}

export default TopUpHistoryModal
