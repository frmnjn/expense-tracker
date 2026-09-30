import { ActionIcon, Divider, Group, Paper, Stack, Text } from '@mantine/core'
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
      <Text size="xs" c="dimmed" mt={2}>
        {dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}
      </Text>
      <Stack gap={2} mt={4}>
        <Text size="xs" c="dimmed">
          Budget: <Text span fw={600}>{expense.budget}</Text>
        </Text>
        {expense.category ? (
          <Text size="xs" c="dimmed">
            Category: <Text span fw={600}>{expense.category}</Text>
          </Text>
        ) : null}
      </Stack>
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
