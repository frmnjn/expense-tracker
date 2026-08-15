import { ActionIcon, Badge, Divider, Group, Paper, Text } from '@mantine/core'
import dayjs from 'dayjs'
import { formatCurrency } from '../utils/currency'
import type { Expense } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'

export function TransactionCard({
  expense,
  onViewPhoto,
  onEdit,
  onDelete,
}: {
  expense: Expense
  onViewPhoto: (expense: Expense) => void
  onEdit: (expense: Expense) => void
  onDelete: (expense: Expense) => void
}) {
  return (
    <Paper key={expense.id} withBorder p="sm" radius="md">
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Text fw={600} truncate style={{ flex: 1, minWidth: 0 }}>
          {expense.name}
        </Text>
        <Text fw={700} ff="monospace" style={{ whiteSpace: 'nowrap' }}>
          {formatCurrency(expense.amount)}
        </Text>
      </Group>
      <Group justify="space-between" mt={2} wrap="nowrap">
        <Text size="xs" c="dimmed">
          {dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}
        </Text>
        <Badge size="sm" variant="light" color="gray">
          {expense.budget}
        </Badge>
      </Group>
      <Divider mt="sm" mb="xs" />
      <Group justify="flex-end" gap={8}>
        {expense.hasPhoto && (
          <ActionIcon variant="light" color="gray" size="lg" onClick={() => onViewPhoto(expense)} aria-label="Lihat foto">
            📷
          </ActionIcon>
        )}
        <ActionIcon variant="light" color="blue" size="lg" disabled={!expense.id} onClick={() => onEdit(expense)} aria-label="Edit">
          ✎
        </ActionIcon>
        <ActionIcon variant="light" color="red" size="lg" disabled={!expense.id} onClick={() => onDelete(expense)} aria-label="Hapus">
          🗑
        </ActionIcon>
      </Group>
    </Paper>
  )
}
