import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ActionIcon,
  Alert,
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
import { IconArrowUp, IconMicrophone, IconPlus, IconX } from '@tabler/icons-react'
import { DateTimePicker } from '@mantine/dates'
import dayjs from 'dayjs'
import { useVoiceNoteDetail, useRetryVoiceNote } from '../hooks/useVoiceNotes'
import { useCreateExpenseBatch } from '../hooks/useScan'
import { useOptions } from '../hooks/useOptions'
import { getVoiceNoteAudioUrl } from '../services/voiceNotes'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'
import type { BudgetOption, VoiceAnalysis } from '../types/expense'
import { useToast } from './Toast'

interface EditItem {
  key: string
  name: string
  amount: number
  budget: string | null
  category: string | null
}

const MAX_DESC = 1000

const groupKey = (budget: string, category: string | null) => `${budget}|||${category ?? ''}`

function toEditItems(analysis: VoiceAnalysis | undefined, budgets: BudgetOption[]): EditItem[] {
  const rawItems = analysis?.items?.length
    ? analysis.items
    : analysis?.name && Number(analysis.amount) > 0
      ? [{
          name: analysis.name,
          amount: Number(analysis.amount),
          suggestedBudget: analysis.budget,
          suggestedCategory: analysis.category,
        }]
      : []

  return rawItems.map((item, index) => {
    const budget = budgets.find((b) => b.name === item.suggestedBudget)
    const category =
      budget && item.suggestedCategory && budget.categories.some((c) => c.name === item.suggestedCategory)
        ? item.suggestedCategory
        : null
    return {
      key: `${Date.now()}-${index}`,
      name: item.name ?? '',
      amount: Number(item.amount) || 0,
      budget: budget ? budget.name : null,
      category,
    }
  })
}

function VoiceReviewModal({
  voiceNoteId,
  onClose,
  onSubmitted,
}: {
  voiceNoteId: string
  onClose: () => void
  onSubmitted: () => void
}) {
  const { data, isPending: detailPending } = useVoiceNoteDetail(voiceNoteId)
  const { data: options } = useOptions()
  const retry = useRetryVoiceNote()
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
    items.forEach((item, index) => map.set(item.key, index))
    return map
  }, [items])

  const budgets = useMemo(() => options?.budgets ?? [], [options])
  const budgetNames = useMemo(() => budgets.map((budget) => budget.name), [budgets])
  const budgetOptions = useMemo(() => budgetNames.map((name) => ({ value: name, label: name })), [budgetNames])

  const categoryOptionsOf = (budget: string | null) =>
    (budgets.find((item) => item.name === budget)?.categories ?? []).map((category) => ({
      value: category.name,
      label: category.name,
    }))

  const analysis = data?.status === 'TO_REVIEW' ? data.analysis : undefined
  const storeName = analysis?.storeName?.trim() || analysis?.name?.trim() || 'Belanja'

  // Munculkan tombol "kembali ke ringkasan" saat section ringkasan tergulung ke atas.
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

  const initialDateTime = useMemo(() => {
    const now = dayjs()
    const ai = analysis?.dateTime?.trim() || ''
    if (ai) {
      const full = dayjs(ai, 'YYYY-MM-DD HH:mm:ss', true)
      if (full.isValid()) return full.format('YYYY-MM-DD HH:mm')
      const minute = dayjs(ai, 'YYYY-MM-DD HH:mm', true)
      if (minute.isValid()) return minute.format('YYYY-MM-DD HH:mm')
      const dateOnly = dayjs(ai, 'YYYY-MM-DD', true)
      if (dateOnly.isValid()) return `${dateOnly.format('YYYY-MM-DD')} ${now.format('HH:mm')}`
    }
    return now.format('YYYY-MM-DD HH:mm')
  }, [analysis])

  const [dateTime, setDateTime] = useState(initialDateTime)

  // Inisialisasi item sekali per voice note. Refetch background (mis. options)
  // tidak boleh menimpa editan user.
  const initializedRef = useRef<string | null>(null)
  useEffect(() => {
    if (!analysis || !options) return
    if (initializedRef.current === voiceNoteId) return
    initializedRef.current = voiceNoteId
    setItems(toEditItems(analysis, budgets))
    setGroupNames({})
    setDateTime(initialDateTime)
  }, [analysis, budgets, initialDateTime, options, voiceNoteId])

  const updateItem = (key: string, patch: Partial<EditItem>) => {
    setItems((prev) => prev.map((item) => (item.key === key ? { ...item, ...patch } : item)))
  }

  const addItem = () => {
    setItems((prev) => [
      ...prev,
      { key: `${Date.now()}-${prev.length}`, name: '', amount: 0, budget: null, category: null },
    ])
  }

  const removeItem = (key: string) => setItems((prev) => prev.filter((item) => item.key !== key))

  const jumpToItem = (key: string) => {
    itemRefs.current[key]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setFlashKey(key)
    window.setTimeout(() => setFlashKey((current) => (current === key ? null : current)), 1500)
  }

  const scrollToSummary = () => {
    summaryRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    setFlashSummary(true)
    window.setTimeout(() => setFlashSummary(false), 1500)
  }

  const groups = useMemo(() => {
    const map = new Map<string, { budget: string; category: string | null; items: EditItem[] }>()
    for (const item of items) {
      if (!item.budget) continue
      const key = groupKey(item.budget, item.category)
      const existing = map.get(key)
      if (existing) {
        existing.items.push(item)
      } else {
        map.set(key, { budget: item.budget, category: item.category, items: [item] })
      }
    }
    return Array.from(map.entries())
  }, [items])

  const itemsSum = items.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)

  const problems = useMemo(() => {
    const list: string[] = []
    items.forEach((item, index) => {
      const name = item.name.trim()
      const label = name ? `Item ${index + 1} (${name})` : `Item ${index + 1}`
      if (!name) list.push(`${label}: nama belum diisi`)
      if (!item.budget) list.push(`${label}: budget belum dipilih`)
      if (Number(item.amount) === 0) list.push(`${label}: nominal masih 0`)
    })
    for (const [, group] of groups) {
      const sum = group.items.reduce((total, item) => total + Number(item.amount), 0)
      if (sum <= 0) {
        const label = group.category ? `${group.budget} / ${group.category}` : group.budget
        list.push(`Budget ${label}: totalnya harus lebih dari 0`)
      }
    }
    return list
  }, [items, groups])
  const invalid = problems.length > 0

  const groupName = (key: string) => groupNames[key] ?? storeName

  const buildDescription = (groupItems: EditItem[]): string => {
    const full = groupItems
      .map((item) => `${item.name.trim()} ${formatCurrency(Number(item.amount))}`)
      .filter(Boolean)
      .join(', ')
    return full.length <= MAX_DESC ? full : `${full.slice(0, MAX_DESC).replace(/,\s*$/, '')}…`
  }

  const handleSubmit = () => {
    if (batch.isPending) return
    if (invalid) {
      toast.error('Periksa item yang ditandai di bawah', { title: 'Belum lengkap' })
      return
    }
    const groupsPayload = groups.map(([key, group]) => {
      const amount = group.items.reduce((sum, item) => sum + Number(item.amount), 0)
      return {
        name: groupName(key).trim() || storeName,
        budget: group.budget,
        category: group.category ?? undefined,
        amount,
        description: buildDescription(group.items),
      }
    })
    batch.mutate(
      { dateTime, voiceNoteId, groups: groupsPayload },
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
            <Text fw={600}>Tinjau Hasil Analisis</Text>
            <Button
              size="compact-xs"
              variant="light"
              leftSection={<IconArrowUp size={14} />}
              onClick={scrollToSummary}
              style={{
                visibility: showBack ? 'visible' : 'hidden',
                opacity: showBack ? 1 : 0,
                pointerEvents: showBack ? 'auto' : 'none',
              }}
            >
              Pengeluaran per budget
            </Button>
          </Stack>
        ) : (
          'Tinjau Hasil Analisis'
        )
      }
      centered
      fullScreen={isMobile}
      size="md"
      styles={{ body: { maxHeight: isMobile ? undefined : 'calc(100dvh - 140px)', overflowY: 'auto' } }}
    >
      {detailPending || data?.status === 'ANALYZING' ? (
        <Stack align="center" py="xl" gap="sm">
          <Loader />
          <Text size="sm" c="dimmed">
            AI sedang mendengarkan rekaman…
          </Text>
        </Stack>
      ) : data?.status === 'NOT_EXPENSE' ? (
        <Stack gap="sm">
          <Alert color="orange" variant="light">
            {data.errorMessage || 'Rekaman ini bukan catatan pengeluaran.'}
          </Alert>
          <Button variant="default" onClick={onClose}>
            Tutup
          </Button>
        </Stack>
      ) : data?.status === 'ERROR' ? (
        <Stack gap="sm">
          <Alert color="red" variant="light">
            {data.errorMessage || 'Analisis rekaman gagal.'}
          </Alert>
          <Button
            variant="light"
            loading={retry.isPending}
            onClick={() =>
              retry.mutate(voiceNoteId, {
                onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
              })
            }
          >
            Coba lagi
          </Button>
        </Stack>
      ) : !analysis ? (
        <Text size="sm" c="dimmed">
          Hasil analisis belum tersedia.
        </Text>
      ) : (
        <Stack>
          <Paper withBorder p="sm" radius="md">
            <Stack gap={6}>
              <Group gap="xs" wrap="nowrap">
                <IconMicrophone size={18} aria-hidden />
                <Text size="sm" fw={600}>
                  {storeName}
                </Text>
              </Group>
              <audio controls preload="metadata" src={getVoiceNoteAudioUrl(voiceNoteId)} style={{ width: '100%' }} />
              {analysis.transcript ? (
                <Text size="xs" c="dimmed">
                  “{analysis.transcript}”
                </Text>
              ) : null}
              <Group justify="space-between" align="center" wrap="nowrap" gap="xs">
                <Text size="sm" c="dimmed">
                  Tanggal pengeluaran
                </Text>
                <DateTimePicker
                  value={dateTime}
                  onChange={(value) =>
                    setDateTime(value ? dayjs(value).format('YYYY-MM-DD HH:mm') : dayjs().format('YYYY-MM-DD HH:mm'))
                  }
                  valueFormat="DD MMM YYYY HH:mm"
                  dropdownType="modal"
                  size="xs"
                  style={{ width: isMobile ? 150 : 200 }}
                />
              </Group>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Jumlah item
                </Text>
                <Text size="sm" fw={600}>
                  {items.length}
                </Text>
              </Group>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Total item
                </Text>
                <Text size="sm" fw={600}>
                  {formatCurrency(itemsSum)}
                </Text>
              </Group>
            </Stack>
          </Paper>

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
            const amount = group.items.reduce((sum, item) => sum + Number(item.amount), 0)
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
                  onChange={(event) =>
                    setGroupNames((prev) => ({ ...prev, [key]: event.currentTarget.value }))
                  }
                />
                <Group gap={6} mt={6}>
                  {group.items.map((item) => (
                    <Text
                      key={item.key}
                      size="xs"
                      c="blue"
                      style={{ cursor: 'pointer', textDecoration: 'underline' }}
                      onClick={() => jumpToItem(item.key)}
                    >
                      {item.name.trim() || `Item ${(indexById.get(item.key) ?? 0) + 1}`}
                    </Text>
                  ))}
                </Group>
              </Paper>
            )
          })}

          <Text size="sm" fw={600}>
            Item
          </Text>
          {items.map((item, index) =>
            isMobile ? (
              <Paper
                key={item.key}
                ref={(element) => {
                  itemRefs.current[item.key] = element
                }}
                withBorder
                p="sm"
                radius="md"
                style={
                  flashKey === item.key
                    ? { borderColor: 'var(--mantine-color-yellow-6)', background: 'rgba(255, 193, 7, 0.12)' }
                    : undefined
                }
              >
                <Group justify="space-between" align="center" mb="xs">
                  <Text size="sm" fw={600}>
                    Item {index + 1}
                  </Text>
                  <ActionIcon color="red" variant="subtle" onClick={() => removeItem(item.key)} aria-label="Hapus item">
                    <IconX size={16} />
                  </ActionIcon>
                </Group>
                <Stack gap="xs">
                  <TextInput
                    size="xs"
                    label="Nama"
                    placeholder="Nama"
                    value={item.name}
                    onChange={(event) => updateItem(item.key, { name: event.currentTarget.value })}
                  />
                  <NumberInput
                    size="xs"
                    label="Nominal"
                    placeholder="0"
                    value={item.amount}
                    onChange={(value) => updateItem(item.key, { amount: Number(value) || 0 })}
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
                    value={item.budget}
                    onChange={(value) => updateItem(item.key, { budget: value, category: null })}
                    searchable
                    maxDropdownHeight={220}
                    comboboxProps={{ withinPortal: false }}
                  />
                  <Select
                    size="xs"
                    label="Category"
                    placeholder={item.budget ? 'Uncategorized' : 'Pilih budget dulu'}
                    data={categoryOptionsOf(item.budget)}
                    value={item.category}
                    onChange={(value) => updateItem(item.key, { category: value })}
                    searchable
                    clearable
                    disabled={!item.budget || categoryOptionsOf(item.budget).length === 0}
                    maxDropdownHeight={220}
                    comboboxProps={{ withinPortal: false }}
                  />
                </Stack>
              </Paper>
            ) : (
              <Group
                key={item.key}
                ref={(element) => {
                  itemRefs.current[item.key] = element
                }}
                wrap="nowrap"
                align="flex-end"
                gap="xs"
                style={
                  flashKey === item.key
                    ? { borderColor: 'var(--mantine-color-yellow-6)', background: 'rgba(255, 193, 7, 0.12)' }
                    : undefined
                }
              >
                <TextInput
                  size="xs"
                  placeholder="Nama"
                  value={item.name}
                  onChange={(event) => updateItem(item.key, { name: event.currentTarget.value })}
                  style={{ flex: 1.4 }}
                />
                <NumberInput
                  size="xs"
                  placeholder="0"
                  value={item.amount}
                  onChange={(value) => updateItem(item.key, { amount: Number(value) || 0 })}
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
                  value={item.budget}
                  onChange={(value) => updateItem(item.key, { budget: value, category: null })}
                  searchable
                  style={{ flex: 1.2 }}
                />
                <Select
                  size="xs"
                  placeholder="Category"
                  data={categoryOptionsOf(item.budget)}
                  value={item.category}
                  onChange={(value) => updateItem(item.key, { category: value })}
                  searchable
                  clearable
                  disabled={!item.budget || categoryOptionsOf(item.budget).length === 0}
                  style={{ flex: 1.2 }}
                  comboboxProps={{ withinPortal: false }}
                />
                <ActionIcon color="red" variant="subtle" onClick={() => removeItem(item.key)} aria-label="Hapus item">
                  <IconX size={16} />
                </ActionIcon>
              </Group>
            ),
          )}
          <Button variant="light" size="xs" leftSection={<IconPlus size={14} />} onClick={addItem} disabled={batch.isPending}>
            Tambah item
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

          <Button fullWidth size="md" onClick={handleSubmit} loading={batch.isPending}>
            Buat {groups.length} Pengeluaran
          </Button>
        </Stack>
      )}
    </Modal>
  )
}

export default VoiceReviewModal
