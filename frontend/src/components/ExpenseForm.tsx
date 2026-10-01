import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Button,
  Divider,
  Group,
  NumberInput,
  Paper,
  Progress,
  SegmentedControl,
  Select,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { DateTimePicker } from '@mantine/dates'
import { useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import PhotoInput, { type PhotoSelection } from './PhotoInput'
import { useCreateExpense, useUploadPhoto } from '../hooks/useCreateExpense'
import { useOptions } from '../hooks/useOptions'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'
import { useToast } from './Toast'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'
const DATE_TIME_SECONDS_FORMAT = 'YYYY-MM-DD HH:mm:ss'
const DRAFT_KEY = 'expense-form-draft'

interface ExpenseDraft {
  name: string
  budget: string | null
  category: string | null
  amount: string | number
  description: string
}

function loadDraft(): ExpenseDraft | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    return raw ? (JSON.parse(raw) as ExpenseDraft) : null
  } catch {
    return null
  }
}

function ExpenseForm() {
  const [mode, setMode] = useState('now')
  const [dateTime, setDateTime] = useState<string>(dayjs().format(DATE_TIME_SECONDS_FORMAT))
  const draft = useMemo(loadDraft, [])
  const [name, setName] = useState(draft?.name ?? '')
  const [budget, setBudget] = useState<string | null>(draft?.budget ?? null)
  const [category, setCategory] = useState<string | null>(draft?.category ?? null)
  const [amount, setAmount] = useState<string | number>(draft?.amount ?? '')
  const [description, setDescription] = useState(draft?.description ?? '')
  const [photo, setPhoto] = useState<PhotoSelection | null>(null)

  const { data: options, isPending: optionsLoading } = useOptions()
  const toast = useToast()
  const createExpense = useCreateExpense()
  const uploadPhoto = useUploadPhoto()
  const photoUploading = uploadPhoto.isPending
  const queryClient = useQueryClient()
  const submittingRef = useRef(false)
  const [errors, setErrors] = useState<{ name?: string; budget?: string; amount?: string }>({})
  const nameRef = useRef<HTMLInputElement>(null)
  const budgetRef = useRef<HTMLInputElement>(null)
  const amountRef = useRef<HTMLInputElement>(null)

  const nowDisabled = mode === 'now'
  const displayValue = nowDisabled ? dayjs().format(DATE_TIME_SECONDS_FORMAT) : dateTime

  // Simpan draft input agar tidak hilang saat berpindah aplikasi di HP.
  useEffect(() => {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ name, budget, category, amount, description }))
    } catch {
      // storage penuh / private mode: abaikan
    }
  }, [name, budget, category, amount, description])

  const validate = () => {
    const next: { name?: string; budget?: string; amount?: string } = {}
    if (name.trim() === '') next.name = 'Nama belum diisi'
    if (!budget) next.budget = 'Budget belum dipilih'
    if (Number(amount) <= 0) next.amount = 'Nominal harus lebih dari 0'
    return next
  }

  const resetForm = () => {
    setName('')
    setBudget(null)
    setCategory(null)
    setAmount('')
    setDescription('')
    setPhoto(null)
    setDateTime(dayjs().format(DATE_TIME_SECONDS_FORMAT))
    try {
      localStorage.removeItem(DRAFT_KEY)
    } catch {
      // abaikan
    }
  }

  const handleSubmit = () => {
    if (submittingRef.current || createExpense.isPending || photoUploading) return
    const nextErrors = validate()
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors)
      toast.error('Periksa field yang ditandai merah', { title: 'Belum lengkap' })
      if (nextErrors.name) nameRef.current?.focus()
      else if (nextErrors.budget) budgetRef.current?.focus()
      else if (nextErrors.amount) amountRef.current?.focus()
      return
    }
    setErrors({})
    submittingRef.current = true
    createExpense.mutate(
      {
        dateTime: dayjs(displayValue).format(DATE_TIME_FORMAT),
        name: name.trim(),
        budget: budget ?? '',
        category: category ?? undefined,
        amount: Number(amount),
        description: description.trim() === '' ? undefined : description.trim(),
        invoiceId: photo?.kind === 'existing' ? photo.invoiceId : undefined,
      },
      {
        onSuccess: (result) => {
          const reset = () => {
            submittingRef.current = false
            queryClient.invalidateQueries({ queryKey: ['options'] })
            resetForm()
          }
          const showSuccess = () => {
            reset()
            toast.success('Pengeluaran berhasil disimpan', { title: 'Berhasil' })
          }
          const showPhotoError = (error: unknown) => {
            reset()
            toast.warning(getErrorMessage(error), { title: 'Tersimpan, tapi foto gagal diupload' })
          }
          if (photo && photo.kind === 'new' && result.id) {
            uploadPhoto.mutate(
              { id: result.id, file: photo.file },
              { onSuccess: showSuccess, onError: showPhotoError },
            )
          } else {
            showSuccess()
          }
        },
        onError: (error) => {
          submittingRef.current = false
          toast.error(getErrorMessage(error), { title: 'Gagal' })
        },
      },
    )
  }

  const balanceOf = (name: string): number | undefined =>
    options?.budgets.find((b) => b.name === name)?.balance

  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((value) => ({ value: value.name, label: value.name })),
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

  const selectedBalance = budget ? balanceOf(budget) : undefined
  const amountNumber = Number(amount)
  const showPreview = selectedBalance !== undefined && amountNumber > 0
  const projectedBalance = showPreview ? selectedBalance - amountNumber : 0
  const balanceColor = (value: number) => (value < 0 ? 'red' : undefined)

  return (
    <Paper withBorder p={{ base: 'md', sm: 'lg' }} radius="md">
      <Stack gap="md">
        <Title order={3}>Catat Pengeluaran</Title>

        <SegmentedControl
          value={mode}
          onChange={setMode}
          fullWidth
          data={[
            { value: 'now', label: 'Waktu Sekarang' },
            { value: 'manual', label: 'Waktu Manual' },
          ]}
        />

        <DateTimePicker
          label="Waktu"
          placeholder="Pilih waktu"
          value={displayValue}
          onChange={(value) => setDateTime(value ?? '')}
          valueFormat={DATE_TIME_FORMAT}
          required
          disabled={nowDisabled}
          dropdownType="modal"
          size="md"
        />

        <TextInput
          ref={nameRef}
          label="Nama"
          placeholder="Nama pengeluaran"
          value={name}
          onChange={(event) => {
            setName(event.currentTarget.value)
            if (errors.name) setErrors((prev) => ({ ...prev, name: undefined }))
          }}
          error={errors.name}
          maxLength={255}
          required
          size="md"
        />

        <Select
          ref={budgetRef}
          label="Budget"
          placeholder={optionsLoading ? 'Memuat...' : 'Pilih budget'}
          data={budgetOptions}
          value={budget}
          onChange={(value) => {
            setBudget(value)
            setCategory(null)
            if (errors.budget) setErrors((prev) => ({ ...prev, budget: undefined }))
          }}
          error={errors.budget}
          searchable
          required
          disabled={optionsLoading}
          size="md"
          maxDropdownHeight={260}
          renderOption={({ option }) => {
            const balance = balanceOf(option.value)
            return (
              <Group flex="1" justify="space-between" wrap="nowrap" gap="md">
                <Text truncate>{option.label}</Text>
                <Text ta="end" ff="monospace" c={balanceColor(balance ?? 0)}>
                  {balance === undefined ? '-' : formatCurrency(balance)}
                </Text>
              </Group>
            )
          }}
        />

        <Select
          label="Category"
          placeholder={budget ? 'Uncategorized' : 'Pilih budget dulu'}
          data={categoryOptions}
          value={category}
          onChange={setCategory}
          searchable
          clearable
          disabled={!budget || categoryOptions.length === 0}
          size="md"
          maxDropdownHeight={260}
        />

        <NumberInput
          ref={amountRef}
          label="Nominal"
          placeholder="0"
          value={amount}
          onChange={(value) => {
            setAmount(value)
            if (errors.amount) setErrors((prev) => ({ ...prev, amount: undefined }))
          }}
          error={errors.amount}
          min={1}
          allowNegative={false}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          required
          size="md"
        />

        {showPreview && (
          <Paper withBorder p="sm" radius="md" bg="var(--mantine-color-body)">
            <Stack gap={6}>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Sisa saldo
                </Text>
                <Text size="sm" ff="monospace">
                  {formatCurrency(selectedBalance ?? 0)}
                </Text>
              </Group>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Nominal
                </Text>
                <Text size="sm" ff="monospace">
                  -{formatCurrency(amountNumber)}
                </Text>
              </Group>
              <Divider my={2} />
              <Group justify="space-between">
                <Text size="sm" fw={500}>
                  Saldo nanti
                </Text>
                <Text fz="lg" fw={700} c={balanceColor(projectedBalance)} ff="monospace" lh={1.2}>
                  {formatCurrency(projectedBalance)}
                </Text>
              </Group>
            </Stack>
          </Paper>
        )}

        <TextInput
          label="Deskripsi (opsional)"
          placeholder="Catatan tambahan"
          value={description}
          onChange={(event) => setDescription(event.currentTarget.value)}
          maxLength={10000}
          size="md"
        />

        <PhotoInput
          value={photo}
          onChange={setPhoto}
          dateTime={dayjs(displayValue).format(DATE_TIME_FORMAT)}
        />

        {photoUploading && (
          <Paper withBorder p="sm" radius="md">
            <Group justify="space-between" mb={4}>
              <Text size="sm">Mengunggah foto...</Text>
              <Text size="sm" c="dimmed">
                {uploadPhoto.progress}%
              </Text>
            </Group>
            <Progress value={uploadPhoto.progress} striped animated />
          </Paper>
        )}

        <Button
          onClick={handleSubmit}
          loading={createExpense.isPending || photoUploading}
          fullWidth
          size="md"
          mt="xs"
        >
          Simpan
        </Button>
      </Stack>
    </Paper>
  )
}

export default ExpenseForm
