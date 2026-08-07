import { useMemo, useState } from 'react'
import {
  ActionIcon,
  Anchor,
  Badge,
  Box,
  Container,
  Group,
  LoadingOverlay,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { usePeriods, useSummary, useTrend } from '../hooks/useExpenses'
import { useOptions } from '../hooks/useOptions'
import ColorSchemeToggle from '../components/ColorSchemeToggle'
import TopUpModal from '../components/TopUpModal'
import TopUpHistoryModal from '../components/TopUpHistoryModal'
import { formatCurrency } from '../utils/currency'
import type { BudgetSummary } from '../types/expense'

const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

function DashboardPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options } = useOptions()
  const [period, setPeriod] = useState<string | null>(null)
  const [selectedTopUpBudget, setSelectedTopUpBudget] = useState<string | null>(null)
  const [selectedTopUpHistoryBudget, setSelectedTopUpHistoryBudget] = useState<string | null>(null)
  const { data: summary, isPending: summaryLoading } = useSummary(period)
  const { data: trend } = useTrend(3)
  const isMobile = useMediaQuery('(max-width: 48em)')

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])

  const balanceOf = (name: string): number | undefined => options?.budgets.find((b) => b.name === name)?.balance

  const byBudget = summary?.byBudget ?? []
  const budgetInfo = (name: string): BudgetSummary | undefined => byBudget.find((b) => b.budget === name)
  const rankOf = new Map<string, number>()
  byBudget.slice(0, 3).forEach((b, i) => rankOf.set(b.budget, i + 1))

  const allBudgets = useMemo(() => {
    const names = new Set<string>([
      ...(options?.budgets ?? []).map((b) => b.name),
      ...(summary?.byBudget ?? []).map((b) => b.budget),
    ])
    return Array.from(names).sort()
  }, [options, summary])

  const selectedTopUpBalance = selectedTopUpBudget ? balanceOf(selectedTopUpBudget) : undefined

  const renderBudgetCard = (name: string) => {
    const balance = balanceOf(name)
    const rank = rankOf.get(name)
    const info = budgetInfo(name)
    return (
      <Paper key={name} withBorder p="sm" radius="md">
        <Group justify="space-between" mb={4}>
          <Group gap="xs" wrap="nowrap">
            {rank && (
              <Badge size="sm" variant="light" color="blue" circle>
                {rank}
              </Badge>
            )}
            <Text fw={600} style={{ wordBreak: 'break-word' }}>
              {name}
            </Text>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <Text fw={700} ff="monospace" c={balance !== undefined ? balanceColor(balance) : undefined}>
              {balance !== undefined ? formatCurrency(balance) : '-'}
            </Text>
            <ActionIcon
              size="sm"
              variant="subtle"
              color="blue"
              onClick={() => setSelectedTopUpHistoryBudget(name)}
            >
              <span>📋</span>
            </ActionIcon>
            <ActionIcon size="sm" variant="subtle" color="green" onClick={() => setSelectedTopUpBudget(name)}>
              <span>+</span>
            </ActionIcon>
          </Group>
        </Group>
        {info && (
          <Text size="sm" c="dimmed">
            {info.count} transaksi · {formatCurrency(info.amount)}
          </Text>
        )}
      </Paper>
    )
  }

  return (
    <Container size="md" py="lg">
      <Group justify="space-between" align="flex-start" mb="md">
        <div>
          <Title order={2}>Dashboard</Title>
          <Group gap="md">
            <Anchor href="/catat" size="sm">
              Catat Pengeluaran
            </Anchor>
            <Anchor href="/riwayat" size="sm">
              Riwayat
            </Anchor>
          </Group>
        </div>
        <ColorSchemeToggle />
      </Group>

      <Select
        label="Periode"
        placeholder={periodsLoading ? 'Memuat...' : 'Pilih periode'}
        data={periods}
        value={period}
        onChange={setPeriod}
        searchable
        size="md"
        mb="md"
      />

      <Stack pos="relative">
        <LoadingOverlay visible={summaryLoading && !!period} zIndex={1000} overlayProps={{ radius: 'sm', blur: 1 }} />

        <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="md" shadow="sm">
          <Title order={4} mb="sm">
            Saldo per Budget
          </Title>
          {allBudgets.length === 0 ? (
            <Text c="dimmed">Belum ada budget.</Text>
          ) : isMobile ? (
            <Stack gap="sm">{allBudgets.map(renderBudgetCard)}</Stack>
          ) : (
            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
              {allBudgets.map(renderBudgetCard)}
            </SimpleGrid>
          )}
        </Paper>

        {trend && trend.periods.length > 0 && (
          <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="md" shadow="sm">
            <Title order={4} mb="sm">
              3 Bulan Terakhir
            </Title>
            <Stack gap="md">
              {trend.periods.map((p) => {
                const max = Math.max(...trend.periods.map((x) => x.total), 1)
                const width = Math.max((p.total / max) * 100, p.total > 0 ? 2 : 0)
                return (
                  <div key={p.period}>
                    <Group justify="space-between" mb={4}>
                      <Text size="sm">{p.period}</Text>
                      <Text size="sm" ff="monospace">
                        {formatCurrency(p.total)} · {p.count} transaksi
                      </Text>
                    </Group>
                    <Box
                      bg="blue.6"
                      style={{
                        width: `${width}%`,
                        height: 14,
                        borderRadius: 4,
                        transition: 'width 0.3s ease',
                      }}
                    />
                  </div>
                )
              })}
            </Stack>
          </Paper>
        )}

        <TopUpModal
          opened={!!selectedTopUpBudget}
          budget={selectedTopUpBudget ?? ''}
          balance={selectedTopUpBalance}
          onClose={() => setSelectedTopUpBudget(null)}
        />

        <TopUpHistoryModal
          opened={!!selectedTopUpHistoryBudget}
          budget={selectedTopUpHistoryBudget ?? ''}
          onClose={() => setSelectedTopUpHistoryBudget(null)}
        />
      </Stack>
    </Container>
  )
}

export default DashboardPage
