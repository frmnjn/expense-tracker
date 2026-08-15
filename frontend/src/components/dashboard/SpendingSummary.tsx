import { Group, Paper, Skeleton, Text } from '@mantine/core'
import { formatCurrency } from '../../utils/currency'

export function SpendingSummary({
  total,
  prevTotal,
  comparisonNote = 'dibanding periode sebelumnya',
  isLoading,
  isError,
  hasPeriod,
}: {
  total: number
  prevTotal: number | null
  comparisonNote?: string
  isLoading: boolean
  isError: boolean
  hasPeriod: boolean
}) {
  if (!hasPeriod || isLoading || isError) {
    return (
      <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="lg" className="hero-card">
        <Text size="sm" c="dimmed" fw={600}>
          Pengeluaran
        </Text>
        {isLoading ? (
          <>
            <Skeleton h={40} mt="sm" w="55%" />
            <Skeleton h={14} mt="sm" w="35%" />
          </>
        ) : isError ? (
          <Text size="sm" c="red" mt="sm">
            Gagal memuat ringkasan pengeluaran.
          </Text>
        ) : (
          <Text size="sm" c="dimmed" mt="sm">
            Pilih periode untuk melihat ringkasan.
          </Text>
        )}
      </Paper>
    )
  }

  const prev = prevTotal
  const canCompare = prev !== null && prev > 0
  const deltaPct = canCompare && total !== prev ? ((total - prev) / prev) * 100 : null
  const goingDown = total < (prev ?? 0)

  return (
    <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="lg" className="hero-card">
      <Text size="sm" c="dimmed" fw={600}>
        Pengeluaran
      </Text>
      <Text fw={800} ff="monospace" fz="clamp(1.9rem, 6vw, 2.6rem)" lh={1.1} mt={2}>
        {formatCurrency(total)}
      </Text>
      <Group gap={6} mt={4}>
        {deltaPct !== null ? (
          <>
            <Text fw={700} c={goingDown ? 'teal' : 'red'}>
              {goingDown ? '↓' : '↑'} {Math.abs(deltaPct).toFixed(1)}%
            </Text>
            <Text size="sm" c="dimmed">
              {goingDown ? 'Lebih hemat' : 'Lebih banyak'} {comparisonNote}
            </Text>
          </>
        ) : canCompare ? (
          <Text size="sm" c="dimmed">
            Sama dengan periode sebelumnya.
          </Text>
        ) : (
          <Text size="sm" c="dimmed">
            Belum ada data periode sebelumnya.
          </Text>
        )}
      </Group>
    </Paper>
  )
}
