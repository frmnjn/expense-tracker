import { Box, Group, Skeleton, Stack, Text } from '@mantine/core'
import { formatCurrency } from '../../utils/currency'
import type { BudgetSummary, CategorySummary } from '../../types/expense'
import { ErrorState } from '../ErrorState'
import { DashboardSection } from './DashboardSection'

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

  const Row = ({ name, amount, categories }: { name: string; amount: number; categories?: CategorySummary[] }) => {
    const share = total > 0 ? (amount / total) * 100 : 0
    const hasRealCategory = (categories ?? []).some((c) => c.category !== 'Uncategorized')
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
        {hasRealCategory && categories && (
          <Stack gap={2} mt={4}>
            {categories.map((c) => (
              <Group key={c.category} justify="space-between" wrap="nowrap" gap="xs">
                <Text size="xs" c="dimmed" truncate>
                  {c.category}
                </Text>
                <Text size="xs" c="dimmed" ff="monospace" style={{ whiteSpace: 'nowrap' }}>
                  {formatCurrency(c.amount)}
                </Text>
              </Group>
            ))}
          </Stack>
        )}
      </div>
    )
  }

  return (
    <DashboardSection title="Pengeluaran per Budget" subtitle="Ke mana uang mengalir">
      {isLoading ? (
        <Stack gap="sm">
          <Skeleton h={28} />
          <Skeleton h={28} />
          <Skeleton h={28} />
        </Stack>
      ) : isError ? (
        <ErrorState message="Gagal memuat pengeluaran per budget." />
      ) : byBudget.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Belum ada pengeluaran pada periode ini.
        </Text>
      ) : (
        <Stack gap="lg">
          {byBudget.map((b) => (
            <Row key={b.budget} name={b.budget} amount={b.amount} categories={b.categories} />
          ))}
        </Stack>
      )}
    </DashboardSection>
  )
}
