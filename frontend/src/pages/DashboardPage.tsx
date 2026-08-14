import { useMemo, useState } from 'react'
import {
  ActionIcon,
  Badge,
  Box,
  Button,
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
import TopUpModal from '../components/TopUpModal'
import TopUpHistoryModal from '../components/TopUpHistoryModal'
import AddBudgetModal from '../components/AddBudgetModal'
import EditBudgetModal from '../components/EditBudgetModal'
import DeleteBudgetModal from '../components/DeleteBudgetModal'
import { formatCurrency } from '../utils/currency'
import type { BudgetSummary } from '../types/expense'

const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

function DashboardPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options } = useOptions()
  const [period, setPeriod] = useState<string | null>(null)
  const [selectedTopUpBudget, setSelectedTopUpBudget] = useState<string | null>(null)
  const [selectedTopUpHistoryBudget, setSelectedTopUpHistoryBudget] = useState<string | null>(null)
  const [addBudgetOpened, setAddBudgetOpened] = useState(false)
  const [deleteBudgetName, setDeleteBudgetName] = useState<string | null>(null)
  const [editBudget, setEditBudget] = useState<{ name: string; balance: number | undefined; alertThreshold: number | undefined } | null>(null)
  const { data: summary, isPending: summaryLoading } = useSummary(period)
  const { data: trend } = useTrend(3)
  const isMobile = useMediaQuery('(max-width: 48em)')

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])
  const balanceOf = (name: string): number | undefined => options?.budgets.find((b) => b.name === name)?.balance
  const thresholdOf = (name: string): number | undefined => options?.budgets.find((b) => b.name === name)?.alertThreshold
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
    const threshold = thresholdOf(name)
    const rank = rankOf.get(name)
    const info = budgetInfo(name)
    return (
      <Paper key={name} withBorder p="md" radius="lg" className="budget-card">
        <Group justify="space-between" align="flex-start" mb="xs" wrap="nowrap">
          <Group gap="xs" wrap="nowrap" style={{ minWidth: 0 }}>
            {rank && <Badge size="sm" variant="light" color="blue" circle>{rank}</Badge>}
            <Text fw={700} truncate>{name}</Text>
          </Group>
          <Text fw={800} ff="monospace" fz="lg" c={balance !== undefined ? balanceColor(balance) : undefined}>
            {balance !== undefined ? formatCurrency(balance) : '-'}
          </Text>
        </Group>
        {info && <Text size="sm" c="dimmed">{info.count} transaksi · {formatCurrency(info.amount)}</Text>}
        {threshold !== undefined && threshold > 0 && (
          <Text size="xs" c="orange" fw={600} mt={6}>⚠️ Ambang {formatCurrency(threshold)}</Text>
        )}
        <Group justify="flex-end" gap={4} mt="sm">
          <ActionIcon size="sm" variant="subtle" onClick={() => setSelectedTopUpHistoryBudget(name)} aria-label="Riwayat top up">📋</ActionIcon>
          <ActionIcon size="sm" variant="light" color="green" onClick={() => setSelectedTopUpBudget(name)} aria-label="Top up">+</ActionIcon>
          <ActionIcon size="sm" variant="subtle" onClick={() => setEditBudget({ name, balance, alertThreshold: threshold })} aria-label="Edit budget">✎</ActionIcon>
          <ActionIcon size="sm" variant="subtle" color="red" onClick={() => setDeleteBudgetName(name)} aria-label="Hapus budget">🗑</ActionIcon>
        </Group>
      </Paper>
    )
  }

  return (
    <Container size="lg" px={{ base: 0, sm: 'md' }} py={{ base: 0, sm: 'md' }}>
      <Stack gap="lg">
        <Group justify="space-between" align="flex-end">
          <div>
            <Text size="sm" c="blue" fw={700} mb={4}>OVERVIEW</Text>
            <Title order={1} size="clamp(1.65rem, 5vw, 2.1rem)">Dashboard</Title>
            <Text c="dimmed" mt={5}>Ringkasan keuangan dan budget kamu.</Text>
          </div>
          <Select
            w={{ base: 150, sm: 190 }}
            placeholder={periodsLoading ? 'Memuat...' : 'Pilih periode'}
            data={periods}
            value={period}
            onChange={setPeriod}
            searchable
            size="sm"
            aria-label="Periode"
          />
        </Group>

        <Stack pos="relative" gap="lg">
          <LoadingOverlay visible={summaryLoading && !!period} zIndex={1000} overlayProps={{ radius: 'lg', blur: 1 }} />
          <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="xl" className="hero-card">
            <Group justify="space-between" align="center" mb="md">
              <div>
                <Text size="sm" c="dimmed" fw={600}>Saldo per budget</Text>
                <Text fz="xs" c="dimmed" mt={3}>{allBudgets.length} budget aktif</Text>
              </div>
              <Button size="sm" radius="md" onClick={() => setAddBudgetOpened(true)}>+ Budget</Button>
            </Group>
            {allBudgets.length === 0 ? (
              <Text c="dimmed" py="md">Belum ada budget. Tambahkan budget pertama kamu.</Text>
            ) : isMobile ? (
              <Stack gap="sm">{allBudgets.map(renderBudgetCard)}</Stack>
            ) : (
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">{allBudgets.map(renderBudgetCard)}</SimpleGrid>
            )}
          </Paper>

          {trend && trend.periods.length > 0 && (
            <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="xl">
              <Group justify="space-between" mb="lg">
                <div>
                  <Text fw={700}>Pengeluaran bulanan</Text>
                  <Text size="xs" c="dimmed">3 bulan terakhir</Text>
                </div>
              </Group>
              <Stack gap="md">
                {trend.periods.map((p) => {
                  const max = Math.max(...trend.periods.map((x) => x.total), 1)
                  const width = Math.max((p.total / max) * 100, p.total > 0 ? 2 : 0)
                  return (
                    <div key={p.period}>
                      <Group justify="space-between" mb={5}>
                        <Text size="sm" fw={600}>{p.period}</Text>
                        <Text size="sm" ff="monospace">{formatCurrency(p.total)}</Text>
                      </Group>
                      <Box bg="blue.1" darkHidden style={{ height: 10, borderRadius: 999 }}>
                        <Box bg="blue.6" style={{ width: `${width}%`, height: 10, borderRadius: 999, transition: 'width .3s ease' }} />
                      </Box>
                      <Text size="xs" c="dimmed" mt={4}>{p.count} transaksi</Text>
                    </div>
                  )
                })}
              </Stack>
            </Paper>
          )}

          <TopUpModal opened={!!selectedTopUpBudget} budget={selectedTopUpBudget ?? ''} balance={selectedTopUpBalance} onClose={() => setSelectedTopUpBudget(null)} />
          <TopUpHistoryModal opened={!!selectedTopUpHistoryBudget} budget={selectedTopUpHistoryBudget ?? ''} onClose={() => setSelectedTopUpHistoryBudget(null)} />
          <AddBudgetModal opened={addBudgetOpened} onClose={() => setAddBudgetOpened(false)} />
          <EditBudgetModal budget={editBudget?.name ?? null} balance={editBudget?.balance} alertThreshold={editBudget?.alertThreshold} onClose={() => setEditBudget(null)} />
          <DeleteBudgetModal name={deleteBudgetName} onClose={() => setDeleteBudgetName(null)} />
        </Stack>
      </Stack>
    </Container>
  )
}

export default DashboardPage
