import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { Anchor, Divider, Group, Skeleton, Stack, Text } from '@mantine/core'
import dayjs from 'dayjs'
import { formatCurrency } from '../../utils/currency'
import type { Expense } from '../../types/expense'
import { DashboardSection } from './DashboardSection'

const MAX_RECENT = 5

export function RecentExpenses({
  expenses,
  isLoading,
  isError,
  hasPeriod,
}: {
  expenses: Expense[]
  isLoading: boolean
  isError: boolean
  hasPeriod: boolean
}) {
  const recent = expenses.slice(0, MAX_RECENT)

  return (
    <DashboardSection
      title="Recent Expenses"
      subtitle="Pengeluaran terakhir"
      action={
        <Anchor component={Link} to="/riwayat" size="sm">
          Lihat semua
        </Anchor>
      }
    >
      {!hasPeriod ? (
        <Text size="sm" c="dimmed" py="sm">
          Pilih periode untuk melihat pengeluaran terakhir.
        </Text>
      ) : isLoading ? (
        <Stack gap="sm">
          <Skeleton h={28} />
          <Skeleton h={28} />
          <Skeleton h={28} />
        </Stack>
      ) : isError ? (
        <Text size="sm" c="red">
          Gagal memuat pengeluaran.
        </Text>
      ) : recent.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Belum ada pengeluaran pada periode ini.
        </Text>
      ) : (
        <Stack gap={0}>
          {recent.map((e, i) => (
            <Fragment key={e.id}>
              {i > 0 && <Divider my="sm" />}
              <Group justify="space-between" align="center" wrap="nowrap" py="xs">
                <div style={{ minWidth: 0, flex: 1 }}>
                  <Text size="sm" fw={600} truncate>
                    {e.name}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {e.budget} · {dayjs(e.dateTime).format('DD MMM, HH:mm')}
                  </Text>
                </div>
                <Text size="sm" fw={700} ff="monospace" style={{ whiteSpace: 'nowrap' }}>
                  {formatCurrency(e.amount)}
                </Text>
              </Group>
            </Fragment>
          ))}
        </Stack>
      )}
    </DashboardSection>
  )
}
