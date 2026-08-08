import { useMemo, useState } from 'react'
import {
  Box,
  Button,
  Group,
  NumberInput,
  Paper,
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
import PhotoInput from './PhotoInput'
import { useCreateExpense, useUploadPhoto } from '../hooks/useCreateExpense'
import { useOptions } from '../hooks/useOptions'
import { formatCurrency } from '../utils/currency'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'
const DATE_TIME_SECONDS_FORMAT = 'YYYY-MM-DD HH:mm:ss'

function ExpenseForm() {
  const [mode, setMode] = useState('now')
  const [dateTime, setDateTime] = useState<string>(dayjs().format(DATE_TIME_SECONDS_FORMAT))
  const [name, setName] = useState('')
  const [budget, setBudget] = useState<string | null>(null)
  const [amount, setAmount] = useState<string | number>('')
  const [description, setDescription] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)

  const { data: options, isPending: optionsLoading } = useOptions()
  const createExpense = useCreateExpense()
  const uploadPhoto = useUploadPhoto()
  const queryClient = useQueryClient()

  const nowDisabled = mode === 'now'
  const displayValue = nowDisabled ? dayjs().format(DATE_TIME_SECONDS_FORMAT) : dateTime

  const submitDisabled =
    name.trim() === '' || !budget || Number(amount) <= 0 || createExpense.isPending

  const resetForm = () => {
    setName('')
    setBudget(null)
    setAmount('')
    setDescription('')
    setPhoto(null)
    setDateTime(dayjs().format(DATE_TIME_SECONDS_FORMAT))
  }

  const handleSubmit = () => {
    createExpense.mutate(
      {
        dateTime: dayjs(displayValue).format(DATE_TIME_FORMAT),
        name: name.trim(),
        budget: budget ?? '',
        amount: Number(amount),
        description: description.trim() === '' ? undefined : description.trim(),
      },
      {
        onSuccess: (result) => {
          const finish = () => {
            notifications.show({
              title: 'Berhasil',
              message: 'Pengeluaran berhasil disimpan',
              color: 'green',
            })
            queryClient.invalidateQueries({ queryKey: ['options'] })
            resetForm()
          }
          if (photo && result.id) {
            uploadPhoto.mutate(
              { id: result.id, file: photo },
              { onSuccess: finish, onError: finish },
            )
          } else {
            finish()
          }
        },
        onError: (error) => {
          const message =
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Terjadi kesalahan, coba lagi'
          notifications.show({
            title: 'Gagal',
            message,
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
    <Paper withBorder p="lg" radius="md" shadow="sm">
      <Stack>
        <Title order={3}>Catat Pengeluaran</Title>

        <div>
          <SegmentedControl
            value={mode}
            onChange={setMode}
            fullWidth
            data={[
              { value: 'now', label: 'Waktu Sekarang' },
              { value: 'manual', label: 'Waktu Manual' },
            ]}
          />
        </div>

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
          label="Name"
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
          suffix=",-"
          required
          size="md"
        />

        {showPreview && (
          <Paper withBorder p="sm" radius="md" bg="var(--mantine-color-body)">
            <Stack gap="4">
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Sisa saldo
                </Text>
                <Text size="sm">{formatCurrency(selectedBalance)}</Text>
              </Group>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Nominal
                </Text>
                <Text size="sm">-{formatCurrency(amountNumber)}</Text>
              </Group>
              <Group justify="space-between">
                <Text size="sm" fw={500}>
                  Saldo nanti
                </Text>
                <Text size="sm" fw={700} c={balanceColor(projectedBalance)} ff="monospace">
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
          maxLength={255}
          size="md"
        />

        <PhotoInput value={photo} onChange={setPhoto} />

        <Box
          mt="md"
          style={{
            position: 'sticky',
            bottom: 0,
            paddingTop: 'var(--mantine-spacing-sm)',
            paddingBottom: 'var(--mantine-spacing-sm)',
            backgroundColor: 'var(--mantine-color-body)',
          }}
        >
          <Button onClick={handleSubmit} loading={createExpense.isPending} disabled={submitDisabled} fullWidth size="md">
            Save
          </Button>
        </Box>
      </Stack>
    </Paper>
  )
}

export default ExpenseForm
