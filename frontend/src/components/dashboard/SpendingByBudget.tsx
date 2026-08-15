import { Box, Group, Skeleton, Stack, Text } from '@mantine/core'
import { formatCurrency } from '../../utils/currency'
import type { BudgetSummary } from '../../types/expense'
import { DashboardSection } from './DashboardSection'

const MAX_ROWS = 5

export function SpendingByBudget({
  byBudget,
  total,
  isLoading,
  isError,
}: {
  byBudget: BudgetSummary[]
  total: number
  isLoading: boolean
  isError: boolean
}) {
  const rows = byBudget.slice(0, MAX_ROWS)
  const restAmount = byBudget.slice(MAX_ROWS).reduce((s, b) => s + b.amount, 0)

  const Row = ({ name, amount }: { name: string; amount: number }) => {
    const share = total > 0 ? (amount / total) * 100 : 0
    return (
      <div>
        <Group justify="space-between" mb={6} wrap="nowrap">
          <Text size="sm" fw={600} truncate style={{ flex: 1 }}>
            {name}
          </Text>
          <Group gap={6} wrap="nowrap">
            {total > 0 && (
              <Text size="sm" fw={700}>
                {Math.round(share)}%
              </Text>
            )}
            <Text size="sm" ff="monospace" c="dimmed">
              {formatCurrency(amount)}
            </Text>
          </Group>
        </Group>
        {total > 0 && (
          <Box bg="var(--mantine-color-gray-light)" style={{ height: 6, borderRadius: 999 }}>
            <Box
              bg="var(--mantine-color-blue-6)"
              style={{ width: `${Math.max(share, 1)}%`, height: 6, borderRadius: 999 }}
            />
          </Box>
        )}
      </div>
    )
  }

  return (
    <DashboardSection title="Spending by Budget" subtitle="Ke mana uang mengalir">
      {isLoading ? (
        <Stack gap="sm">
          <Skeleton h={28} />
          <Skeleton h={28} />
          <Skeleton h={28} />
        </Stack>
      ) : isError ? (
        <Text size="sm" c="red">
          Gagal memuat pengeluaran per budget.
        </Text>
      ) : byBudget.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Belum ada pengeluaran pada periode ini.
        </Text>
      ) : (
        <Stack gap="lg">
          {rows.map((b) => (
            <Row key={b.budget} name={b.budget} amount={b.amount} />
          ))}
          {restAmount > 0 && <Row name="Lainnya" amount={restAmount} />}
        </Stack>
      )}
    </DashboardSection>
  )
}
