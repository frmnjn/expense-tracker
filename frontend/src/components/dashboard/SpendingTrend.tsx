import { Box, Group, Skeleton, Stack, Text } from '@mantine/core'
import { formatCurrency } from '../../utils/currency'
import type { TrendPoint } from '../../types/expense'
import { DashboardSection } from './DashboardSection'

function shortLabel(period: string): string {
  const parts = period.split('-')
  return parts.length >= 3 ? `${parts[1]}–${parts[2]}` : period
}

const CHART_HEIGHT = 96
const BAR_WIDTH = 34

export function SpendingTrend({
  periods,
  selectedPeriod,
  isLoading,
  isError,
}: {
  periods: TrendPoint[]
  selectedPeriod: string | null
  isLoading: boolean
  isError: boolean
}) {
  const max = Math.max(...periods.map((p) => p.total), 1)

  return (
    <DashboardSection title="Spending Trend" subtitle="3 bulan terakhir">
      {isLoading ? (
        <Stack gap="sm">
          <Skeleton h={CHART_HEIGHT} />
          <Skeleton h={14} w="60%" />
        </Stack>
      ) : isError ? (
        <Text size="sm" c="red">
          Gagal memuat tren pengeluaran.
        </Text>
      ) : periods.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Belum ada data tren.
        </Text>
      ) : (
        <Group align="flex-end" gap="sm" wrap="nowrap">
          {periods.map((p) => {
            const isSelected = p.period === selectedPeriod
            const height = Math.max(Math.round((p.total / max) * CHART_HEIGHT), p.total > 0 ? 6 : 0)
            return (
              <Stack key={p.period} gap={6} style={{ flex: 1, alignItems: 'center', minWidth: 0 }}>
                <div
                  style={{
                    height: CHART_HEIGHT,
                    display: 'flex',
                    alignItems: 'flex-end',
                  }}
                >
                  <Box
                    w={BAR_WIDTH}
                    h={height}
                    bg={isSelected ? 'blue.7' : 'blue.4'}
                    style={{ borderRadius: 6, transition: 'height .3s ease' }}
                  />
                </div>
                <Text size="xs" fw={isSelected ? 700 : 500} c={isSelected ? 'blue.7' : 'dimmed'}>
                  {shortLabel(p.period)}
                </Text>
                <Text size="xs" ff="monospace" c={isSelected ? undefined : 'dimmed'} truncate>
                  {formatCurrency(p.total)}
                </Text>
              </Stack>
            )
          })}
        </Group>
      )}
    </DashboardSection>
  )
}
