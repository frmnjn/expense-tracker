import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ActionIcon,
  Button,
  Container,
  Divider,
  Group,
  Image,
  LoadingOverlay,
  Modal,
  NumberInput,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import dayjs from 'dayjs'
import { useDeleteExpense, useExpenses, usePeriods, useUpdateExpense } from '../hooks/useExpenses'
import { useDeletePhoto, useUploadPhoto } from '../hooks/useCreateExpense'
import { useOptions } from '../hooks/useOptions'
import PhotoInput, { type PhotoSelection } from '../components/PhotoInput'
import { TransactionCard } from '../components/TransactionCard'
import { getPhotoUrl } from '../services/expense'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'
import type { Expense } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'

const SORT_LABELS: Record<string, string> = {
  'waktu-desc': 'Waktu terbaru',
  'waktu-asc': 'Waktu terlama',
  'nominal-desc': 'Nominal terbesar',
  'nominal-asc': 'Nominal terkecil',
}

const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

function HistoryPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options } = useOptions()
  const [period, setPeriod] = useState<string | null>(null)
  const { data: expensesData, isPending: expensesLoading } = useExpenses(period)
  const isMobile = useMediaQuery('(max-width: 48em)')

  useEffect(() => {
    if (!period && periodsData && periodsData.periods.length > 0) {
      setPeriod(periodsData.periods[0])
    }
  }, [period, periodsData])

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])
  const expenses = expensesData?.expenses ?? []
  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((b) => ({ value: b.name, label: b.name })),
    [options],
  )

  const [search, setSearch] = useState('')
  const [budgetFilter, setBudgetFilter] = useState<string | null>(null)
  const [sortBy, setSortBy] = useState<string>('waktu-desc')
  const [filterOpened, setFilterOpened] = useState(false)
  const [viewingPhoto, setViewingPhoto] = useState<Expense | null>(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState<number>(15)
  const pageSizeUserSetRef = useRef(false)

  // Default baris/halaman ikut device (5 mobile / 15 desktop) sampai user
  // memilih sendiri lewat selector.
  useEffect(() => {
    if (!pageSizeUserSetRef.current) {
      setPageSize(isMobile ? 5 : 15)
    }
  }, [isMobile])

  const hasActiveFilters = budgetFilter !== null || sortBy !== 'waktu-desc'
  const activeFilterCount = (budgetFilter ? 1 : 0) + (sortBy !== 'waktu-desc' ? 1 : 0)

  useEffect(() => {
    setPage(1)
  }, [search, budgetFilter, sortBy, period, pageSize])

  const visibleExpenses = useMemo(() => {
    const q = search.trim().toLowerCase()
    let list = expensesData?.expenses ?? []
    if (q) {
      list = list.filter((e) => e.name.toLowerCase().includes(q))
    }
    if (budgetFilter) {
      list = list.filter((e) => e.budget === budgetFilter)
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
  }, [expensesData, search, budgetFilter, sortBy])

  const totalPages = Math.max(1, Math.ceil(visibleExpenses.length / pageSize))
  const safePage = Math.min(page, totalPages)
  const pageExpenses = visibleExpenses.slice((safePage - 1) * pageSize, safePage * pageSize)

  const [editing, setEditing] = useState<Expense | null>(null)
  const [deleting, setDeleting] = useState<Expense | null>(null)

  const deleteExpense = useDeleteExpense()

  const handleDelete = () => {
    if (!deleting) return
    deleteExpense.mutate(deleting.id, {
      onSuccess: () => {
        notifications.show({ title: 'Berhasil', message: 'Pengeluaran dihapus', color: 'green' })
        setDeleting(null)
      },
      onError: (error) => {
        notifications.show({ title: 'Gagal', message: getErrorMessage(error), color: 'red' })
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
        <Group justify="space-between" align="flex-end">
          <div>
            <Text size="sm" c="blue" fw={700} mb={4}>
              TRANSACTIONS
            </Text>
            <Title order={1} size="clamp(1.65rem, 5vw, 2.1rem)">
              Riwayat Pengeluaran
            </Title>
            <Text c="dimmed" mt={5}>
              Lihat dan kelola semua pengeluaran kamu.
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

        {period && !(isMobile && editing) && (
          <Stack gap="xs">
            <Group wrap="nowrap" align="center">
              <TextInput
                placeholder="Cari nama pengeluaran"
                value={search}
                onChange={(e) => setSearch(e.currentTarget.value)}
                leftSection="🔍"
                rightSection={
                  search ? (
                    <ActionIcon variant="subtle" size="sm" onClick={() => setSearch('')} aria-label="Bersihkan pencarian">
                      ✕
                    </ActionIcon>
                  ) : null
                }
                size="md"
                style={{ flex: 1, minWidth: 0 }}
              />
              <Button
                size="md"
                variant={hasActiveFilters ? 'filled' : 'light'}
                color="blue"
                leftSection="⚙"
                onClick={() => setFilterOpened(true)}
                style={{ flexShrink: 0 }}
              >
                Filter & Urut{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
              </Button>
            </Group>

            {hasActiveFilters && (
              <Group gap="xs" wrap="wrap">
                {budgetFilter && (
                  <Button
                    size="xs"
                    variant="light"
                    color="blue"
                    rightSection={<span>✕</span>}
                    onClick={() => setBudgetFilter(null)}
                  >
                    Budget: {budgetFilter}
                  </Button>
                )}
                {sortBy !== 'waktu-desc' && (
                  <Button
                    size="xs"
                    variant="light"
                    color="gray"
                    rightSection={<span>✕</span>}
                    onClick={() => setSortBy('waktu-desc')}
                  >
                    {SORT_LABELS[sortBy] ?? sortBy}
                  </Button>
                )}
              </Group>
            )}
          </Stack>
        )}

        {isMobile && editing ? (
          <EditExpenseForm expense={editing} onClose={() => setEditing(null)} inline />
        ) : (
          <>
            <Paper withBorder p={{ base: 'sm', sm: 'lg' }} radius="xl" pos="relative">
          <LoadingOverlay
            visible={expensesLoading && !!period}
            zIndex={1000}
            overlayProps={{ radius: 'lg', blur: 1 }}
          />

          {expenses.length === 0 ? (
            <Text c="dimmed" ta="center" py="lg">
              {period ? 'Belum ada pengeluaran pada periode ini.' : 'Pilih periode untuk melihat pengeluaran.'}
            </Text>
          ) : visibleExpenses.length === 0 ? (
            <Stack align="center" gap={4} py="xl">
              <Text fz={28} aria-hidden>
                🔍
              </Text>
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
                  onViewPhoto={setViewingPhoto}
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
                    <Table.Td>{dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}</Table.Td>
                    <Table.Td>{expense.name}</Table.Td>
                    <Table.Td>{expense.budget}</Table.Td>
                    <Table.Td ta="right">{formatCurrency(expense.amount)}</Table.Td>
                    <Table.Td ta="right">
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                        {expense.hasPhoto && (
                          <ActionIcon variant="light" color="gray" size="md" onClick={() => setViewingPhoto(expense)} aria-label="Lihat foto">
                            📷
                          </ActionIcon>
                        )}
                        <ActionIcon variant="light" color="blue" size="md" disabled={!expense.id} onClick={() => setEditing(expense)} aria-label="Edit">
                          ✎
                        </ActionIcon>
                        <ActionIcon variant="light" color="red" size="md" disabled={!expense.id} onClick={() => setDeleting(expense)} aria-label="Hapus">
                          🗑
                        </ActionIcon>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Paper>

        {totalPages > 1 && (
          <Group justify="center" gap="sm" wrap="wrap">
            <Select
              size="xs"
              w={86}
              aria-label="Baris per halaman"
              value={String(pageSize)}
              onChange={(v) => {
                pageSizeUserSetRef.current = true
                setPageSize(Number(v) || 15)
              }}
              data={[
                { value: '5', label: '5' },
                { value: '10', label: '10' },
                { value: '15', label: '15' },
                { value: '25', label: '25' },
              ]}
            />
            <Pagination value={safePage} onChange={setPage} total={totalPages} size="md" radius="md" />
          </Group>
        )}
          </>
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
          opened={filterOpened}
          onClose={() => setFilterOpened(false)}
          title="Filter & Urut"
          centered
          fullScreen={isMobile}
          size="sm"
          zIndex={1200}
        >
          <Stack gap="lg">
            <Select
              label="Budget"
              placeholder="Semua budget"
              data={budgetOptions}
              value={budgetFilter}
              onChange={setBudgetFilter}
              clearable
              searchable
              size="md"
              comboboxProps={{ withinPortal: false }}
            />
            <Select
              label="Urutkan"
              data={[
                { value: 'waktu-desc', label: 'Waktu terbaru' },
                { value: 'waktu-asc', label: 'Waktu terlama' },
                { value: 'nominal-desc', label: 'Nominal terbesar' },
                { value: 'nominal-asc', label: 'Nominal terkecil' },
              ]}
              value={sortBy}
              onChange={(value) => setSortBy(value ?? 'waktu-desc')}
              size="md"
              comboboxProps={{ withinPortal: false }}
            />
            <Group justify="flex-end" mt="sm">
              <Button fullWidth={isMobile} onClick={() => setFilterOpened(false)}>
                Apply
              </Button>
            </Group>
          </Stack>
        </Modal>

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
                <Text fz={48}>📄</Text>
                <Button component="a" href={getPhotoUrl(viewingPhoto.id)} target="_blank" variant="light">
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

  const [dateTime, setDateTime] = useState(expense.dateTime)
  const [name, setName] = useState(expense.name)
  const [budget, setBudget] = useState<string | null>(expense.budget)
  const [amount, setAmount] = useState<string | number>(expense.amount)
  const [description, setDescription] = useState(expense.description ?? '')
  const [photo, setPhoto] = useState<PhotoSelection | null>(null)
  const [removePhoto, setRemovePhoto] = useState(false)

  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((b) => ({ value: b.name, label: b.name })),
    [options],
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
          amount: Number(amount),
          description: description.trim() === '' ? undefined : description.trim(),
          invoiceId: photo?.kind === 'existing' ? photo.invoiceId : undefined,
        },
      },
      {
        onSuccess: () => {
          const success = () => {
            notifications.show({ title: 'Berhasil', message: 'Pengeluaran diperbarui', color: 'green' })
            onClose()
          }
          const photoError = (error: unknown) => {
            notifications.show({ title: 'Tersimpan, tapi foto gagal diupload', message: getErrorMessage(error), color: 'orange' })
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
          notifications.show({ title: 'Gagal', message: getErrorMessage(error), color: 'red' })
        },
      },
    )
  }

  return (
    <Stack gap="md">
      {inline && (
        <Group justify="space-between" align="center">
          <Button variant="subtle" leftSection="←" onClick={onClose}>
            Kembali
          </Button>
          <Title order={3}>Ubah pengeluaran</Title>
        </Group>
      )}

      <TextInput label="Waktu" value={dateTime} onChange={(e) => setDateTime(e.currentTarget.value)} required size="md" />
        <TextInput label="Nama" value={name} onChange={(e) => setName(e.currentTarget.value)} maxLength={255} required size="md" />
        <Select label="Budget" data={budgetOptions} value={budget} onChange={setBudget} searchable required size="md" comboboxProps={{ withinPortal: false }} />
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

        <TextInput
          label="Description (opsional)"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          maxLength={10000}
          size="md"
        />

        {expense.hasPhoto && !removePhoto && !photo && (
          <Stack gap={4}>
            <Group align="flex-start">
              {expense.photoType === 'pdf' ? (
                <Button component="a" href={getPhotoUrl(expense.id)} target="_blank" variant="light" size="xs">
                  📄 Lihat PDF
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
