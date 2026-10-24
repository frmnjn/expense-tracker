import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ActionIcon,
  Button,
  Chip,
  Container,
  Divider,
  Group,
  Image,
  LoadingOverlay,
  Modal,
  NumberInput,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import {
  IconArrowLeft,
  IconFileTypePdf,
  IconPhoto,
  IconMicrophone,
  IconPencil,
  IconSearch,
  IconSearchOff,
  IconTrash,
  IconX,
} from '@tabler/icons-react'
import dayjs from 'dayjs'
import { PageHeader } from '../components/PageHeader'
import { useDeleteExpense, useExpenses, usePeriods, useUpdateExpense } from '../hooks/useExpenses'
import { useDeletePhoto, useUploadPhoto } from '../hooks/useCreateExpense'
import { useOptions } from '../hooks/useOptions'
import PhotoInput, { type PhotoSelection } from '../components/PhotoInput'
import { TransactionCard } from '../components/TransactionCard'
import { AppPagination } from '../components/AppPagination'
import { ErrorState } from '../components/ErrorState'
import { usePdfViewer } from '../components/PdfViewerContext'
import { useToast } from '../components/Toast'
import { getPhotoUrl } from '../services/expense'
import { getVoiceNoteAudioUrl } from '../services/voiceNotes'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'
import type { Expense } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'
const DISPLAY_DATE_TIME_FORMAT = 'DD MMM YYYY HH:mm'

const UNCATEGORIZED = '__uncategorized__'

const SORT_LABELS: Record<string, string> = {
  'waktu-desc': 'Waktu terbaru',
  'waktu-asc': 'Waktu terlama',
  'nominal-desc': 'Nominal terbesar',
  'nominal-asc': 'Nominal terkecil',
}

const SORT_OPTIONS = Object.entries(SORT_LABELS).map(([value, label]) => ({ value, label }))

const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

function HistoryPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options } = useOptions()
  const [periodSelection, setPeriodSelection] = useState<string | null>(null)
  const isMobile = useMediaQuery('(max-width: 48em)')

  const period = periodSelection ?? periodsData?.periods[0] ?? null
  const setPeriod = setPeriodSelection
  const { data: expensesData, isPending: expensesLoading, isError: expensesError, refetch: refetchExpenses } = useExpenses(period)

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])
  const expenses = expensesData?.expenses ?? []
  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((b) => ({ value: b.name, label: b.name })),
    [options],
  )

  const [search, setSearch] = useState('')
  const [budgetFilters, setBudgetFilters] = useState<string[]>([])
  const [categoryFilters, setCategoryFilters] = useState<string[]>([])
  const [sortBy, setSortBy] = useState<string>('waktu-desc')
  const [viewingPhoto, setViewingPhoto] = useState<Expense | null>(null)
  const { openPdf } = usePdfViewer()

  const openExpensePhoto = (expense: Expense) => {
    if (expense.photoType === 'pdf') {
      openPdf({
        url: getPhotoUrl(expense.id),
        title: expense.photoName || expense.name,
        fileName: expense.photoName || undefined,
      })
      return
    }
    setViewingPhoto(expense)
  }
  const [viewingVoice, setViewingVoice] = useState<Expense | null>(null)

  const categoryFilterOptions = useMemo(() => {
    const names = new Set<string>()
    for (const budget of options?.budgets ?? []) {
      if (!budgetFilters.includes(budget.name)) {
        continue
      }
      for (const category of budget.categories ?? []) {
        names.add(category.name)
      }
    }
    return [
      { value: UNCATEGORIZED, label: 'Tanpa Kategori' },
      ...[...names].map((name) => ({ value: name, label: name })),
    ]
  }, [options, budgetFilters])

  const hasActiveFilters =
    search.trim() !== '' ||
    budgetFilters.length > 0 ||
    categoryFilters.length > 0 ||
    sortBy !== 'waktu-desc'

  const handleBudgetFiltersChange = (next: string[]) => {
    setBudgetFilters(next)
    if (next.length === 0) {
      setCategoryFilters([])
      return
    }
    const available = new Set<string>()
    for (const budget of options?.budgets ?? []) {
      if (!next.includes(budget.name)) {
        continue
      }
      for (const category of budget.categories ?? []) {
        available.add(category.name)
      }
    }
    setCategoryFilters((current) =>
      current.filter((value) => value === UNCATEGORIZED || available.has(value)),
    )
  }

  const toggleBudgetFilter = (name: string) => {
    const next = budgetFilters.includes(name)
      ? budgetFilters.filter((value) => value !== name)
      : [...budgetFilters, name]
    handleBudgetFiltersChange(next)
  }

  const toggleCategoryFilter = (name: string) => {
    setCategoryFilters((current) =>
      current.includes(name) ? current.filter((value) => value !== name) : [...current, name],
    )
  }

  const resetFilters = () => {
    setSearch('')
    setBudgetFilters([])
    setCategoryFilters([])
    setSortBy('waktu-desc')
  }

  const visibleExpenses = useMemo(() => {
    const q = search.trim().toLowerCase()
    let list = expensesData?.expenses ?? []
    if (q) {
      list = list.filter((e) => e.name.toLowerCase().includes(q))
    }
    if (budgetFilters.length > 0) {
      const selectedBudgets = new Set(budgetFilters)
      list = list.filter((e) => selectedBudgets.has(e.budget))
    }
    if (categoryFilters.length > 0) {
      const selectedCategories = new Set(categoryFilters.filter((value) => value !== UNCATEGORIZED))
      const includeUncategorized = categoryFilters.includes(UNCATEGORIZED)
      list = list.filter((e) =>
        e.category ? selectedCategories.has(e.category) : includeUncategorized,
      )
    }
    const sorted = [...list]
    switch (sortBy) {
      case 'waktu-asc':
        sorted.sort((a, b) => a.dateTime.localeCompare(b.dateTime))
        break
      case 'nominal-desc':
        sorted.sort((a, b) => b.amount - a.amount)
        break
      case 'nominal-asc':
        sorted.sort((a, b) => a.amount - b.amount)
        break
      default:
        sorted.sort((a, b) => b.dateTime.localeCompare(a.dateTime))
    }
    return sorted
  }, [expensesData, search, budgetFilters, categoryFilters, sortBy])

  const [editing, setEditing] = useState<Expense | null>(null)
  const [deleting, setDeleting] = useState<Expense | null>(null)

  const deleteExpense = useDeleteExpense()
  const toast = useToast()

  const handleDelete = () => {
    if (!deleting) return
    deleteExpense.mutate(deleting.id, {
      onSuccess: () => {
        toast.success('Pengeluaran dihapus', { title: 'Berhasil' })
        setDeleting(null)
      },
      onError: (error) => {
        toast.error(getErrorMessage(error), { title: 'Gagal' })
      },
    })
  }

  const balanceOf = (name: string): number | undefined => options?.budgets.find((b) => b.name === name)?.balance
  const deletingBalance = deleting ? balanceOf(deleting.budget) : undefined
  const projectedDeleteBalance =
    deletingBalance !== undefined && deleting ? deletingBalance + deleting.amount : undefined

  return (
    <Container
      size="md"
      py="lg"
      pb={{ base: 'calc(96px + env(safe-area-inset-bottom, 0px))', sm: 'lg' }}
    >
      <Stack gap="lg">
        <PageHeader
          eyebrow="Transaksi"
          title="Riwayat Pengeluaran"
          subtitle="Lihat dan kelola semua pengeluaran kamu."
          right={
            <Select
              w={{ base: '100%', sm: 190 }}
              placeholder={periodsLoading ? 'Memuat...' : 'Pilih periode'}
              data={periods}
              value={period}
              onChange={setPeriod}
              searchable
              size="sm"
              aria-label="Periode"
            />
          }
        />

        {period && !(isMobile && editing) && (
          <Stack gap="xs">
            <Group wrap="nowrap" align="center">
              <TextInput
                placeholder="Cari nama pengeluaran"
                value={search}
                onChange={(e) => setSearch(e.currentTarget.value)}
                leftSection={<IconSearch size={16} />}
                rightSection={
                  search ? (
                    <ActionIcon variant="subtle" size="sm" onClick={() => setSearch('')} aria-label="Bersihkan pencarian">
                      <IconX size={14} />
                    </ActionIcon>
                  ) : null
                }
                size="md"
                style={{ flex: 1, minWidth: 0 }}
              />
              <Select
                data={SORT_OPTIONS}
                value={sortBy}
                onChange={(value) => setSortBy(value ?? 'waktu-desc')}
                size="md"
                w={{ base: 160, sm: 180 }}
                aria-label="Urutkan"
                allowDeselect={false}
                comboboxProps={{ withinPortal: false }}
                style={{ flexShrink: 0 }}
              />
            </Group>

            <Stack gap={6}>
              <Text size="xs" fw={700} c="blue.7" tt="uppercase">
                Budget
              </Text>
              <Group gap={6} wrap="wrap" py={2}>
                <Chip
                  checked={budgetFilters.length === 0}
                  onChange={() => handleBudgetFiltersChange([])}
                  size="sm"
                  radius="xl"
                  variant="light"
                  color="blue"
                >
                  Semua Budget
                </Chip>
                {budgetOptions.map((budget) => (
                  <Chip
                    key={budget.value}
                    checked={budgetFilters.includes(budget.value)}
                    onChange={() => toggleBudgetFilter(budget.value)}
                    size="sm"
                    radius="xl"
                    variant="light"
                    color="blue"
                  >
                    {budget.label}
                  </Chip>
                ))}
              </Group>
            </Stack>

            <Stack gap={6}>
              <Text size="xs" fw={700} c="grape.7" tt="uppercase">
                Kategori
              </Text>
              {budgetFilters.length === 0 ? (
                <Text size="xs" c="dimmed">
                  Pilih budget dulu untuk melihat kategori.
                </Text>
              ) : (
                <Group gap={6} wrap="wrap" py={2}>
                  <Chip
                    checked={categoryFilters.length === 0}
                    onChange={() => setCategoryFilters([])}
                    size="sm"
                    radius="xl"
                    variant="light"
                    color="grape"
                  >
                    Semua Kategori
                  </Chip>
                  {categoryFilterOptions.map((category) => (
                    <Chip
                      key={category.value}
                      checked={categoryFilters.includes(category.value)}
                      onChange={() => toggleCategoryFilter(category.value)}
                      size="sm"
                      radius="xl"
                      variant="light"
                      color="grape"
                    >
                      {category.label}
                    </Chip>
                  ))}
                </Group>
              )}
            </Stack>

            {hasActiveFilters && (
              <Group justify="flex-end">
                <Button
                  size="compact-xs"
                  variant="subtle"
                  color="gray"
                  leftSection={<IconX size={12} />}
                  onClick={resetFilters}
                >
                  Reset filter
                </Button>
              </Group>
            )}
          </Stack>
        )}

        {isMobile && editing ? (
          <EditExpenseForm expense={editing} onClose={() => setEditing(null)} inline />
        ) : (
          <AppPagination
            data={visibleExpenses}
            key={`${search}|${budgetFilters.join(',')}|${categoryFilters.join(',')}|${sortBy}|${period}`}
          >
            {(pageExpenses) => (
              <Paper withBorder p={{ base: 'sm', sm: 'lg' }} radius="xl" pos="relative">
          <LoadingOverlay
            visible={expensesLoading && !!period}
            zIndex={1000}
            overlayProps={{ radius: 'lg', blur: 1 }}
          />

          {expensesError ? (
            <ErrorState message="Gagal memuat pengeluaran." onRetry={() => void refetchExpenses()} />
          ) : expenses.length === 0 ? (
            <Stack align="center" gap="sm" py="lg">
              <Text c="dimmed" ta="center">
                {period ? 'Belum ada pengeluaran pada periode ini.' : 'Pilih periode untuk melihat pengeluaran.'}
              </Text>
              {period && (
                <Button component={Link} to="/catat" size="compact-sm" variant="light">
                  Catat pengeluaran
                </Button>
              )}
            </Stack>
          ) : visibleExpenses.length === 0 ? (
            <Stack align="center" gap={4} py="xl">
              <IconSearchOff size={28} aria-hidden />
              <Text c="dimmed" ta="center">
                Tidak ada pengeluaran ditemukan.
              </Text>
              <Text size="sm" c="dimmed" ta="center">
                Ubah kata kunci atau filter untuk melihat hasil lain.
              </Text>
            </Stack>
          ) : isMobile ? (
            <Stack gap="sm">
              {pageExpenses.map((expense) => (
                <TransactionCard
                  key={expense.id}
                  expense={expense}
                  onViewPhoto={openExpensePhoto}
                  onViewVoice={setViewingVoice}
                  onEdit={setEditing}
                  onDelete={setDeleting}
                />
              ))}
            </Stack>
          ) : (
            <Table highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Waktu</Table.Th>
                  <Table.Th>Nama</Table.Th>
                  <Table.Th>Budget</Table.Th>
                  <Table.Th ta="right">Nominal</Table.Th>
                  <Table.Th ta="right">Aksi</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {pageExpenses.map((expense) => (
                  <Table.Tr key={expense.id}>
                    <Table.Td>{dayjs(expense.dateTime).format(DISPLAY_DATE_TIME_FORMAT)}</Table.Td>
                    <Table.Td>{expense.name}</Table.Td>
                    <Table.Td>
                      <Text size="sm">{expense.budget}</Text>
                      {expense.category && (
                        <Text size="xs" c="dimmed">
                          {expense.category}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td ta="right">{formatCurrency(expense.amount)}</Table.Td>
                    <Table.Td ta="right">
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                        {expense.hasPhoto && (
                          <ActionIcon variant="light" color="gray" size="md" onClick={() => openExpensePhoto(expense)} aria-label="Lihat foto">
                            <IconPhoto size={16} />
                          </ActionIcon>
                        )}
                        {expense.voiceNoteId && (
                          <ActionIcon variant="light" color="grape" size="md" onClick={() => setViewingVoice(expense)} aria-label="Putar rekaman suara">
                            <IconMicrophone size={16} />
                          </ActionIcon>
                        )}
                        <ActionIcon variant="light" color="blue" size="md" disabled={!expense.id} onClick={() => setEditing(expense)} aria-label="Edit">
                          <IconPencil size={16} />
                        </ActionIcon>
                        <ActionIcon variant="light" color="red" size="md" disabled={!expense.id} onClick={() => setDeleting(expense)} aria-label="Hapus">
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Paper>
            )}
          </AppPagination>
        )}

        {editing && !isMobile && (
          <Modal
            opened
            onClose={() => setEditing(null)}
            title="Ubah pengeluaran"
            centered
            size="md"
            zIndex={1200}
          >
            <EditExpenseForm expense={editing} onClose={() => setEditing(null)} />
          </Modal>
        )}

        <Modal
          opened={!!viewingPhoto}
          onClose={() => setViewingPhoto(null)}
          title={viewingPhoto?.name ?? viewingPhoto?.photoName ?? 'Invoice'}
          centered={!isMobile}
          fullScreen={isMobile}
          size="md"
          zIndex={1200}
        >
          {viewingPhoto &&
            (viewingPhoto.photoType === 'pdf' ? (
              <Stack align="center" gap="sm">
                <IconFileTypePdf size={48} aria-hidden />
                <Button
                  onClick={() => {
                    const expense = viewingPhoto
                    setViewingPhoto(null)
                    openPdf({
                      url: getPhotoUrl(expense.id),
                      title: expense.photoName || expense.name,
                      fileName: expense.photoName || undefined,
                    })
                  }}
                  variant="light"
                >
                  Buka PDF
                </Button>
              </Stack>
            ) : (
              <Stack align="center" gap="sm">
                <Image
                  src={getPhotoUrl(viewingPhoto.id)}
                  alt={viewingPhoto.name}
                  fit="contain"
                  mah="60vh"
                  style={{ maxWidth: '100%' }}
                />
                {viewingPhoto.photoName && (
                  <Text size="xs" c="dimmed" ta="center" truncate style={{ maxWidth: '100%' }}>
                    {viewingPhoto.photoName}
                  </Text>
                )}
              </Stack>
            ))}
          {viewingPhoto?.photoType === 'pdf' && viewingPhoto?.photoName && (
            <Text size="xs" c="dimmed" ta="center" mt="sm" truncate>
              {viewingPhoto.photoName}
            </Text>
          )}
        </Modal>

        <Modal
          opened={!!viewingVoice}
          onClose={() => setViewingVoice(null)}
          title={viewingVoice?.name ?? 'Rekaman Suara'}
          centered
          size="md"
        >
          {viewingVoice?.voiceNoteId && (
            <Stack gap="sm">
              <Text size="sm" c="dimmed">
                Rekaman asli untuk pengeluaran ini.
              </Text>
              <audio
                controls
                autoPlay
                preload="metadata"
                src={getVoiceNoteAudioUrl(viewingVoice.voiceNoteId)}
                style={{ width: '100%' }}
              />
            </Stack>
          )}
        </Modal>

        <Modal
          opened={!!deleting}
          onClose={() => setDeleting(null)}
          title="Hapus pengeluaran"
          centered
          size="sm"
        >
          <Text>
            Yakin ingin menghapus <Text span fw={700}>"{deleting?.name}"</Text> sebesar{' '}
            {deleting ? formatCurrency(deleting.amount) : ''}?
          </Text>
          {deleting && projectedDeleteBalance !== undefined && (
            <Paper withBorder p="sm" radius="md" mt="md" bg="var(--mantine-color-body)">
              <Stack gap="4">
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">
                    Saldo {deleting.budget} saat ini
                  </Text>
                  <Text size="sm" ff="monospace">
                    {formatCurrency(deletingBalance ?? 0)}
                  </Text>
                </Group>
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">
                    Dikembalikan
                  </Text>
                  <Text size="sm" ff="monospace">
                    +{formatCurrency(deleting.amount)}
                  </Text>
                </Group>
                <Divider my={2} />
                <Group justify="space-between">
                  <Text size="sm" fw={500}>
                    Saldo nanti
                  </Text>
                  <Text size="sm" fw={700} c={balanceColor(projectedDeleteBalance)} ff="monospace">
                    {formatCurrency(projectedDeleteBalance)}
                  </Text>
                </Group>
              </Stack>
            </Paper>
          )}
          <Group justify="flex-end" mt="lg">
            <Button color="red" loading={deleteExpense.isPending} onClick={handleDelete} fullWidth={isMobile}>
              Hapus
            </Button>
          </Group>
        </Modal>
      </Stack>
    </Container>
  )
}

function EditExpenseForm({
  expense,
  onClose,
  inline = false,
}: {
  expense: Expense
  onClose: () => void
  inline?: boolean
}) {
  const { data: options } = useOptions()
  const updateExpense = useUpdateExpense()
  const uploadPhoto = useUploadPhoto()
  const deletePhoto = useDeletePhoto()
  const toast = useToast()

  const [dateTime, setDateTime] = useState(expense.dateTime)
  const [name, setName] = useState(expense.name)
  const [budget, setBudget] = useState<string | null>(expense.budget)
  const [category, setCategory] = useState<string | null>(expense.category ?? null)
  const [amount, setAmount] = useState<string | number>(expense.amount)
  const [description, setDescription] = useState(expense.description ?? '')
  const [photo, setPhoto] = useState<PhotoSelection | null>(null)
  const [removePhoto, setRemovePhoto] = useState(false)
  const { openPdf } = usePdfViewer()

  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((b) => ({ value: b.name, label: b.name })),
    [options],
  )

  const categoryOptions = useMemo(
    () =>
      (options?.budgets.find((b) => b.name === budget)?.categories ?? []).map((c) => ({
        value: c.name,
        label: c.name,
      })),
    [options, budget],
  )

  const submitDisabled = name.trim() === '' || !budget || Number(amount) <= 0 || updateExpense.isPending

  const balanceOf = (n: string): number | undefined => options?.budgets.find((b) => b.name === n)?.balance
  const oldAmount = expense.amount
  const newAmount = Number(amount)
  const sameBudget = budget === expense.budget
  const editPreview = budget && newAmount > 0
  const oldBalance = balanceOf(expense.budget)
  const newBudgetBalance = budget ? balanceOf(budget) : undefined
  const projectedSameBudget = oldBalance !== undefined && sameBudget ? oldBalance + oldAmount - newAmount : undefined
  const projectedOldBudget = oldBalance !== undefined && !sameBudget ? oldBalance + oldAmount : undefined
  const projectedNewBudget = newBudgetBalance !== undefined && !sameBudget ? newBudgetBalance - newAmount : undefined

  const handleSubmit = () => {
    updateExpense.mutate(
      {
        id: expense.id,
        request: {
          dateTime,
          name: name.trim(),
          budget: budget ?? '',
          category: category ?? undefined,
          amount: Number(amount),
          description: description.trim() === '' ? undefined : description.trim(),
          invoiceId: photo?.kind === 'existing' ? photo.invoiceId : undefined,
        },
      },
      {
        onSuccess: () => {
          const success = () => {
            toast.success('Pengeluaran diperbarui', { title: 'Berhasil' })
            onClose()
          }
          const photoError = (error: unknown) => {
            toast.warning(getErrorMessage(error), { title: 'Tersimpan, tapi foto gagal diupload' })
            onClose()
          }
          if (removePhoto) {
            deletePhoto.mutate(expense.id, { onSuccess: success, onError: photoError })
          } else if (photo?.kind === 'new') {
            uploadPhoto.mutate({ id: expense.id, file: photo.file }, { onSuccess: success, onError: photoError })
          } else {
            success()
          }
        },
        onError: (error) => {
          toast.error(getErrorMessage(error), { title: 'Gagal' })
        },
      },
    )
  }

  return (
    <Stack gap="md">
      {inline && (
        <Group justify="space-between" align="center">
          <Button variant="subtle" leftSection={<IconArrowLeft size={16} />} onClick={onClose}>
            Kembali
          </Button>
          <Title order={3}>Ubah pengeluaran</Title>
        </Group>
      )}

      <TextInput label="Waktu" value={dateTime} onChange={(e) => setDateTime(e.currentTarget.value)} required size="md" />
        <TextInput label="Nama" value={name} onChange={(e) => setName(e.currentTarget.value)} maxLength={255} required size="md" />
        <Select label="Budget" data={budgetOptions} value={budget} onChange={(v) => { setBudget(v); setCategory(null) }} searchable required size="md" comboboxProps={{ withinPortal: false }} />
        <Select
          label="Kategori"
          placeholder={budget ? 'Tanpa Kategori' : 'Pilih budget dulu'}
          data={categoryOptions}
          value={category}
          onChange={setCategory}
          searchable
          clearable
          disabled={!budget || categoryOptions.length === 0}
          size="md"
          comboboxProps={{ withinPortal: false }}
        />
        <NumberInput
          label="Nominal"
          value={amount}
          onChange={setAmount}
          min={0}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          required
          size="md"
        />

        {editPreview && (
          <Paper withBorder p="sm" radius="md" bg="var(--mantine-color-body)">
            <Stack gap="4">
              {sameBudget && projectedSameBudget !== undefined ? (
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">
                    Saldo {budget}
                  </Text>
                  <Text size="sm" ff="monospace">
                    {formatCurrency(oldBalance ?? 0)} →{' '}
                    <Text span c={balanceColor(projectedSameBudget)} fw={700}>
                      {formatCurrency(projectedSameBudget)}
                    </Text>
                  </Text>
                </Group>
              ) : (
                <>
                  <Group justify="space-between">
                    <Text size="sm" c="dimmed">
                      Saldo {expense.budget} (dikembalikan)
                    </Text>
                    <Text size="sm" ff="monospace">
                      {formatCurrency(oldBalance ?? 0)} →{' '}
                      <Text span c={balanceColor(projectedOldBudget ?? 0)} fw={700}>
                        {formatCurrency(projectedOldBudget ?? 0)}
                      </Text>
                    </Text>
                  </Group>
                  <Group justify="space-between">
                    <Text size="sm" c="dimmed">
                      Saldo {budget}
                    </Text>
                    <Text size="sm" ff="monospace">
                      {formatCurrency(newBudgetBalance ?? 0)} →{' '}
                      <Text span c={balanceColor(projectedNewBudget ?? 0)} fw={700}>
                        {formatCurrency(projectedNewBudget ?? 0)}
                      </Text>
                    </Text>
                  </Group>
                </>
              )}
            </Stack>
          </Paper>
        )}

        <Textarea
          label="Deskripsi (opsional)"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          maxLength={10000}
          size="md"
          autosize
          minRows={2}
          maxRows={6}
        />

        {expense.hasPhoto && !removePhoto && !photo && (
          <Stack gap={4}>
            <Group align="flex-start">
              {expense.photoType === 'pdf' ? (
                <Button
                  onClick={() => openPdf({
                    url: getPhotoUrl(expense.id),
                    title: expense.photoName || expense.name,
                    fileName: expense.photoName || undefined,
                  })}
                  variant="light"
                  size="xs"
                  leftSection={<IconFileTypePdf size={14} />}
                >
                  Lihat PDF
                </Button>
              ) : (
                <Image
                  src={getPhotoUrl(expense.id)}
                  alt="Invoice saat ini"
                  mah={150}
                  fit="contain"
                  radius="md"
                  style={{ flex: 1 }}
                />
              )}
              <Button variant="subtle" color="red" size="sm" onClick={() => setRemovePhoto(true)}>
                Hapus Foto
              </Button>
            </Group>
            {expense.photoName && (
              <Text size="xs" c="dimmed" truncate>
                {expense.photoName}
              </Text>
            )}
          </Stack>
        )}

        {expense.hasPhoto && removePhoto ? <Text size="sm" c="red">Foto akan dihapus setelah disimpan.</Text> : null}

        <PhotoInput
          value={photo}
          onChange={(v) => {
            setPhoto(v)
            if (v) setRemovePhoto(false)
          }}
          dateTime={dayjs(dateTime).format(DATE_TIME_FORMAT)}
        />

        <Group justify="flex-end" mt="md">
          <Button onClick={handleSubmit} loading={updateExpense.isPending} disabled={submitDisabled}>
            Simpan
          </Button>
        </Group>
      </Stack>
  )
}

export default HistoryPage
