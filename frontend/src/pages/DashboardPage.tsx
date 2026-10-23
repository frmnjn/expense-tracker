import { useEffect, useMemo, useState } from 'react'
import { Button, Container, Group, Select, SimpleGrid, Stack } from '@mantine/core'
import { IconCalendarEvent } from '@tabler/icons-react'
import { PageHeader } from '../components/PageHeader'
import { useExpenses, usePeriods, useSummary, useTrend } from '../hooks/useExpenses'
import { useOptions } from '../hooks/useOptions'
import TopUpModal from '../components/TopUpModal'
import TopUpHistoryModal from '../components/TopUpHistoryModal'
import AddBudgetModal from '../components/AddBudgetModal'
import EditBudgetModal from '../components/EditBudgetModal'
import DeleteBudgetModal from '../components/DeleteBudgetModal'
import ManageCategoriesModal from '../components/ManageCategoriesModal'
import PaydaySettingsModal from '../components/PaydaySettingsModal'
import { SpendingSummary } from '../components/dashboard/SpendingSummary'
import { BudgetHealth } from '../components/dashboard/BudgetHealth'
import { SpendingTrend } from '../components/dashboard/SpendingTrend'
import { SpendingByBudget } from '../components/dashboard/SpendingByBudget'
import { FinancialInsights } from '../components/dashboard/FinancialInsights'
import { RecentExpenses } from '../components/dashboard/RecentExpenses'
import { previousPeriod, parsePeriodStart, elapsedDayCount } from '../utils/period'
import { buildInsights } from '../utils/insights'

function DashboardPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options, isPending: optionsLoading, isError: optionsError } = useOptions()
  const [period, setPeriod] = useState<string | null>(null)
  const [selectedTopUpBudget, setSelectedTopUpBudget] = useState<string | null>(null)
  const [selectedTopUpHistoryBudget, setSelectedTopUpHistoryBudget] = useState<string | null>(null)
  const [addBudgetOpened, setAddBudgetOpened] = useState(false)
  const [deleteBudgetName, setDeleteBudgetName] = useState<string | null>(null)
  const [manageCategoriesBudget, setManageCategoriesBudget] = useState<string | null>(null)
  const [editBudget, setEditBudget] = useState<{ name: string; balance: number | undefined; alertThreshold: number | undefined; description: string | undefined } | null>(null)
  const [paydaySettingsOpened, setPaydaySettingsOpened] = useState(false)

  useEffect(() => {
    if (!period && periodsData && periodsData.periods.length > 0) {
      setPeriod(periodsData.periods[0])
    }
  }, [period, periodsData])

  const calendar = useMemo(() => periodsData?.calendar ?? [], [periodsData])
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

  // Perbandingan jumlah hari: N hari pertama periode ini dibandingkan dengan
  // N hari pertama periode sebelumnya. Untuk periode lama, bandingkan periode penuh.
  const comparison = useMemo(() => {
    if (!period || !prevPeriod) return { prevTotal: null as number | null, partial: false, days: null as number | null }
    const prevExpenses = prevExpensesData?.expenses ?? []
    const isRunning = period === periodsData?.currentPeriod

    if (!isRunning) {
      const total = prevExpenses.reduce((s, e) => s + e.amount, 0)
      return { prevTotal: total > 0 ? total : null, partial: false, days: null }
    }

    const days = elapsedDayCount(period, new Date(), calendar)
    const prevStart = parsePeriodStart(prevPeriod, calendar)
    if (days === null || !prevStart) return { prevTotal: null, partial: false, days: null }
    const cutoff = new Date(prevStart.getFullYear(), prevStart.getMonth(), prevStart.getDate() + days)
    const total = prevExpenses
      .filter((e) => new Date(e.dateTime.replace(' ', 'T')) < cutoff)
      .reduce((s, e) => s + e.amount, 0)
    return { prevTotal: total > 0 ? total : null, partial: true, days }
  }, [period, prevPeriod, prevExpensesData, calendar, periodsData?.currentPeriod])

  const comparisonNote = comparison.partial && comparison.days
    ? `dibanding ${comparison.days} hari pertama periode sebelumnya`
    : 'dibanding periode sebelumnya'

  const insights = useMemo(() => {
    if (!summary) return []
    return buildInsights({
      selectedTotal: summary.total,
      prevTotal: comparison.prevTotal,
      byBudget: summary.byBudget ?? [],
      budgets,
      expenses: expensesData?.expenses ?? [],
      comparisonNote,
    })
  }, [summary, comparison, budgets, comparisonNote, expensesData])

  return (
    <Container size="lg" px={{ base: 'md', sm: 'md' }} py={{ base: 'md', sm: 'md' }} pb={{ base: 88, sm: 'md' }}>
      <Stack gap="xl">
        <PageHeader
          eyebrow="Ringkasan"
          title="Dashboard"
          subtitle="Ringkasan keuangan dan budget kamu."
          right={
            <Group align="flex-end" gap="xs" wrap="nowrap">
              <Select
                w={{ base: 'auto', sm: 190 }}
                style={{ flex: 1, minWidth: 0 }}
                placeholder={periodsLoading ? 'Memuat...' : 'Pilih periode'}
                data={periods}
                value={period}
                onChange={setPeriod}
                searchable
                size="sm"
                aria-label="Periode"
              />
              <Button
                variant="light"
                size="sm"
                leftSection={<IconCalendarEvent size={16} />}
                onClick={() => setPaydaySettingsOpened(true)}
              >
                Atur gajian
              </Button>
            </Group>
          }
        />

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
            onManageCategories={(name) => setManageCategoriesBudget(name)}
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
            isLoading={summaryLoading || expensesLoading}
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
        {manageCategoriesBudget && (
          <ManageCategoriesModal
            budget={manageCategoriesBudget}
            onClose={() => setManageCategoriesBudget(null)}
          />
        )}
        <PaydaySettingsModal
          opened={paydaySettingsOpened}
          calendar={calendar}
          currentPeriod={periodsData?.currentPeriod ?? period ?? ''}
          onClose={() => setPaydaySettingsOpened(false)}
        />
      </Stack>
    </Container>
  )
}

export default DashboardPage
