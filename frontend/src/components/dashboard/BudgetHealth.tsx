import { Fragment, useMemo } from 'react'
import {
  ActionIcon,
  Button,
  Divider,
  Group,
  Menu,
  Popover,
  Skeleton,
  Stack,
  Text,
} from '@mantine/core'
import {
  IconAlertTriangle,
  IconClipboardList,
  IconDots,
  IconInfoCircle,
  IconPencil,
  IconPlus,
  IconTag,
  IconTrash,
} from '@tabler/icons-react'
import { formatCurrency } from '../../utils/currency'
import type { BudgetSummary } from '../../types/expense'
import { DashboardSection } from './DashboardSection'

export interface BudgetCardData {
  name: string
  balance: number
  alertThreshold: number
  description?: string
}

export function BudgetHealth({
  budgets,
  byBudget,
  isLoading,
  isError,
  onAddBudget,
  onTopUp,
  onHistory,
  onManageCategories,
  onEdit,
  onDelete,
}: {
  budgets: BudgetCardData[]
  byBudget: BudgetSummary[]
  isLoading: boolean
  isError: boolean
  onAddBudget: () => void
  onTopUp: (name: string) => void
  onHistory: (name: string) => void
  onManageCategories: (name: string) => void
  onEdit: (budget: { name: string; balance: number | undefined; alertThreshold: number | undefined; description: string | undefined }) => void
  onDelete: (name: string) => void
}) {
  const sorted = useMemo(() => {
    const level = (b: BudgetCardData): number => {
      if (b.balance < 0) return 0
      if (b.alertThreshold > 0 && b.balance < b.alertThreshold) return 1
      return 2
    }
    return [...budgets].sort((a, b) => level(a) - level(b) || a.balance - b.balance)
  }, [budgets])

  const infoOf = (name: string): BudgetSummary | undefined => byBudget.find((b) => b.budget === name)

  return (
    <DashboardSection
      title="Kesehatan Budget"
      subtitle={`${budgets.length} budget aktif`}
      action={
        <Button size="sm" radius="md" onClick={onAddBudget}>
          + Budget
        </Button>
      }
    >
      {isLoading ? (
        <Stack gap="sm">
          <Skeleton h={40} />
          <Skeleton h={40} />
          <Skeleton h={40} />
        </Stack>
      ) : isError ? (
        <Text size="sm" c="red">
          Gagal memuat budget.
        </Text>
      ) : budgets.length === 0 ? (
        <Text c="dimmed" py="md">
          Belum ada budget. Tambahkan budget pertama kamu.
        </Text>
      ) : (
        <Stack gap={0}>
          {sorted.map((b, i) => {
            const info = infoOf(b.name)
            const negative = b.balance < 0
            const belowThreshold = b.alertThreshold > 0 && b.balance < b.alertThreshold
            const warning = negative || belowThreshold
            return (
              <Fragment key={b.name}>
                {i > 0 && <Divider my="sm" />}
                <Group justify="space-between" align="center" wrap="nowrap" py="xs">
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <Group gap={6} wrap="nowrap">
                      {warning && (
                        <IconAlertTriangle size={15} color={negative ? 'var(--mantine-color-red-6)' : 'var(--mantine-color-orange-6)'} aria-hidden />
                      )}
                      <Text fw={600} truncate>
                        {b.name}
                      </Text>
                      {b.description && (
                        <Popover width={280} position="bottom-start" withArrow shadow="md">
                          <Popover.Target>
                            <ActionIcon
                              size="xs"
                              variant="subtle"
                              color="brand"
                              aria-label={`Deskripsi ${b.name}`}
                              style={{ flexShrink: 0 }}
                            >
                              <IconInfoCircle size={14} />
                            </ActionIcon>
                          </Popover.Target>
                          <Popover.Dropdown>
                            <Text size="sm">{b.description}</Text>
                          </Popover.Dropdown>
                        </Popover>
                      )}
                    </Group>
                    <Text size="xs" c="dimmed" truncate>
                      {negative
                        ? `-${formatCurrency(Math.abs(b.balance))}`
                        : `${formatCurrency(b.balance)} tersisa`}
                      {info ? ` · ${info.count} transaksi` : ''}
                    </Text>
                    {belowThreshold && (
                      <Text size="xs" c="orange" fw={600}>
                        di bawah ambang {formatCurrency(b.alertThreshold)}
                      </Text>
                    )}
                  </div>
                  <Menu shadow="md" width={200} position="bottom-end">
                    <Menu.Target>
                      <ActionIcon variant="subtle" color="gray" aria-label={`Menu ${b.name}`}>
                        <IconDots size={18} />
                      </ActionIcon>
                    </Menu.Target>
                    <Menu.Dropdown>
                      <Menu.Item leftSection={<IconPlus size={16} />} onClick={() => onTopUp(b.name)}>
                        Top-up
                      </Menu.Item>
                      <Menu.Item leftSection={<IconClipboardList size={16} />} onClick={() => onHistory(b.name)}>
                        Riwayat top-up
                      </Menu.Item>
                      <Menu.Item leftSection={<IconTag size={16} />} onClick={() => onManageCategories(b.name)}>
                        Kelola Kategori
                      </Menu.Item>
                      <Menu.Item leftSection={<IconPencil size={16} />} onClick={() => onEdit({ name: b.name, balance: b.balance, alertThreshold: b.alertThreshold, description: b.description })}>
                        Edit Budget
                      </Menu.Item>
                      <Menu.Divider />
                      <Menu.Item color="red" leftSection={<IconTrash size={16} />} onClick={() => onDelete(b.name)}>
                        Hapus
                      </Menu.Item>
                    </Menu.Dropdown>
                  </Menu>
                </Group>
              </Fragment>
            )
          })}
        </Stack>
      )}
    </DashboardSection>
  )
}
