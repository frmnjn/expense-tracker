import { useMemo, useRef, useState } from 'react'
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
import { notifications } from '@mantine/notifications'
import { useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import PhotoInput, { type PhotoSelection } from './PhotoInput'
import { useCreateExpense, useUploadPhoto } from '../hooks/useCreateExpense'
import { useOptions } from '../hooks/useOptions'
import { formatCurrency } from '../utils/currency'
import { getErrorMessage } from '../utils/error'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'
const DATE_TIME_SECONDS_FORMAT = 'YYYY-MM-DD HH:mm:ss'

function ExpenseForm() {
  const [mode, setMode] = useState('now')
  const [dateTime, setDateTime] = useState<string>(dayjs().format(DATE_TIME_SECONDS_FORMAT))
  const [name, setName] = useState('')
  const [budget, setBudget] = useState<string | null>(null)
  const [amount, setAmount] = useState<string | number>('')
  const [description, setDescription] = useState('')
  const [photo, setPhoto] = useState<PhotoSelection | null>(null)

  const { data: options, isPending: optionsLoading } = useOptions()
  const createExpense = useCreateExpense()
  const uploadPhoto = useUploadPhoto()
  const photoUploading = uploadPhoto.isPending
  const queryClient = useQueryClient()
  const submittingRef = useRef(false)

  const nowDisabled = mode === 'now'
  const displayValue = nowDisabled ? dayjs().format(DATE_TIME_SECONDS_FORMAT) : dateTime

  const submitDisabled =
    name.trim() === '' || !budget || Number(amount) <= 0 || createExpense.isPending || photoUploading

  const resetForm = () => {
    setName('')
    setBudget(null)
    setAmount('')
    setDescription('')
    setPhoto(null)
    setDateTime(dayjs().format(DATE_TIME_SECONDS_FORMAT))
  }

  const handleSubmit = () => {
    if (submittingRef.current) return
    submittingRef.current = true
    createExpense.mutate(
      {
        dateTime: dayjs(displayValue).format(DATE_TIME_FORMAT),
        name: name.trim(),
        budget: budget ?? '',
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
            notifications.show({
              title: 'Berhasil',
              message: 'Pengeluaran berhasil disimpan',
              color: 'green',
            })
          }
          const showPhotoError = (error: unknown) => {
            reset()
            notifications.show({
              title: 'Tersimpan, tapi foto gagal diupload',
              message: getErrorMessage(error),
              color: 'orange',
            })
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
          notifications.show({
            title: 'Gagal',
            message: getErrorMessage(error),
            color: 'red',
          })
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
          label="Nama"
          placeholder="Nama pengeluaran"
          value={name}
          onChange={(event) => setName(event.currentTarget.value)}
          maxLength={255}
          required
          size="md"
        />

        <Select
          label="Budget"
          placeholder={optionsLoading ? 'Memuat...' : 'Pilih budget'}
          data={budgetOptions}
          value={budget}
          onChange={setBudget}
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

        <NumberInput
          label="Nominal"
          placeholder="0"
          value={amount}
          onChange={setAmount}
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
          label="Description (opsional)"
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
              <Text size="sm">Mengupload foto...</Text>
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
          disabled={submitDisabled}
          fullWidth
          size="md"
          mt="xs"
        >
          Save
        </Button>
      </Stack>
    </Paper>
  )
}

export default ExpenseForm
