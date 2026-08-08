import { useMemo, useState } from 'react'
import {
  ActionIcon,
  Anchor,
  Button,
  Container,
  Group,
  Image,
  LoadingOverlay,
  Modal,
  NumberInput,
  Paper,
  Select,
  SimpleGrid,
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
import ColorSchemeToggle from '../components/ColorSchemeToggle'
import PhotoInput, { type PhotoSelection } from '../components/PhotoInput'
import { getPhotoUrl } from '../services/expense'
import { formatCurrency } from '../utils/currency'
import type { Expense } from '../types/expense'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'

const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

function HistoryPage() {
  const { data: periodsData, isPending: periodsLoading } = usePeriods()
  const { data: options } = useOptions()
  const [period, setPeriod] = useState<string | null>(null)
  const { data: expensesData, isPending: expensesLoading } = useExpenses(period)
  const isMobile = useMediaQuery('(max-width: 48em)')

  const periods = useMemo(() => (periodsData?.periods ?? []).map((p) => ({ value: p, label: p })), [periodsData])
  const expenses = expensesData?.expenses ?? []
  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((b) => ({ value: b.name, label: b.name })),
    [options],
  )

  const [search, setSearch] = useState('')
  const [budgetFilter, setBudgetFilter] = useState<string | null>(null)
  const [sortBy, setSortBy] = useState<string>('waktu-desc')
  const [viewingPhoto, setViewingPhoto] = useState<Expense | null>(null)

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
        const message =
          (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Terjadi kesalahan, coba lagi'
        notifications.show({ title: 'Gagal', message, color: 'red' })
      },
    })
  }

  const balanceOf = (name: string): number | undefined => options?.budgets.find((b) => b.name === name)?.balance
  const deletingBalance = deleting ? balanceOf(deleting.budget) : undefined
  const projectedDeleteBalance =
    deletingBalance !== undefined && deleting ? deletingBalance + deleting.amount : undefined

  return (
    <Container size="md" py="lg">
      <Group justify="space-between" align="flex-start" mb="xs">
        <div>
          <Title order={2}>Riwayat Pengeluaran</Title>
          <Group gap="md">
            <Anchor href="/dashboard" display="inline-block">
              Dashboard
            </Anchor>
            <Anchor href="/catat" display="inline-block">
              Catat Pengeluaran
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

      {period && (
        <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="xs" mb="md">
          <TextInput
            placeholder="Cari nama..."
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
          />
          <Select
            placeholder="Semua budget"
            data={budgetOptions}
            value={budgetFilter}
            onChange={setBudgetFilter}
            clearable
            searchable
          />
          <Select
            placeholder="Urutkan"
            data={[
              { value: 'waktu-desc', label: 'Waktu terbaru' },
              { value: 'waktu-asc', label: 'Waktu terlama' },
              { value: 'nominal-desc', label: 'Nominal terbesar' },
              { value: 'nominal-asc', label: 'Nominal terkecil' },
            ]}
            value={sortBy}
            onChange={(value) => setSortBy(value ?? 'waktu-desc')}
          />
        </SimpleGrid>
      )}

      <Paper withBorder p="lg" radius="md" shadow="sm" pos="relative">
        <LoadingOverlay visible={expensesLoading && !!period} zIndex={1000} overlayProps={{ radius: 'sm', blur: 1 }} />

        {expenses.length === 0 ? (
          <Text c="dimmed" ta="center" py="lg">
            {period ? 'Tidak ada pengeluaran pada periode ini.' : 'Pilih periode untuk melihat pengeluaran.'}
          </Text>
        ) : visibleExpenses.length === 0 ? (
          <Text c="dimmed" ta="center" py="lg">
            Tidak ada hasil yang cocok dengan filter.
          </Text>
        ) : isMobile ? (
          <Stack gap="sm">
            {visibleExpenses.map((expense) => (
              <Paper key={expense.id} withBorder p="sm" radius="md">
                <Group justify="space-between" mb={4} wrap="nowrap" align="flex-start">
                  <Text fw={600} style={{ wordBreak: 'break-word' }}>
                    {expense.name}
                  </Text>
                  <Text fw={700} ff="monospace" style={{ whiteSpace: 'nowrap' }}>
                    {formatCurrency(expense.amount)}
                  </Text>
                </Group>
                <Group justify="space-between" mb="xs">
                  <Text size="sm" c="dimmed">
                    {dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}
                  </Text>
                  <Text size="sm" c="dimmed">
                    {expense.budget}
                  </Text>
                </Group>
                <Group justify="flex-end" gap="xs">
                  {expense.hasPhoto && (
                    <ActionIcon variant="subtle" color="gray" onClick={() => setViewingPhoto(expense)}>
                      <span>📷</span>
                    </ActionIcon>
                  )}
                  <ActionIcon
                    variant="subtle"
                    color="blue"
                    disabled={!expense.id}
                    onClick={() => setEditing(expense)}
                  >
                    <span>✎</span>
                  </ActionIcon>
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    disabled={!expense.id}
                    onClick={() => setDeleting(expense)}
                  >
                    <span>🗑</span>
                  </ActionIcon>
                </Group>
              </Paper>
            ))}
          </Stack>
        ) : (
          <Table highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Waktu</Table.Th>
                <Table.Th>Name</Table.Th>
                <Table.Th>Budget</Table.Th>
                <Table.Th ta="right">Nominal</Table.Th>
                <Table.Th ta="right">Aksi</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {visibleExpenses.map((expense) => (
                <Table.Tr key={expense.id}>
                  <Table.Td>{dayjs(expense.dateTime).format(DATE_TIME_FORMAT)}</Table.Td>
                  <Table.Td>{expense.name}</Table.Td>
                  <Table.Td>{expense.budget}</Table.Td>
                  <Table.Td ta="right">{formatCurrency(expense.amount)}</Table.Td>
                  <Table.Td ta="right">
                    <Group gap="xs" justify="flex-end" wrap="nowrap">
                      {expense.hasPhoto && (
                        <ActionIcon variant="subtle" color="gray" onClick={() => setViewingPhoto(expense)}>
                          <span>📷</span>
                        </ActionIcon>
                      )}
                      <ActionIcon
                        variant="subtle"
                        color="blue"
                        disabled={!expense.id}
                        onClick={() => setEditing(expense)}
                      >
                        <span>✎</span>
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        disabled={!expense.id}
                        onClick={() => setDeleting(expense)}
                      >
                        <span>🗑</span>
                      </ActionIcon>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Paper>

      {editing && <EditExpenseModal expense={editing} onClose={() => setEditing(null)} />}

      <Modal
        opened={!!viewingPhoto}
        onClose={() => setViewingPhoto(null)}
        title={viewingPhoto?.name}
        size="md"
        centered
      >
        {viewingPhoto && (
          <Image src={getPhotoUrl(viewingPhoto.id)} alt="Invoice" fit="contain" />
        )}
      </Modal>

      <Modal opened={!!deleting} onClose={() => setDeleting(null)} title="Hapus pengeluaran" centered>
        <Text>
          Yakin ingin menghapus "{deleting?.name}" sebesar {deleting ? formatCurrency(deleting.amount) : ''}?
        </Text>
        {deleting && projectedDeleteBalance !== undefined && (
          <Paper withBorder p="sm" radius="md" mt="md" bg="var(--mantine-color-body)">
            <Stack gap="4">
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Saldo {deleting.budget} saat ini
                </Text>
                <Text size="sm">{formatCurrency(deletingBalance ?? 0)}</Text>
              </Group>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Dikembalikan
                </Text>
                <Text size="sm">+{formatCurrency(deleting.amount)}</Text>
              </Group>
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
          <Button variant="default" onClick={() => setDeleting(null)}>
            Batal
          </Button>
          <Button color="red" loading={deleteExpense.isPending} onClick={handleDelete}>
            Hapus
          </Button>
        </Group>
      </Modal>
    </Container>
  )
}

function EditExpenseModal({ expense, onClose }: { expense: Expense; onClose: () => void }) {
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
          const finish = () => {
            notifications.show({ title: 'Berhasil', message: 'Pengeluaran diperbarui', color: 'green' })
            onClose()
          }
          if (removePhoto) {
            deletePhoto.mutate(expense.id, { onSuccess: finish, onError: finish })
          } else if (photo?.kind === 'new') {
            uploadPhoto.mutate({ id: expense.id, file: photo.file }, { onSuccess: finish, onError: finish })
          } else {
            finish()
          }
        },
        onError: (error) => {
          const message =
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Terjadi kesalahan, coba lagi'
          notifications.show({ title: 'Gagal', message, color: 'red' })
        },
      },
    )
  }

  return (
    <Modal opened onClose={onClose} title="Ubah pengeluaran" centered>
      <Stack gap="sm">
        <TextInput
          label="Waktu"
          value={dateTime}
          onChange={(e) => setDateTime(e.currentTarget.value)}
          required
          size="md"
        />
        <TextInput
          label="Name"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={255}
          required
          size="md"
        />
        <Select
          label="Budget"
          data={budgetOptions}
          value={budget}
          onChange={setBudget}
          required
          size="md"
        />
        <NumberInput
          label="Nominal"
          value={amount}
          onChange={setAmount}
          min={0}
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
          maxLength={255}
          size="md"
        />

        {expense.hasPhoto && !removePhoto && !photo && (
          <Group align="flex-start">
            <Image
              src={getPhotoUrl(expense.id)}
              alt="Invoice saat ini"
              mah={150}
              fit="contain"
              radius="md"
              style={{ flex: 1 }}
            />
            <Button variant="subtle" color="red" onClick={() => setRemovePhoto(true)}>
              Hapus Foto
            </Button>
          </Group>
        )}

        {expense.hasPhoto && removePhoto ? (
          <Text size="sm" c="red">
            Foto akan dihapus setelah disimpan.
          </Text>
        ) : null}

        <PhotoInput
          value={photo}
          onChange={(v) => {
            setPhoto(v)
            if (v) setRemovePhoto(false)
          }}
          dateTime={dayjs(dateTime).format(DATE_TIME_FORMAT)}
        />

        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={onClose}>
            Batal
          </Button>
          <Button onClick={handleSubmit} loading={updateExpense.isPending} disabled={submitDisabled}>
            Simpan
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default HistoryPage
