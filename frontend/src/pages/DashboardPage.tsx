import { useMemo, useState } from 'react'
import {
  ActionIcon,
  Anchor,
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
import { useTopUps } from '../hooks/useTopUps'
import ColorSchemeToggle from '../components/ColorSchemeToggle'
import TopUpModal from '../components/TopUpModal'
import { formatCurrency } from '../utils/currency'
import type { BudgetSummary } from '../types/expense'

const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

function StatCard({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <Paper withBorder p="lg" radius="md" shadow="sm">
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text size="xl" fw={700} ff="monospace" c={color}>
        {value}
      </Text>
    </Paper>
  )
}

function DashboardPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options } = useOptions()
  const { data: topUpsData } = useTopUps()
  const [period, setPeriod] = useState<string | null>(null)
  const [selectedTopUpBudget, setSelectedTopUpBudget] = useState<string | null>(null)
  const { data: summary, isPending: summaryLoading } = useSummary(period)
  const { data: trend } = useTrend(3)
  const isMobile = useMediaQuery('(max-width: 48em)')

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])

  const balanceOf = (name: string): number | undefined => options?.budgets.find((b) => b.name === name)?.balance
  const negativeCount = (options?.budgets ?? []).filter((b) => b.balance < 0).length
  const spendingOf = new Map<string, number>((summary?.byBudget ?? []).map((b: BudgetSummary) => [b.budget, b.amount]))
  const topUps = useMemo(() => (topUpsData?.topUps ?? []).slice().reverse(), [topUpsData])

  const allBudgets = useMemo(() => {
    const names = new Set<string>([
      ...(options?.budgets ?? []).map((b) => b.name),
      ...(summary?.byBudget ?? []).map((b) => b.budget),
    ])
    return Array.from(names).sort()
  }, [options, summary])

  const topBudgets = summary?.byBudget.slice(0, 3) ?? []
  const selectedTopUpBalance = selectedTopUpBudget ? balanceOf(selectedTopUpBudget) : undefined

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
            <Stack gap="sm">
              {allBudgets.map((name) => {
                const balance = balanceOf(name)
                const spending = spendingOf.get(name)
                return (
                  <Paper key={name} withBorder p="sm" radius="md">
                    <Group justify="space-between" mb={4}>
                      <Text fw={600} style={{ wordBreak: 'break-word' }}>
                        {name}
                      </Text>
                      <Group gap="xs" wrap="nowrap">
                        <Text
                          fw={700}
                          ff="monospace"
                          c={balance !== undefined ? balanceColor(balance) : undefined}
                        >
                          {balance !== undefined ? formatCurrency(balance) : '-'}
                        </Text>
                        <ActionIcon
                          size="sm"
                          variant="subtle"
                          color="green"
                          onClick={() => setSelectedTopUpBudget(name)}
                        >
                          <span>+</span>
                        </ActionIcon>
                      </Group>
                    </Group>
                    {spending !== undefined && (
                      <Text size="sm" c="dimmed">
                        Pengeluaran periode: {formatCurrency(spending)}
                      </Text>
                    )}
                  </Paper>
                )
              })}
            </Stack>
          ) : (
            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
              {allBudgets.map((name) => {
                const balance = balanceOf(name)
                const spending = spendingOf.get(name)
                return (
                  <Paper key={name} withBorder p="sm" radius="md">
                    <Group justify="space-between" mb={4}>
                      <Text fw={600} style={{ wordBreak: 'break-word' }}>
                        {name}
                      </Text>
                      <Group gap="xs" wrap="nowrap">
                        <Text
                          fw={700}
                          ff="monospace"
                          c={balance !== undefined ? balanceColor(balance) : undefined}
                        >
                          {balance !== undefined ? formatCurrency(balance) : '-'}
                        </Text>
                        <ActionIcon
                          size="sm"
                          variant="subtle"
                          color="green"
                          onClick={() => setSelectedTopUpBudget(name)}
                        >
                          <span>+</span>
                        </ActionIcon>
                      </Group>
                    </Group>
                    {spending !== undefined && (
                      <Text size="sm" c="dimmed">
                        Pengeluaran periode: {formatCurrency(spending)}
                      </Text>
                    )}
                  </Paper>
                )
              })}
            </SimpleGrid>
          )}
        </Paper>

        {!period ? (
          <Text c="dimmed" ta="center" py="lg">
            Pilih periode untuk melihat ringkasan pengeluaran.
          </Text>
        ) : (
          <>
            <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
              <StatCard label="Total Pengeluaran" value={formatCurrency(summary?.total ?? 0)} />
              <StatCard label="Jumlah Transaksi" value={String(summary?.count ?? 0)} />
              <StatCard
                label="Budget Saldo Negatif"
                value={String(negativeCount)}
                color={negativeCount > 0 ? 'red' : undefined}
              />
            </SimpleGrid>

            <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="md" shadow="sm">
              <Title order={4} mb="sm">
                Pengeluaran Terbesar
              </Title>
              {topBudgets.length === 0 ? (
                <Text c="dimmed">Belum ada pengeluaran pada periode ini.</Text>
              ) : (
                <Stack gap="xs">
                  {topBudgets.map((b, i) => (
                    <Group key={b.budget} justify="space-between">
                      <Text>
                        {i + 1}. {b.budget}
                      </Text>
                      <Text ff="monospace">{formatCurrency(b.amount)}</Text>
                    </Group>
                  ))}
                </Stack>
              )}
            </Paper>
          </>
        )}

        {topUps.length > 0 && (
          <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="md" shadow="sm">
            <Title order={4} mb="sm">
              Riwayat Top-up
            </Title>
            <Stack gap="xs">
              {topUps.map((t) => (
                <Group key={t.id} justify="space-between">
                  <div>
                    <Text size="sm">{t.budget}</Text>
                    <Text size="xs" c="dimmed">
                      {t.dateTime}
                    </Text>
                  </div>
                  <Text ff="monospace" fw={600} c="green">
                    +{formatCurrency(t.amount)}
                  </Text>
                </Group>
              ))}
            </Stack>
          </Paper>
        )}

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
      </Stack>
    </Container>
  )
}

export default DashboardPage
