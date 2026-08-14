import { useEffect, useMemo, useState } from 'react'
import {
  ActionIcon,
  Button,
  Group,
  Loader,
  Modal,
  NumberInput,
  Paper,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { DateTimePicker } from '@mantine/dates'
import dayjs from 'dayjs'
import { useInvoiceDetail, useCreateExpenseBatch } from '../hooks/useScan'
import { useOptions } from '../hooks/useOptions'
import { getInvoicePhotoUrl } from '../services/expense'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'
import { InvoiceThumb } from './InvoiceThumb'

interface EditItem {
  key: string
  name: string
  amount: number
  budget: string | null
}

const MAX_DESC = 1000

function toEditItems(analysis: { items: { name: string; amount: number; suggestedBudget?: string }[] } | undefined, budgetNames: string[]): EditItem[] {
  return (analysis?.items ?? []).map((it, i) => ({
    key: `${Date.now()}-${i}`,
    name: it.name ?? '',
    amount: Number(it.amount) || 0,
    budget: it.suggestedBudget && budgetNames.includes(it.suggestedBudget) ? it.suggestedBudget : null,
  }))
}

function ReviewModal({
  invoiceId,
  onClose,
  onSubmitted,
}: {
  invoiceId: string
  onClose: () => void
  onSubmitted: () => void
}) {
  const { data, isPending } = useInvoiceDetail(invoiceId)
  const { data: options } = useOptions()
  const batch = useCreateExpenseBatch()
  const [items, setItems] = useState<EditItem[]>([])
  const [groupNames, setGroupNames] = useState<Record<string, string>>({})

  const budgetNames = useMemo(() => (options?.budgets ?? []).map((b) => b.name), [options])
  const budgetOptions = useMemo(() => budgetNames.map((n) => ({ value: n, label: n })), [budgetNames])

  const analysis = data?.status === 'TO_REVIEW' ? data.analysis : undefined
  const storeName = analysis?.storeName?.trim() || 'Belanja'

  const initialDateTime = useMemo(() => {
    const now = dayjs()
    const ai = analysis?.dateTime?.trim() || ''
    if (ai) {
      const full = dayjs(ai, 'YYYY-MM-DD HH:mm:ss', true)
      if (full.isValid()) return full.format('YYYY-MM-DD HH:mm')
      const min = dayjs(ai, 'YYYY-MM-DD HH:mm', true)
      if (min.isValid()) return min.format('YYYY-MM-DD HH:mm')
      const dateOnly = dayjs(ai, 'YYYY-MM-DD', true)
      if (dateOnly.isValid()) return `${dateOnly.format('YYYY-MM-DD')} ${now.format('HH:mm')}`
    }
    return now.format('YYYY-MM-DD HH:mm')
  }, [analysis])

  const [dateTime, setDateTime] = useState(initialDateTime)
  useEffect(() => {
    setDateTime(initialDateTime)
  }, [initialDateTime])

  useEffect(() => {
    if (analysis) {
      setItems(toEditItems(analysis, budgetNames))
      setGroupNames({})
    }
    // reset item saat invoice berubah
  }, [analysis, budgetNames, invoiceId])

  const updateItem = (key: string, patch: Partial<EditItem>) => {
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...patch } : it)))
  }

  const addItem = () => {
    setItems((prev) => [...prev, { key: `${Date.now()}-${prev.length}`, name: '', amount: 0, budget: null }])
  }

  const removeItem = (key: string) => setItems((prev) => prev.filter((it) => it.key !== key))

  const groups = useMemo(() => {
    const map = new Map<string, EditItem[]>()
    for (const it of items) {
      if (!it.budget) continue
      map.set(it.budget, [...(map.get(it.budget) ?? []), it])
    }
    return Array.from(map.entries())
  }, [items])

  const itemsSum = items.reduce((s, it) => s + (Number(it.amount) || 0), 0)
  const aiTotal = analysis && Number(analysis.total) > 0 ? Number(analysis.total) : null
  const mismatch = aiTotal !== null && aiTotal !== itemsSum
  const missingBudget = items.some((it) => !it.budget)
  const invalid =
    items.some((it) => it.name.trim() === '' || !it.budget || Number(it.amount) === 0) ||
    groups.some(([, list]) => list.reduce((s, it) => s + Number(it.amount), 0) <= 0)

  const groupName = (budget: string) => groupNames[budget] ?? storeName

  const buildDescription = (list: EditItem[]): string => {
    const full = list
      .map((it) => `${it.name.trim()} ${formatCurrency(Number(it.amount))}`)
      .filter(Boolean)
      .join(', ')
    if (full.length <= MAX_DESC) return full
    const truncated = full.slice(0, MAX_DESC).replace(/,\s*$/, '')
    return `${truncated}…`
  }

  const handleSubmit = () => {
    if (invalid || batch.isPending) return
    const groupsPayload = groups.map(([budget, list]) => {
      const amount = list.reduce((s, it) => s + Number(it.amount), 0)
      const description = buildDescription(list)
      return {
        name: groupName(budget).trim() || storeName,
        budget,
        amount,
        description,
      }
    })
    batch.mutate(
      { dateTime, invoiceId, groups: groupsPayload },
      {
        onSuccess: () => {
          notifications.show({
            title: 'Berhasil',
            message: `${groupsPayload.length} pengeluaran berhasil dibuat`,
            color: 'green',
          })
          onSubmitted()
        },
        onError: (error) => {
          notifications.show({ title: 'Gagal', message: getErrorMessage(error), color: 'red' })
        },
      },
    )
  }

  return (
    <Modal opened onClose={onClose} title="Review Hasil Analisis" centered size="md">
      {isPending || data?.status === 'ANALYZING' ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : data?.status === 'ERROR' ? (
        <Text size="sm" c="red">
          Analisis gagal{data.errorMessage ? `: ${data.errorMessage}` : ''}. Coba lagi dari halaman Scan.
        </Text>
      ) : (
        <Stack>
          <Paper withBorder p="sm" radius="md">
            <Group wrap="nowrap" align="flex-start" gap="md">
              <InvoiceThumb
                type={data?.type ?? 'image'}
                url={getInvoicePhotoUrl(invoiceId)}
                h={80}
              />
              <Stack gap={2} flex={1}>
                <Text size="sm" fw={600}>
                  {storeName}
                </Text>
                {data?.name ? (
                  <Text size="xs" c="dimmed" truncate title={data.name}>
                    {data.name}
                  </Text>
                ) : null}
                <Group justify="space-between" align="center">
                  <Text size="sm" c="dimmed">
                    Tanggal belanja
                  </Text>
                  <DateTimePicker
                    value={dateTime}
                    onChange={(v) => setDateTime(v ? dayjs(v).format('YYYY-MM-DD HH:mm') : dayjs().format('YYYY-MM-DD HH:mm'))}
                    valueFormat="DD MMM YYYY HH:mm"
                    dropdownType="modal"
                    size="xs"
                    style={{ width: 200 }}
                  />
                </Group>
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">
                    Total struk
                  </Text>
                  <Text size="sm" fw={600}>
                    {aiTotal === null ? '-' : formatCurrency(aiTotal)}
                  </Text>
                </Group>
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">
                    Jumlah item
                  </Text>
                  <Text size="sm" fw={600} c={mismatch ? 'orange' : undefined}>
                    {formatCurrency(itemsSum)}
                  </Text>
                </Group>
                {mismatch && (
                  <Text size="xs" c="orange">
                    ⚠️ Total item tidak sama dengan total struk. Periksa kembali.
                  </Text>
                )}
              </Stack>
            </Group>
          </Paper>

          <Text size="sm" fw={600}>
            Pengeluaran per budget
          </Text>
          {groups.length === 0 && (
            <Text size="sm" c="dimmed">
              Belum ada item dengan budget. Assign budget tiap item di bawah.
            </Text>
          )}
          {groups.map(([budget, list]) => {
            const amount = list.reduce((s, it) => s + Number(it.amount), 0)
            return (
              <Paper key={budget} withBorder p="sm" radius="md">
                <Group justify="space-between" mb={4}>
                  <Text size="sm" fw={600}>
                    {budget}
                  </Text>
                  <Text size="sm" fw={700}>
                    {formatCurrency(amount)}
                  </Text>
                </Group>
                <TextInput
                  size="xs"
                  label="Nama pengeluaran"
                  value={groupName(budget)}
                  onChange={(e) =>
                    setGroupNames((prev) => ({ ...prev, [budget]: e.currentTarget.value }))
                  }
                />
                <Text size="xs" c="dimmed" mt={6}>
                  {list.map((it) => it.name.trim()).filter(Boolean).join(', ') || '—'}
                </Text>
              </Paper>
            )
          })}

          <Text size="sm" fw={600}>
            Item
          </Text>
          {items.map((it) => (
            <Group key={it.key} wrap="nowrap" align="flex-end" gap="xs">
              <TextInput
                size="xs"
                placeholder="Nama"
                value={it.name}
                onChange={(e) => updateItem(it.key, { name: e.currentTarget.value })}
                style={{ flex: 1.4 }}
              />
              <NumberInput
                size="xs"
                placeholder="0"
                value={it.amount}
                onChange={(v) => updateItem(it.key, { amount: Number(v) || 0 })}
                allowNegative
                prefix="Rp"
                thousandSeparator="."
                decimalSeparator=","
                style={{ flex: 1 }}
              />
              <Select
                size="xs"
                placeholder="Budget"
                data={budgetOptions}
                value={it.budget}
                onChange={(v) => updateItem(it.key, { budget: v })}
                searchable
                style={{ flex: 1.2 }}
              />
              <ActionIcon color="red" variant="subtle" onClick={() => removeItem(it.key)}>
                ✕
              </ActionIcon>
            </Group>
          ))}
          <Button variant="light" size="xs" onClick={addItem} disabled={batch.isPending}>
            + Tambah item
          </Button>

          <Button
            fullWidth
            size="md"
            onClick={handleSubmit}
            loading={batch.isPending}
            disabled={invalid}
          >
            Buat {groups.length} Pengeluaran
          </Button>
          {missingBudget && (
            <Text size="xs" c="orange" ta="center">
              Ada item yang belum diassign budget.
            </Text>
          )}
        </Stack>
      )}
    </Modal>
  )
}

export default ReviewModal
