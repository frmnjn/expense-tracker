import { Fragment, useMemo } from 'react'
import { Divider, Group, Skeleton, Stack, Text } from '@mantine/core'
import dayjs from 'dayjs'
import { useTopUps } from '../../hooks/useTopUps'
import { formatCurrency } from '../../utils/currency'
import { isDateInPeriod } from '../../utils/period'
import type { PeriodRange } from '../../types/expense'
import { ErrorState } from '../ErrorState'
import { DashboardSection } from './DashboardSection'

export function TopUpHistory({
  period,
  calendar,
}: {
  period: string | null
  calendar: PeriodRange[]
}) {
  const { data, isPending, isError } = useTopUps()

  const topUps = useMemo(() => {
    if (!period) return []
    return (data?.topUps ?? []).filter((topUp) => isDateInPeriod(topUp.dateTime, period, calendar))
  }, [data, period, calendar])

  return (
    <DashboardSection title="Top Up" subtitle={period ? `Top up periode ${period}` : 'Top up periode'}>
      {isPending ? (
        <Stack gap="sm">
          <Skeleton h={28} />
          <Skeleton h={28} />
          <Skeleton h={28} />
        </Stack>
      ) : isError ? (
        <ErrorState message="Gagal memuat riwayat top-up." />
      ) : topUps.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Belum ada top-up pada periode ini.
        </Text>
      ) : (
        <Stack gap={0}>
          {topUps.map((topUp, index) => (
            <Fragment key={topUp.id}>
              {index > 0 && <Divider my="sm" />}
              <Group justify="space-between" align="center" wrap="nowrap" py="xs" gap="sm">
                <div style={{ minWidth: 0, flex: 1 }}>
                  <Text size="sm" fw={600} truncate>
                    {topUp.budget}
                  </Text>
                  {topUp.description && (
                    <Text size="xs" c="dimmed" truncate>
                      {topUp.description}
                    </Text>
                  )}
                  <Text size="xs" c="dimmed">
                    {dayjs(topUp.dateTime).format('DD MMM YYYY, HH:mm')}
                  </Text>
                </div>
                <Text size="sm" fw={700} ff="monospace" c="green" style={{ whiteSpace: 'nowrap' }}>
                  +{formatCurrency(topUp.amount)}
                </Text>
              </Group>
            </Fragment>
          ))}
        </Stack>
      )}
    </DashboardSection>
  )
}
