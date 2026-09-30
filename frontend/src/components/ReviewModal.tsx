import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ActionIcon,
  Box,
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
import { useMediaQuery } from '@mantine/hooks'
import { DateTimePicker } from '@mantine/dates'
import dayjs from 'dayjs'
import { useInvoiceDetail, useCreateExpenseBatch } from '../hooks/useScan'
import { useOptions } from '../hooks/useOptions'
import { getInvoicePhotoUrl } from '../services/expense'
import type { BudgetOption } from '../types/expense'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'
import { InvoiceThumb } from './InvoiceThumb'
import { useToast } from './Toast'

interface EditItem {
  key: string
  name: string
  amount: number
  budget: string | null
  category: string | null
}

const MAX_DESC = 1000

const DEFAULT_CURRENCY = 'IDR'

const groupKey = (budget: string, category: string | null) => `${budget}|||${category ?? ''}`

function toEditItems(
  analysis: { items: { name: string; amount: number; suggestedBudget?: string; suggestedCategory?: string }[] } | undefined,
  budgets: BudgetOption[],
): EditItem[] {
  return (analysis?.items ?? []).map((it, i) => {
    const budget = budgets.find((b) => b.name === it.suggestedBudget)
    const category =
      budget && it.suggestedCategory && budget.categories.some((c) => c.name === it.suggestedCategory)
        ? it.suggestedCategory
        : null
    return {
      key: `${Date.now()}-${i}`,
      name: it.name ?? '',
      amount: Number(it.amount) || 0,
      budget: budget ? budget.name : null,
      category,
    }
  })
}

/** Format angka desimal id-ID (koma), tanpa simbol, tanpa pembulatan. */
function formatNumber(value: number): string {
  return new Intl.NumberFormat('id-ID', { maximumFractionDigits: 10 }).format(value)
}

/** Format kurs: Rp{angka} (tanpa simbol, tanpa pembulatan). */
function formatRate(rate: number): string {
  return `Rp${formatNumber(rate)}`
}

function ReviewModal({
  invoiceId,
  onClose,
  onSubmitted,
}: {
  invoiceId: string
  onClose: () => void
  onSubmitted: () => void
}) {  const { data, isPending } = useInvoiceDetail(invoiceId)
  const { data: options } = useOptions()
  const batch = useCreateExpenseBatch()
  const toast = useToast()
  const isMobile = useMediaQuery('(max-width: 48em)')
  const [items, setItems] = useState<EditItem[]>([])
  const [groupNames, setGroupNames] = useState<Record<string, string>>({})
  const [flashKey, setFlashKey] = useState<string | null>(null)
  const itemRefs = useRef<Record<string, HTMLElement | null>>({})
  const summaryRef = useRef<HTMLDivElement | null>(null)
  const [flashSummary, setFlashSummary] = useState(false)
  const [showBack, setShowBack] = useState(false)

  const indexById = useMemo(() => {
    const map = new Map<string, number>()
    items.forEach((it, i) => map.set(it.key, i))
    return map
  }, [items])

  const budgets = useMemo(() => options?.budgets ?? [], [options])
  const budgetNames = useMemo(() => budgets.map((b) => b.name), [budgets])
  const budgetOptions = useMemo(() => budgetNames.map((n) => ({ value: n, label: n })), [budgetNames])

  const categoryOptionsOf = (budget: string | null) =>
    (budgets.find((b) => b.name === budget)?.categories ?? []).map((c) => ({ value: c.name, label: c.name }))

  const analysis = data?.status === 'TO_REVIEW' ? data.analysis : undefined
  const storeName = analysis?.storeName?.trim() || 'Belanja'

  // Munculkan tombol "kembali ke ringkasan" saat section ringkasan tergulung ke atas.
  // Baris tombol di header selalu dirender (tinggi header konstan), jadi perubahan
  // showBack tidak menggeser layout dan tidak memicu kedip.
  useEffect(() => {
    const summary = summaryRef.current
    if (!summary) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry) setShowBack(!entry.isIntersecting)
      },
      { threshold: 0 },
    )
    observer.observe(summary)
    return () => observer.disconnect()
  }, [analysis])

  // Konversi mata uang: bila currency bukan IDR, tampilkan info kurs & nilau asli.
  const currency = analysis?.currency?.trim() || DEFAULT_CURRENCY
  const isConverted = currency.toUpperCase() !== DEFAULT_CURRENCY
  const exchangeRate = analysis?.exchangeRate
  const exchangeDate = analysis?.exchangeDate
  const originalTotal = analysis?.originalTotal

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
      setItems(toEditItems(analysis, budgets))
      setGroupNames({})
    }
    // reset item saat invoice berubah
  }, [analysis, budgets, invoiceId])

  const updateItem = (key: string, patch: Partial<EditItem>) => {
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...patch } : it)))
  }

  const addItem = () => {
    setItems((prev) => [...prev, { key: `${Date.now()}-${prev.length}`, name: '', amount: 0, budget: null, category: null }])
  }

  const removeItem = (key: string) => setItems((prev) => prev.filter((it) => it.key !== key))

  const jumpToItem = (key: string) => {
    itemRefs.current[key]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setFlashKey(key)
    window.setTimeout(() => setFlashKey((k) => (k === key ? null : k)), 1500)
  }

  const scrollToSummary = () => {
    summaryRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    setFlashSummary(true)
    window.setTimeout(() => setFlashSummary(false), 1500)
  }

  const groups = useMemo(() => {
    const map = new Map<string, { budget: string; category: string | null; items: EditItem[] }>()
    for (const it of items) {
      if (!it.budget) continue
      const key = groupKey(it.budget, it.category)
      const existing = map.get(key)
      if (existing) {
        existing.items.push(it)
      } else {
        map.set(key, { budget: it.budget, category: it.category, items: [it] })
      }
    }
    return Array.from(map.entries())
  }, [items])

  const itemsSum = items.reduce((s, it) => s + (Number(it.amount) || 0), 0)
  const aiTotal = analysis && Number(analysis.total) > 0 ? Number(analysis.total) : null
  const mismatch = aiTotal !== null && aiTotal !== itemsSum

  const problems = useMemo(() => {
    const list: string[] = []
    items.forEach((it, i) => {
      const name = it.name.trim()
      const label = name ? `Item ${i + 1} (${name})` : `Item ${i + 1}`
      if (!name) list.push(`${label}: nama belum diisi`)
      if (!it.budget) list.push(`${label}: budget belum dipilih`)
      if (Number(it.amount) === 0) list.push(`${label}: nominal masih 0`)
    })
    for (const [, group] of groups) {
      const sum = group.items.reduce((s, it) => s + Number(it.amount), 0)
      if (sum <= 0) {
        const label = group.category ? `${group.budget} / ${group.category}` : group.budget
        list.push(`Budget ${label}: totalnya harus lebih dari 0`)
      }
    }
    return list
  }, [items, groups])
  const invalid = problems.length > 0

  const groupName = (key: string) => groupNames[key] ?? storeName

  const buildDescription = (list: EditItem[]): string => {
    const full = list
      .map((it) => `${it.name.trim()} ${formatCurrency(Number(it.amount))}`)
      .filter(Boolean)
      .join(', ')
    const base = full.length <= MAX_DESC ? full : `${full.slice(0, MAX_DESC).replace(/,\s*$/, '')}…`
    if (!isConverted) return base
    const rateStr = exchangeRate ? `Kurs 1 ${currency} = ${formatRate(exchangeRate)}` : 'kurs tidak diketahui'
    const dateStr = exchangeDate ? ` pada ${exchangeDate}` : ''
    const originalStr = originalTotal ? `Nilai asli: ${formatNumber(originalTotal)} ${currency}.` : ''
    const note = `Hasil konversi dari ${currency} ke IDR. ${rateStr}${dateStr}. ${originalStr}`
    const combined = `${note} ${base}`
    return combined.length <= MAX_DESC ? combined : `${combined.slice(0, MAX_DESC).replace(/,\s*$/, '')}…`
  }

  const handleSubmit = () => {
    if (invalid || batch.isPending) return
    const groupsPayload = groups.map(([key, group]) => {
      const amount = group.items.reduce((s, it) => s + Number(it.amount), 0)
      const description = buildDescription(group.items)
      return {
        name: groupName(key).trim() || storeName,
        budget: group.budget,
        category: group.category ?? undefined,
        amount,
        description,
      }
    })
    batch.mutate(
      { dateTime, invoiceId, groups: groupsPayload },
      {
        onSuccess: () => {
          toast.success(`${groupsPayload.length} pengeluaran berhasil dibuat`, { title: 'Berhasil' })
          onSubmitted()
        },
        onError: (error) => {
          toast.error(getErrorMessage(error), { title: 'Gagal' })
        },
      },
    )
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={
        analysis ? (
          <Stack gap={4}>
            <Text fw={600}>Review Hasil Analisis</Text>
            <Button
              size="compact-xs"
              variant="light"
              onClick={scrollToSummary}
              style={{
                visibility: showBack ? 'visible' : 'hidden',
                opacity: showBack ? 1 : 0,
                pointerEvents: showBack ? 'auto' : 'none',
              }}
            >
              ↑ Pengeluaran per budget
            </Button>
          </Stack>
        ) : (
          'Review Hasil Analisis'
        )
      }
      centered
      fullScreen={isMobile}
      size="md"
      styles={{ body: { maxHeight: isMobile ? undefined : 'calc(100dvh - 140px)', overflowY: 'auto' } }}
    >
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
              <Box w={80} style={{ flexShrink: 0 }}>
                <InvoiceThumb
                  type={data?.type ?? 'image'}
                  url={getInvoicePhotoUrl(invoiceId)}
                  h={80}
                />
              </Box>
              <Stack gap={2} flex={1}>
                <Text size="sm" fw={600}>
                  {storeName}
                </Text>
                {data?.name ? (
                  <Text size="xs" c="dimmed" truncate title={data.name}>
                    {data.name}
                  </Text>
                ) : null}
                <Group justify="space-between" align="center" wrap="nowrap" gap="xs">
                  <Text size="sm" c="dimmed">
                    Tanggal belanja
                  </Text>
                  <DateTimePicker
                    value={dateTime}
                    onChange={(v) => setDateTime(v ? dayjs(v).format('YYYY-MM-DD HH:mm') : dayjs().format('YYYY-MM-DD HH:mm'))}
                    valueFormat="DD MMM YYYY HH:mm"
                    dropdownType="modal"
                    size="xs"
                    style={{ width: isMobile ? 150 : 200 }}
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

          {isConverted && (
            <Paper withBorder p="sm" radius="md" style={{ background: 'rgba(255, 193, 7, 0.12)' }}>
              <Text size="xs" fw={600} c="orange">
                ⚠️ Ditampilkan dalam IDR. Hasil konversi dari {currency} ke IDR (kira-kira, tidak persis).
              </Text>
              {exchangeRate && exchangeDate ? (
                <Text size="xs" c="dimmed" mt={4}>
                  Kurs pada {exchangeDate} = {formatRate(exchangeRate)} per 1 {currency}
                </Text>
              ) : (
                <Text size="xs" c="dimmed" mt={4}>
                  Kurs tidak diketahui, periksa manual.
                </Text>
              )}
              {originalTotal ? (
                <Text size="xs" c="dimmed" mt={2}>
                  Total asli: {formatNumber(originalTotal)} {currency}
                </Text>
              ) : null}
            </Paper>
          )}

          <Box ref={summaryRef}>
            <Text
              size="sm"
              fw={600}
              style={
                flashSummary
                  ? { background: 'rgba(255, 193, 7, 0.12)', borderRadius: 4, padding: '2px 4px' }
                  : undefined
              }
            >
              Pengeluaran per budget
            </Text>
          </Box>
          {groups.length === 0 && (
            <Text size="sm" c="dimmed">
              Belum ada item dengan budget. Assign budget tiap item di bawah.
            </Text>
          )}
          {groups.map(([key, group]) => {
            const amount = group.items.reduce((s, it) => s + Number(it.amount), 0)
            const title = group.category ? `${group.budget} / ${group.category}` : group.budget
            return (
              <Paper key={key} withBorder p="sm" radius="md">
                <Group justify="space-between" mb={4}>
                  <Text size="sm" fw={600}>
                    {title}
                  </Text>
                  <Text size="sm" fw={700}>
                    {formatCurrency(amount)}
                  </Text>
                </Group>
                <TextInput
                  size="xs"
                  label="Nama pengeluaran"
                  value={groupName(key)}
                  onChange={(e) =>
                    setGroupNames((prev) => ({ ...prev, [key]: e.currentTarget.value }))
                  }
                />
                <Group gap={6} mt={6}>
                  {group.items.map((it) => (
                    <Text
                      key={it.key}
                      size="xs"
                      c="blue"
                      style={{ cursor: 'pointer', textDecoration: 'underline' }}
                      onClick={() => jumpToItem(it.key)}
                    >
                      {it.name.trim() || `Item ${(indexById.get(it.key) ?? 0) + 1}`}
                    </Text>
                  ))}
                </Group>
              </Paper>
            )
          })}

          <Text size="sm" fw={600}>
            Item
          </Text>
          {items.map((it, idx) =>
            isMobile ? (
              <Paper
                key={it.key}
                ref={(el) => {
                  itemRefs.current[it.key] = el
                }}
                withBorder
                p="sm"
                radius="md"
                style={
                  flashKey === it.key
                    ? { borderColor: 'var(--mantine-color-yellow-6)', background: 'rgba(255, 193, 7, 0.12)' }
                    : undefined
                }
              >
                <Group justify="space-between" align="center" mb="xs">
                  <Text size="sm" fw={600}>
                    Item {idx + 1}
                  </Text>
                  <ActionIcon color="red" variant="subtle" onClick={() => removeItem(it.key)} aria-label="Hapus item">
                    ✕
                  </ActionIcon>
                </Group>
                <Stack gap="xs">
                  <TextInput
                    size="xs"
                    label="Nama"
                    placeholder="Nama"
                    value={it.name}
                    onChange={(e) => updateItem(it.key, { name: e.currentTarget.value })}
                  />
                  <NumberInput
                    size="xs"
                    label="Nominal"
                    placeholder="0"
                    value={it.amount}
                    onChange={(v) => updateItem(it.key, { amount: Number(v) || 0 })}
                    allowNegative
                    prefix="Rp"
                    thousandSeparator="."
                    decimalSeparator=","
                  />
                  <Select
                    size="xs"
                    label="Budget"
                    placeholder="Budget"
                    data={budgetOptions}
                    value={it.budget}
                    onChange={(v) => updateItem(it.key, { budget: v, category: null })}
                    searchable
                    maxDropdownHeight={220}
                    comboboxProps={{ withinPortal: false }}
                  />
                  <Select
                    size="xs"
                    label="Category"
                    placeholder={it.budget ? 'Uncategorized' : 'Pilih budget dulu'}
                    data={categoryOptionsOf(it.budget)}
                    value={it.category}
                    onChange={(v) => updateItem(it.key, { category: v })}
                    searchable
                    clearable
                    disabled={!it.budget || categoryOptionsOf(it.budget).length === 0}
                    maxDropdownHeight={220}
                    comboboxProps={{ withinPortal: false }}
                  />
                </Stack>
              </Paper>
            ) : (
              <Group
                key={it.key}
                ref={(el) => {
                  itemRefs.current[it.key] = el
                }}
                wrap="nowrap"
                align="flex-end"
                gap="xs"
                style={
                  flashKey === it.key
                    ? { borderColor: 'var(--mantine-color-yellow-6)', background: 'rgba(255, 193, 7, 0.12)' }
                    : undefined
                }
              >
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
                  onChange={(v) => updateItem(it.key, { budget: v, category: null })}
                  searchable
                  style={{ flex: 1.2 }}
                />
                <Select
                  size="xs"
                  placeholder="Category"
                  data={categoryOptionsOf(it.budget)}
                  value={it.category}
                  onChange={(v) => updateItem(it.key, { category: v })}
                  searchable
                  clearable
                  disabled={!it.budget || categoryOptionsOf(it.budget).length === 0}
                  style={{ flex: 1.2 }}
                  comboboxProps={{ withinPortal: false }}
                />
                <ActionIcon color="red" variant="subtle" onClick={() => removeItem(it.key)} aria-label="Hapus item">
                  ✕
                </ActionIcon>
              </Group>
            ),
          )}
          <Button variant="light" size="xs" onClick={addItem} disabled={batch.isPending}>
            + Tambah item
          </Button>

          {problems.length > 0 && (
            <Stack gap={2}>
              {problems.map((problem) => (
                <Text key={problem} size="xs" c="orange">
                  ⚠ {problem}
                </Text>
              ))}
            </Stack>
          )}

          <Button
            fullWidth
            size="md"
            onClick={handleSubmit}
            loading={batch.isPending}
            disabled={invalid}
          >
            Buat {groups.length} Pengeluaran
          </Button>
        </Stack>
      )}
    </Modal>
  )
}

export default ReviewModal
