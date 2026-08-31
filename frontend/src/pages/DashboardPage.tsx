import { useEffect, useMemo, useState } from 'react'
import { Container, Group, Select, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { useExpenses, usePeriods, useSummary, useTrend } from '../hooks/useExpenses'
import { useOptions } from '../hooks/useOptions'
import TopUpModal from '../components/TopUpModal'
import TopUpHistoryModal from '../components/TopUpHistoryModal'
import AddBudgetModal from '../components/AddBudgetModal'
import EditBudgetModal from '../components/EditBudgetModal'
import DeleteBudgetModal from '../components/DeleteBudgetModal'
import { SpendingSummary } from '../components/dashboard/SpendingSummary'
import { BudgetHealth } from '../components/dashboard/BudgetHealth'
import { SpendingTrend } from '../components/dashboard/SpendingTrend'
import { SpendingByBudget } from '../components/dashboard/SpendingByBudget'
import { FinancialInsights } from '../components/dashboard/FinancialInsights'
import { RecentExpenses } from '../components/dashboard/RecentExpenses'
import { previousPeriod, parsePeriodStart, periodEnd, elapsedDays } from '../utils/period'
import { buildInsights } from '../utils/insights'

function DashboardPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options, isPending: optionsLoading, isError: optionsError } = useOptions()
  const [period, setPeriod] = useState<string | null>(null)
  const [selectedTopUpBudget, setSelectedTopUpBudget] = useState<string | null>(null)
  const [selectedTopUpHistoryBudget, setSelectedTopUpHistoryBudget] = useState<string | null>(null)
  const [addBudgetOpened, setAddBudgetOpened] = useState(false)
  const [deleteBudgetName, setDeleteBudgetName] = useState<string | null>(null)
  const [editBudget, setEditBudget] = useState<{ name: string; balance: number | undefined; alertThreshold: number | undefined; description: string | undefined } | null>(null)

  useEffect(() => {
    if (!period && periodsData && periodsData.periods.length > 0) {
      setPeriod(periodsData.periods[0])
    }
  }, [period, periodsData])

  const prevPeriod = period ? previousPeriod(period) : null
  const { data: summary, isPending: summaryLoading, isError: summaryError } = useSummary(period)
  const {
    data: prevExpensesData,
    isPending: prevExpensesLoading,
    isError: prevExpensesError,
  } = useExpenses(prevPeriod)
  const { data: trend, isPending: trendLoading, isError: trendError } = useTrend(3)
  const {
    data: expensesData,
    isPending: expensesLoading,
    isError: expensesError,
  } = useExpenses(period)

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])
  const budgets = useMemo(() => options?.budgets ?? [], [options])
  const byBudget = useMemo(() => summary?.byBudget ?? [], [summary])
  const selectedTopUpBalance = selectedTopUpBudget
    ? budgets.find((b) => b.name === selectedTopUpBudget)?.balance
    : undefined

  // Perbandingan date-to-date dengan periode sebelumnya (Plan A):
  // total periode berjalan dibandingkan dengan pengeluaran periode sebelumnya
  // hanya sampai tanggal yang sama, bukan seluruh periode.
  const comparison = useMemo(() => {
    if (!period || !prevPeriod) return { prevTotal: null as number | null, partial: false }
    const days = elapsedDays(period, new Date())
    const prevStart = parsePeriodStart(prevPeriod)
    if (days === null || !prevStart) return { prevTotal: null, partial: false }
    const cutoff = new Date(prevStart.getFullYear(), prevStart.getMonth(), prevStart.getDate() + days)
    const total = (prevExpensesData?.expenses ?? [])
      .filter((e) => new Date(e.dateTime.replace(' ', 'T')) <= cutoff)
      .reduce((s, e) => s + e.amount, 0)
    const end = periodEnd(period)
    const partial = end !== null && new Date() < end
    return { prevTotal: total > 0 ? total : null, partial }
  }, [period, prevPeriod, prevExpensesData])

  const comparisonNote = comparison.partial
    ? 'dibanding periode sebelumnya (tanggal yang sama)'
    : 'dibanding periode sebelumnya'

  const insights = useMemo(() => {
    if (!summary) return []
    return buildInsights({
      selectedTotal: summary.total,
      prevTotal: comparison.prevTotal,
      byBudget: summary.byBudget ?? [],
      budgets,
      comparisonNote,
    })
  }, [summary, comparison, budgets, comparisonNote])

  return (
    <Container size="lg" px={{ base: 'md', sm: 'md' }} py={{ base: 'md', sm: 'md' }} pb={{ base: 88, sm: 'md' }}>
      <Stack gap="xl">
        <Group justify="space-between" align="flex-end">
          <div>
            <Text size="sm" c="blue" fw={700} mb={4}>
              OVERVIEW
            </Text>
            <Title order={1} size="clamp(1.65rem, 5vw, 2.1rem)">
              Dashboard
            </Title>
            <Text c="dimmed" mt={5}>
              Ringkasan keuangan dan budget kamu.
            </Text>
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

        <SpendingSummary
          total={summary?.total ?? 0}
          prevTotal={comparison.prevTotal}
          comparisonNote={comparisonNote}
          isLoading={summaryLoading || prevExpensesLoading}
          isError={summaryError || prevExpensesError}
          hasPeriod={!!period}
        />

        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xl">
          <BudgetHealth
            budgets={budgets}
            byBudget={byBudget}
            isLoading={optionsLoading}
            isError={optionsError}
            onAddBudget={() => setAddBudgetOpened(true)}
            onTopUp={(name) => setSelectedTopUpBudget(name)}
            onHistory={(name) => setSelectedTopUpHistoryBudget(name)}
            onEdit={setEditBudget}
            onDelete={(name) => setDeleteBudgetName(name)}
          />
          <SpendingTrend
            periods={trend?.periods ?? []}
            selectedPeriod={period}
            isLoading={trendLoading}
            isError={trendError}
          />
        </SimpleGrid>

        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xl">
          <SpendingByBudget
            byBudget={byBudget}
            total={summary?.total ?? 0}
            isLoading={summaryLoading}
            isError={summaryError}
          />
          <FinancialInsights
            insights={insights}
            isLoading={summaryLoading}
            isError={summaryError}
            hasPeriod={!!period}
          />
        </SimpleGrid>

        <RecentExpenses
          expenses={expensesData?.expenses ?? []}
          isLoading={expensesLoading}
          isError={expensesError}
          hasPeriod={!!period}
        />

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
        <AddBudgetModal opened={addBudgetOpened} onClose={() => setAddBudgetOpened(false)} />
        <EditBudgetModal
          budget={editBudget?.name ?? null}
          balance={editBudget?.balance}
          alertThreshold={editBudget?.alertThreshold}
          description={editBudget?.description}
          onClose={() => setEditBudget(null)}
        />
        <DeleteBudgetModal name={deleteBudgetName} onClose={() => setDeleteBudgetName(null)} />
      </Stack>
    </Container>
  )
}

export default DashboardPage
