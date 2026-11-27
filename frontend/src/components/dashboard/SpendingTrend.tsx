import { useState } from 'react'
import { ActionIcon, Box, Group, Skeleton, Stack, Text } from '@mantine/core'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react'
import { formatCurrency } from '../../utils/currency'
import type { TrendPoint } from '../../types/expense'
import { ErrorState } from '../ErrorState'
import { DashboardSection } from './DashboardSection'

function shortLabel(period: string): string {
  const parts = period.split('-')
  return parts.length >= 3 ? `${parts[1]}–${parts[2]}` : period
}

const CHART_HEIGHT = 96
const BAR_WIDTH = 34
const PERIODS_PER_VIEW = 3

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
  const [endIndex, setEndIndex] = useState<number | null>(null)
  const lastIndex = periods.length - 1
  const resolvedEnd = endIndex === null ? lastIndex : Math.min(endIndex, lastIndex)
  const start = Math.max(0, resolvedEnd - (PERIODS_PER_VIEW - 1))
  const visible = periods.slice(start, resolvedEnd + 1)
  const canPrev = start > 0
  const canNext = resolvedEnd < lastIndex
  const max = Math.max(...visible.map((p) => p.total), 1)

  return (
    <DashboardSection title="Tren Pengeluaran" subtitle="3 periode per tampilan">
      {isLoading ? (
        <Stack gap="sm">
          <Skeleton h={CHART_HEIGHT} />
          <Skeleton h={14} w="60%" />
        </Stack>
      ) : isError ? (
        <ErrorState message="Gagal memuat tren pengeluaran." />
      ) : periods.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Belum ada data tren.
        </Text>
      ) : (
        <Group wrap="nowrap" align="center" gap={4}>
          <Box w={28} style={{ display: 'flex', justifyContent: 'center', flexShrink: 0 }}>
            {canPrev && (
              <ActionIcon
                variant="subtle"
                size="sm"
                aria-label="Periode sebelumnya"
                onClick={() => setEndIndex(resolvedEnd - 1)}
              >
                <IconChevronLeft size={18} />
              </ActionIcon>
            )}
          </Box>
          <Group
            align="flex-end"
            gap={2}
            wrap="nowrap"
            role="img"
            style={{ flex: 1, minWidth: 0 }}
            aria-label={`Tren pengeluaran: ${visible
              .map((p) => `${shortLabel(p.period)} ${formatCurrency(p.total)}`)
              .join(', ')}`}
          >
            {visible.map((p) => {
              const isSelected = p.period === selectedPeriod
              const height = Math.max(Math.round((p.total / max) * CHART_HEIGHT), p.total > 0 ? 6 : 0)
              return (
                <Stack key={p.period} gap={6} style={{ flex: 1, alignItems: 'center', minWidth: 0 }}>
                  <Box
                    w={BAR_WIDTH}
                    h={height}
                    bg={isSelected ? 'blue.7' : 'blue.4'}
                    style={{ borderRadius: 6, transition: 'height .3s ease' }}
                    mt="auto"
                  />
                  <Text size="xs" fw={isSelected ? 700 : 500} c={isSelected ? 'blue.7' : 'dimmed'}>
                    {shortLabel(p.period)}
                  </Text>
                  <Text
                    fz={10}
                    ff="monospace"
                    c={isSelected ? undefined : 'dimmed'}
                    w="100%"
                    ta="center"
                    title={formatCurrency(p.total)}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    {formatCurrency(p.total)}
                  </Text>
                  <Text fz={10} c="dimmed" w="100%" ta="center" style={{ whiteSpace: 'nowrap' }}>
                    {p.count} transaksi
                  </Text>
                </Stack>
              )
            })}
          </Group>
          <Box w={28} style={{ display: 'flex', justifyContent: 'center', flexShrink: 0 }}>
            {canNext && (
              <ActionIcon
                variant="subtle"
                size="sm"
                aria-label="Periode berikutnya"
                onClick={() => setEndIndex(resolvedEnd + 1)}
              >
                <IconChevronRight size={18} />
              </ActionIcon>
            )}
          </Box>
        </Group>
      )}
    </DashboardSection>
  )
}
